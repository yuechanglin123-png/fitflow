# Offline Training Assistant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Preserve the user's previously chosen native execution method.

**Goal:** 在原生 Android 训练页面交付可离线唤醒、控制训练、播报提醒、切换男女声并查询时间和天气的助教。

**Architecture:** 本地语音识别与合成通过适配器接入，确定性解析器只接受已定义意图。应用级唯一训练状态持有者供界面和前台服务共享，所有修改经同一互斥锁落库。天气作为独立网络查询，录音不上传。

**Tech Stack:** Kotlin、Compose、Room、协程、Android AudioRecord/AudioTrack、Vosk 候选识别模型、sherpa-onnx 候选合成模型。

**Spec:** `docs/superpowers/specs/2026-09-23-offline-training-assistant-design.md`

## 实施记录（2026-09-24）

代码与模型接入已覆盖 Tasks 1–8；最终选择 Zipformer 14M int8 和 Kokoro v1.1-zh int8，替代初始候选。逐项测试与模型实测见 `docs/validation/assistant-model-evaluation.md`，最终构建结果见 `docs/validation/第七版发布记录.md`。以下原始清单保留为计划，不把未进行的人工试听、实体 ARM 手机、API26、蓝牙和来电验证勾选为通过。提交调整为最终验证后一个完整集成提交；模型文件通过固定 SHA-256 下载脚本复现。

## Global Constraints

- APK、安装后的应用及模型静态文件占用分别验收，均不超过 500,000,000 字节。
- Android minSdk 26；交付优先 arm64，模拟器测试架构另行构建。
- “小练小练”唤醒，一次唤醒最多执行一个操作；8秒无有效指令回到等待唤醒。
- 休息剩余10秒提醒；未暂停训练时间每10分钟鼓励，包含休息，不补播错过的鼓励。
- 四类训练命令经过当前状态检查，不允许模型自由生成代码或工具调用。
- 男声和女声在本地合成；模型随 APK 提供，不依赖首次下载或手机预装 TTS。
- 默认天气城市手动设置；天气联网，语音识别与训练数据不上传。
- 关闭即停止监听和播报；进程重启不自动开启麦克风。
- 单独核验运行库和权重许可，完整记录模型体积及评测，不把模拟器测试冒充真机测试。

## Review Focus

1. 一条“完成本组”重复回调或跨组迟到：只执行一次，不能完成下一组；Task 3。
2. 开关关闭后初始化、天气、播报回调到达：不得恢复录音；Task 6。
3. 暂停/延长后旧提醒仍排队：旧提醒失效，累计时间不漂移；Task 4。
4. 同名城市、HTTP失败或缺字段：不得播报错误地点或虚构天气；Task 5。
5. 界面和服务各自持有训练状态：可能覆盖彼此更新；Task 3 建立唯一状态所有权并验证。

## 文件与接口组织

新增 `assistant/` 目录，按职责划分 `AssistantModels.kt`、`AssistantIntentParser.kt`、`AssistantController.kt`、`AssistantReminderPolicy.kt`、`AssistantSettings.kt`、`AssistantPanel.kt`、`AssistantCommandDispatcher.kt`、`WeatherRepository.kt`；语音适配器置于 `assistant/speech/`。

复用 `session/SessionViewModel.kt` 和 `SessionReducer.kt`，新增 `data/WorkoutRuntime.kt` 提供进程内单一仓库和会话控制器；训练界面与前台服务均从该处获取对象。所有新状态的序列化字段有默认值，旧训练记录继续可读。

公共类型在 Task 2 定义，后续任务只消费这些接口；具体模型库和权重版本在 Task 1 实测后锁定，不凭文档猜测兼容性。

## Task 1: 验证可交付的本地语音模型

**Files:** Create `tools/assistant/evaluate-models.py`, `tools/assistant/model-manifest.json`, `docs/validation/assistant-model-evaluation.md`, `app/src/main/assets/assistant/licenses/`; Modify `app/build.gradle.kts`, `gradle/libs.versions.toml` only after model acceptance.

**Interfaces:** Produces 模型清单，字段 `name, version, source, sha256, license, archiveBytes, extractedBytes, sampleRate, speakerIds`；模型适配器读取这些锁定参数。

- [ ] 核验 Vosk Small CN 0.22 和 sherpa-onnx 中文 AISHELL3 权重的许可、模型格式、Android ABI及依赖。记录官方来源、实际文件校验值。若许可不允许交付，不打包该权重，评估另一明确许可候选并记录理由。
- [ ] 在独立验证目录加载真实模型；选择两种可区分的普通话音色，生成时间、数字、城市、训练指令的音频并试听。记录实际 speaker ID，不能猜测性别标签。
- [ ] 建立至少40条清晰命令/问句及20条非指令的 WAV 清单，注明真人或合成、采样率和预期意图；合成测试仅证明基本管线，不替代健身房真人验证。
- [ ] 验证脚本按下列断言输出报告；不达标时先调整模型而非削弱标准：

