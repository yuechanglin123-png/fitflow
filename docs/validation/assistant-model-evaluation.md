# 离线训练助教验证记录

日期：2026-09-24。Windows 开发机，Android API 36 x86_64 模拟器。未连接实体手机；未做健身房噪声、远场或口音验收。

开发机报告 CPU：Intel64 Family 6 Model 183 Stepping 1。模拟器测试的 TTS 阶段进程 PSS 单点记录为625,796 KiB，约611 MiB；这是运行内存观测值，不是完整峰值，也不是 APK/安装存储。不能据此推算 ARM 手机内存或延迟。

## 模型选择

| 用途 | 最终选择 | 配置 |
|---|---|---|
| 中文识别 | Zipformer Chinese 14M 2023-02-23 | int8 encoder/joiner，FP32 decoder，16 kHz，modified_beam_search，热词分数 1.5 |
| 中文播报 | Kokoro v1.1-zh int8 | 24 kHz，女声 3 / zf_001，男声 58 / zm_009，2线程 |
| 原生推理 | sherpa-onnx 1.12.26 | 官方 Android AAR，arm64-v8a / x86_64 |

ASR 模型文件约 24.7 MB。TTS 仅打包中文词典、所需模型及前端资源；不打包英文词典和未使用的候选模型。大权重直接从 APK 读取，只把前端字典复制到应用私有目录。全部权重随 APK 提供，无首次下载。

Vosk Small CN 0.22、AISHELL3 8kHz VITS、AISHELL3 标准 int8 VITS 均参与候选比较，未采用：前者在本项目语音集识别较差，后两者合成往返识别明显差于 Kokoro。候选下载没有进入发布 APK。

来源、归档 SHA-256、每个打包文件的大小与校验值：`tools/assistant/model-manifest.json`。运行库 Apache-2.0；两种权重 Apache-2.0；运行库还含 eSpeak NG GPLv3，详见 `app/src/main/assets/assistant/licenses/NOTICE.txt`。不能将整个二进制标为 Apache-only。

## 实际语音评测

- 独立 Windows SAPI 中文男女声样本：40条唤醒词加指令/问句，20条没有唤醒词的日常/否定/复合句。
- 真实 Zipformer + 固定热词识别：40/40 正样本完整文字正确；20/20 负样本没有触发唤醒/操作。
- 识别输出保存在 `app/src/test/resources/assistant/asr-corpus.json`，`ModelCorpusTest` 使用产品解析器核验正确意图 >=90%、负样本操作数为0。
- 独立语音文件另有1条进入 Android 测试资源，实测 Android 原生模型识别“小练小练完成本组”。
- Kokoro 自合成的60条样本另做往返识别：40条正样本中32条完整匹配；主要偏差为同音字和问句。未把这组自合成样本冒充真人识别率，也未把独立 SAPI 的100%结果当成噪声场景准确率。
- Kokoro 桌面初始化约3.86秒；60条短句合成平均11.08秒、最大15.26秒。动态回答可能需等待数秒以上，实际速度取决于手机。
- 对提醒、唤醒回应、常用操作反馈及结束提示预合成本地 PCM，运行时直接播放，避开这些短提示的实时合成等待；切换声音选择对应缓存。时间、日期、进度和天气等动态内容仍由本地模型生成。
- 代码默认在播报时停止录音，播报后创建新录音实例，清空旧缓冲，避免自触发。

这些是合成样本和模拟器验证；尚无真人听感评分、实体 ARM 手机延迟/内存峰值、健身房背景音乐与口音报告。

## 自动化验证与修复

覆盖：严格整句解析、否定和多命令拒绝、唤醒与最终结果、重复回调、识别期间手动切组、关闭时初始化/天气取消、最终播报后关闭、状态原子更新、暂停计时、提醒去重、不补播鼓励、天气失败/字段缺失/同名城市、Android真实模型加载、界面开关与音色选择。

独立代码审查提出的三处问题已补回归：通知栏关闭入口；最终完成播报生命周期；自动休息结束与闹钟竞争。完整测试与最终体积记录随交付 APK 写入同目录发布记录。

## 重建

1. PowerShell 运行 `tools/assistant/prepare-models.ps1`，下载固定归档并逐文件校验；大型模型与 AAR 不提交到 Git。
2. 安装 Java 17 / Android SDK 36，设置 JAVA_HOME 与 ANDROID_HOME。
3. `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`。
4. 有模拟器时运行 `:app:connectedDebugAndroidTest`；静态检查运行 `:app:lintDebug`。

固定提示可通过 `cache-prompts.py` 重建，Python依赖 sherpa-onnx==1.12.26、numpy。评测脚本另外使用 soundfile 和 vosk==0.3.45。