```python
assert all(item['license'] and item['sha256'] for item in manifest)
assert command_correct / command_total >= 0.90
assert background_actions == 0
assert installed_static_bytes < 500_000_000
```

- [ ] 记录识别、首音、初始化延迟和内存峰值及测试硬件；固定已验证依赖版本，保存所需许可声明。模型文件由可复现下载/校验脚本管理，避免误提交大文件；禁止继续接入未通过体积或许可门槛的模型。
- [ ] Commit: `chore: validate and pin offline speech models`。

## Task 2: 定义指令、语音和问答边界

**Files:** Create `app/src/main/java/com/fitflow/training/assistant/AssistantModels.kt`, `AssistantIntentParser.kt`; Test `app/src/test/java/com/fitflow/training/assistant/AssistantIntentParserTest.kt`。

**Interfaces:**

```kotlin
enum class TrainingCommand { SKIP_REST, EXTEND_REST_30, PAUSE, COMPLETE_SET }
enum class VoiceChoice { MALE, FEMALE }
enum class LocalQuestion { TIME, DATE, CURRENT_EXERCISE, REMAINING_SETS, REST_REMAINING }
sealed interface AssistantIntent {
    data class Command(val value: TrainingCommand) : AssistantIntent
    data class Query(val value: LocalQuestion) : AssistantIntent
    data class Weather(val city: String?) : AssistantIntent
    data object Unsupported : AssistantIntent
}
data class SpeechResult(val turnId: Long, val text: String, val isFinal: Boolean)
interface SpeechRecognizerAdapter {
    suspend fun initialize()
    fun start(onResult: (SpeechResult) -> Unit, onError: (String) -> Unit)
    fun stop()
    fun close()
}
interface SpeechSynthesizerAdapter {
    suspend fun initialize()
    suspend fun speak(text: String, voice: VoiceChoice)
    fun stop()
    fun close()
}
```

- [ ] 写失败测试，覆盖全角标点、三十/30秒、明确同义句、否定和多命令：

```kotlin
assertEquals(AssistantIntent.Command(TrainingCommand.EXTEND_REST_30), parser.parse("延长三十秒休息时间"))
assertEquals(AssistantIntent.Unsupported, parser.parse("不要完成本组"))
assertEquals(AssistantIntent.Unsupported, parser.parse("完成本组然后暂停训练"))
assertEquals(AssistantIntent.Weather("北京"), parser.parse("北京今天天气"))
```

- [ ] 执行 `:app:testDebugUnitTest --tests '*AssistantIntentParserTest'` 观察失败。
- [ ] 实现 `AssistantIntentParser.parse(text: String): AssistantIntent`：先规范化标点与空格，再对完整句式匹配；否定句先拒绝，避免子串匹配造成误执行；问答和训练操作互斥。
- [ ] 重跑目标测试通过，提交 `feat: parse bounded training assistant intents`。

## Task 3: 唯一会话状态与原子命令执行

**Files:** Create `data/WorkoutRuntime.kt`, `assistant/AssistantCommandDispatcher.kt`; Modify `session/SessionViewModel.kt`, `session/SessionScreen.kt`, `plan/PlanScreen.kt` and other actual session-controller construction sites; Test `assistant/AssistantCommandDispatcherTest.kt`, Android `session/AssistantSessionPersistenceTest.kt`。

**Interfaces:** Consumes `TrainingCommand`; Produces:

```kotlin
data class CommandContext(val sessionId: String, val blockId: String?, val phase: Phase, val turnId: Long)
data class CommandResult(val applied: Boolean, val message: String)
// SessionViewModel member; checks and state mutation share its existing Mutex.
suspend fun executeAssistant(command: TrainingCommand, context: CommandContext): CommandResult
// WorkoutRuntime exposes the single process-wide SessionViewModel to UI and service.
```

- [ ] 写失败测试：休息阶段才能跳过/延长，READY才能完成，暂停拒绝完成；同回合第二次不执行；识别期间按钮推进下一组后旧context失效；不同session失效。
- [ ] 运行上述测试确认失败。
- [ ] 在现有 `lock.withLock` 内去重、核对context和阶段、调用Reducer并落库；回应基于实际新状态。不要在锁中再次调用会获取同一锁的方法。
- [ ] 引入 `WorkoutRuntime.get(context)` 单例，检查所有创建 `SessionViewModel` 的位置；界面和服务共享Flow/Mutex。启动/恢复训练都通过它，不允许第二个写入控制器。
- [ ] Android Room测试验证服务语音和按钮交错操作最终数据库与Flow一致；单元测试及现有会话测试通过后提交 `feat: serialize assistant commands with workout state`。

## Task 4: 可恢复的训练计时与提醒去重

**Files:** Create `assistant/AssistantReminderPolicy.kt`; Modify `session/SessionModels.kt`, `session/SessionReducer.kt`, `session/SessionViewModel.kt`; Test `assistant/AssistantReminderPolicyTest.kt`, `session/SessionReducerTest.kt`。

**Interfaces:** Produces `ReminderEvent(id: String, sessionId: String, text: String, priority: Int, expiresAtMs: Long)`；`AssistantReminderPolicy.evaluate(snapshot: SessionSnapshot, nowMs: Long, enabled: Boolean): List<ReminderEvent>`。快照添加默认值字段用于累计未暂停时间、计时锚点、鼓励节点及休息提醒代次。

- [ ] 写时间驱动失败用例：11秒无提醒→10秒一次→9秒不重复；0秒无提醒；5秒休息一次；延长到>10秒后新阈值可提醒；暂停10分钟不增加有效时间；重新开启不补播。
- [ ] 运行 `:app:testDebugUnitTest --tests '*AssistantReminderPolicyTest' --tests '*SessionReducerTest'` 确认失败。
- [ ] 用可注入时钟累计有效时长；进程内以单调时钟计时，持久恢复使用保存锚点并处理时钟回拨的非负增量。关闭助教仍推进鼓励节点。旧JSON缺省字段可解析。
- [ ] 提醒ID包含session、休息阶段代次/鼓励节点；播报前重核快照。延长重新生成休息阈值代次，暂停恢复保留已播标记；鼓励有效期30秒。
- [ ] 验证重启、旧记录加载和时钟变更没有连播或负时长，测试通过，提交 `feat: schedule assistant reminders from workout time`。

## Task 5: 城市天气与本地问答

**Files:** Create `assistant/WeatherRepository.kt`, `assistant/LocalAnswerProvider.kt`, `assistant/AssistantSettings.kt`; Test `assistant/WeatherRepositoryTest.kt`, `assistant/LocalAnswerProviderTest.kt`。

**Interfaces:**

```kotlin
data class WeatherCity(val id: String, val name: String, val region: String, val latitude: Double, val longitude: Double)
data class WeatherAnswer(val city: WeatherCity, val description: String, val temperature: Double, val low: Double, val high: Double)
interface WeatherRepository {
    suspend fun searchCities(query: String): List<WeatherCity>
    suspend fun weather(city: WeatherCity): WeatherAnswer
}
```

- [ ] 核验 Open-Meteo 当前条款、地理编码和预报文档，使用HTTPS；如果部署用途不符合免费接口条款，在评测记录明确所需配置，不冒用免费权限。
- [ ] 写失败测试：无默认城市、多个同名匹配、无结果、超时、非200、缺温度、未知天气码，以及请求取消；本地时间采用注入Clock和ZoneId。
- [ ] 运行目标测试观察失败；实现可替换HTTP传输，IO调度器、连接/读取各5秒超时，总请求10秒上限，取消时关闭连接；URL参数编码，不把完整语音作为城市请求参数。
- [ ] 解析温度和天气码时验证必要字段，未知码只说天气描述暂不可用。配置持久化只保存音色与已选择城市，开启状态不持久化。
- [ ] 目标测试通过后，人工联网核对指定城市返回；接口失败给出明确提示；提交 `feat: answer local workout questions and city weather`。

## Task 6: 本地音频管线与唤醒控制器

**Files:** Create `assistant/speech/VoskRecognizerAdapter.kt`, `assistant/speech/SherpaSynthesizerAdapter.kt`, `assistant/AssistantController.kt`; Test `assistant/AssistantControllerTest.kt`, Android `assistant/SpeechModelInstrumentedTest.kt`。

**Interfaces:** Consumes Task 2 adapters, Task 3 command API, Task 4 events, Task 5 repositories. Produces `AssistantController.enable()/disable()/close()`, state `StateFlow<AssistantUiState>`；`AssistantUiState` includes enabled、阶段枚举、最近识别文本、回应和错误。

- [ ] 写失败测试：8秒超时、一句话唤醒+命令、先唤醒后命令、中间结果不执行、每回合一次、播报时不识别；disable发生在初始化/天气/播放中后迟到回调无效。
- [ ] 执行控制器测试确认失败。
- [ ] 根据Task 1锁定SDK实现适配器：AudioRecord单个所有者，按模型采样率采PCM；后台推理，识别器停止后丢弃旧turnId。合成输出按模型采样率送AudioTrack，取消立即stop/flush。
- [ ] 控制器用父Job管理任务，每次enable/disable递增generation；异步回调先比较generation。唤醒等待、指令等待、处理和播报显式状态迁移，关闭后资源释放。
- [ ] 播报队列优先级：操作回应→准备提醒→问答→鼓励。完整录音不持久化；语音模型缺失或损坏显示错误并保持关闭，不能静默改用在线识别。
- [ ] 控制器测试通过，真实模型仪器测试识别评测WAV并产生男女两份非空可播放音频；提交 `feat: run offline wake and speech assistant pipeline`。

## Task 7: 前台服务、训练界面与权限

**Files:** Modify `reminder/WorkoutForegroundService.kt`, `session/SessionScreen.kt`, `app/src/main/AndroidManifest.xml`; Create `assistant/AssistantPanel.kt`; Test Android `assistant/AssistantPanelTest.kt`, `assistant/AssistantLifecycleTest.kt`。

**Interfaces:** Service owns AssistantController acquired with shared WorkoutRuntime; UI reads StateFlow and sends enable/disable, voice and city settings; notification stop action targets assistant only, leaving workout service active as required by training.

- [ ] 写失败UI/生命周期测试：权限拒绝、开关关闭、男女声保存、城市选择、旋转/返回页面、通知关闭、结束训练、销毁时释放。
- [ ] 执行目标测试观察失败。
- [ ] 添加RECORD_AUDIO、INTERNET、FOREGROUND_SERVICE_MICROPHONE权限和microphone服务类型；在可见Activity获授权后启动麦克风服务。使用目标SDK支持的后台限制流程；服务通知与原训练通知合并，防止重复播报。
- [ ] 页面增加开关和设置，状态文案来自控制器。默认城市输入触发搜索并让用户选择地区明确的结果；权限被拒绝时回退开关并解释。
- [ ] 处理音频焦点丢失、录音错误、权限撤销：停止助教、更新状态；重新开始需用户开启。服务START_NOT_STICKY，进程恢复不启动麦克风。锁屏保持已开启服务工作。
- [ ] UI测试通过；记录API26/目标API生命周期验证和可用设备上的锁屏、来电/音频抢占、蓝牙结果；提交 `feat: expose assistant controls and microphone lifecycle`。

## Task 8: 集成验收、包体核验与交付

**Files:** Create `tools/assistant/verify-package.ps1`, `docs/validation/assistant-release.md`; Modify `README.md`, `app/build.gradle.kts` for version metadata and ABI packaging only。

- [ ] 运行 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`；在可用模拟器运行 `:app:connectedDebugAndroidTest`。检查所有失败原因，修复后只重跑受影响与必需检查。
- [ ] 重跑真实模型评测清单，输出逐条预期/识别/意图结果；人工试听选定男女声。语音识别效果、后台功能和模型初始化失败都有实际记录。
- [ ] 检查APK中模型、运行库、许可和正确ABI；将APK大小、首次初始化后安装/模型副本及缓存占用分别记录，执行：

```powershell
if ((Get-Item -LiteralPath $apkPath).Length -ge 500000000) { throw 'APK exceeds size budget' }
if ($installedStaticBytes -ge 500000000) { throw 'Installed footprint exceeds size budget' }
Get-FileHash -LiteralPath $apkPath -Algorithm SHA256
```

- [ ] 做一次完整训练：开助教→唤醒→完成组→延长30秒→10秒提醒→跳过→暂停→手动恢复→查询时间/指定城市天气→关闭→完成训练；检验每次操作仅一次。
- [ ] 最终全分支审查，重点核对Review Focus；没有真机证据的噪声识别、蓝牙和性能项目明确标“未验证”，不把它们报告为通过。
- [ ] 更新使用说明、模型来源和许可、限制及测量记录。提交 `feat: deliver validated offline training assistant`；按用户已授权交付方式合入原项目，生成下一版本根目录APK并比对哈希，不覆盖现有版本。

## 自审结果与执行交接

规格1–2覆盖Task 1/7/8；规格3覆盖Task 2/3/6；规格4覆盖Task 4/6；规格5覆盖Task 5；规格6覆盖Task 1/8；规格7覆盖Task 3/6/7；规格8–9覆盖所有验证步骤。五项Review Focus均分配到拥有对应实现的任务。具体模型版本与性能证据由Task 1产生，后续任务只能使用其已验证产物。

保留用户此前选择的native方式，由当前会话逐项实施。用户审阅本计划后开始执行；当前仅完成规格和计划，尚未安装模型依赖或修改产品代码。
