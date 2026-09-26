# 逐组卡片与动作间休息 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将力量训练计划改成可逐组修改的方形卡片，并在相邻动作间运行独立休息倒计时。

**Architecture:** 沿用 Room 中的 JSON 文本和现有 `PlannedBlock` 类型，但新计划里每个 `PlannedBlock` 只代表一组（`sets == 1`），以稳定 ID 保存该组参数。读取旧版多组数据时由独立转换器按原顺序展开，并转换活动训练的完成计数。状态机根据当前组后是否还有同动作小组或下一动作决定休息种类。

**Tech Stack:** Kotlin 2.2.20、Jetpack Compose、Room、kotlinx.serialization、JUnit、AndroidX instrumentation tests、Android SDK API 36；minSdk 26。

**Spec:** `docs/superpowers/specs/2026-09-22-strength-set-cards-and-exercise-rest-design.md`

## Global Constraints

- 新增动作仍支持一次填写重量、组数、次数、休息与备注，再生成逐组卡片。
- 动作容器纵向排列，每组卡片为正方形、从上到下排列；每组可单独修改重量、次数、休息与备注。
- 动作间休息默认 120 秒，可单独设置；最后一组后只执行动作间休息，训练最后一组后直接显示总结。
- 休息结束自动推进；保留跳过、延长 30 秒、后台提醒、恢复和手动打卡。
- 保留旧计划、进行中训练和历史打卡的数据；根目录交付更新的 APK。

## Review Focus

- 旧计划有多张模板且每张组数不同：展开后应保持模板间和组内顺序，ID 稳定且无重复。
- 旧训练已完成部分组数：转换后完成标记、当前组和正在运行的休息截止时间不得丢失。
- 动作末组与下一动作首组相邻：只运行动作间休息，不能叠加末组卡片的休息。
- 最后一个动作完成：直接显示总结，不触发多余提醒；零秒休息也应自动推进。
- 正在休息时修改未完成组：已完成组和当前截止时间保持不变，删除当前休息目标应拒绝或明确调整游标。

---

### Task 1: 逐组数据与旧数据转换

**Files:**
- Modify: `app/src/main/java/com/fitflow/training/plan/PlanModels.kt`
- Modify: `app/src/main/java/com/fitflow/training/plan/PlanRules.kt`
- Create: `app/src/main/java/com/fitflow/training/plan/LegacyPlanNormalizer.kt`
- Modify: `app/src/main/java/com/fitflow/training/data/WorkoutRepository.kt`
- Test: `app/src/test/java/com/fitflow/training/plan/LegacyPlanNormalizerTest.kt`
- Test: `app/src/androidTest/java/com/fitflow/training/data/WorkoutRepositoryTest.kt`

**Interfaces:**
- Produce `PlannedExercise.exerciseRestSeconds: Int = 120`.
- Produce `LegacyPlanNormalizer.normalize(plan: WorkoutPlan): WorkoutPlan` and `normalize(snapshot: SessionSnapshot): SessionSnapshot`.
- New writes use `PlannedBlock.sets == 1`; `PlannedBlock` retains `sets` to decode legacy JSON.

- [ ] **Step 1: Write failing normalization tests.** Use a plan with two old blocks (`sets=2`, `sets=3`) and assert five blocks in the same order, IDs `oldId:1` through `oldId:3`, all with `sets=1`, and unchanged weight/reps/rest/note. Normalize twice and assert equality. For a session with completed count 1 on the first old block, assert only `oldId:1` is complete, current ID maps to `oldId:2`, and `restEndsAtEpochMs` remains unchanged.

```kotlin
val converted = LegacyPlanNormalizer.normalize(oldPlan)
assertEquals(listOf("warm:1", "warm:2", "heavy:1", "heavy:2", "heavy:3"),
    converted.exercises.single().blocks.map { it.id })
assertEquals(converted, LegacyPlanNormalizer.normalize(converted))
assertEquals(deadline, LegacyPlanNormalizer.normalize(oldSession).restEndsAtEpochMs)
```
- [ ] **Step 2: Run `.\\gradlew.bat :app:testDebugUnitTest --tests 'com.fitflow.training.plan.LegacyPlanNormalizerTest'` from the ASCII worktree.** Expected: FAIL because the normalizer and new property do not exist.
- [ ] **Step 3: Implement the smallest converter.** Add `exerciseRestSeconds` with serialization default 120; validate 0–3600. Expand only blocks where `sets > 1` via `(1..block.sets).map { index -> block.copy(id = "${block.id}:$index", sets = 1) }`. Transfer each old completed count to the first N generated IDs, and translate `currentBlockId` to the first not-yet-completed generated ID (or the last generated ID while resting). Leave already normalized single-set IDs unchanged.

```kotlin
val expanded = if (block.sets == 1) listOf(block)
    else (1..block.sets).map { index -> block.copy(id = "${block.id}:$index", sets = 1) }
val completedIds = expanded.take(oldCompleted.coerceIn(0, expanded.size))
    .associate { it.id to 1 }
```
- [ ] **Step 4: Normalize on `loadPlan`, `loadSession`, `latestSession` and check-in reads; persist converted active plan/session after successful read.** Add an Android repository test with legacy JSON payloads, reopen the database, and assert the converted values survive a second read. Decode the historic `CheckIn.plan` through the same converter before display without changing the check-in date or catalog IDs.

```kotlin
val decoded = Json.decodeFromString<WorkoutPlan>(stored.payload)
val normalized = LegacyPlanNormalizer.normalize(decoded)
if (normalized != decoded) savePlan(normalized)
return normalized
```
- [ ] **Step 5: Run the unit and repository tests, then commit.** Expected: PASS, including idempotence and partial completion cases. Commit message: `feat: normalize legacy workout blocks into sets`.

### Task 2: 批量生成与逐组计划界面

**Files:**
- Modify: `app/src/main/java/com/fitflow/training/plan/PlanViewModel.kt`
- Modify: `app/src/main/java/com/fitflow/training/plan/PlanScreen.kt`
- Modify: `app/src/main/java/com/fitflow/training/plan/PlanRules.kt`
- Test: `app/src/androidTest/java/com/fitflow/training/plan/PlanScreenTest.kt`
- Test: `app/src/test/java/com/fitflow/training/plan/PlanRulesTest.kt`

**Interfaces:**
- Produce `PlanViewModel.addBlock(parentId: String, template: PlannedBlock)` that appends `template.sets` independent blocks with unique IDs and `sets=1`.
- Produce `PlanViewModel.editBlock(parentId: String, block: PlannedBlock)` for one group; reject `sets != 1`.
- Produce `PlanViewModel.editExerciseRest(parentId: String, seconds: Int)`; validate 0–3600.

- [ ] **Step 1: Write failing UI and rule tests.** In Compose test, enter 3 sets, save, assert three `第 1/2/3 组` cards; edit group 2 to a different weight/reps/rest and assert groups 1 and 3 unchanged. Assert each card has `Modifier.aspectRatio(1f)` via its measured width/height within one pixel. Change action rest and reopen the screen to assert persistence. In rule test reject negative and over-3600 action rest.

```kotlin
rule.onNodeWithText("第 2 组").assertExists()
rule.onNodeWithText("第 3 组").assertExists()
assertFalse(PlanRules.validate(exercise.copy(exerciseRestSeconds = 3601)).valid)
```
- [ ] **Step 2: Run `:app:testDebugUnitTest` and the targeted PlanScreen instrumentation test.** Expected: FAIL on the new card count and edit behavior.
- [ ] **Step 3: Change `addBlock` to expand the template once using `UUID.randomUUID().toString()` per group; retain the batch editor's existing fields and note.** The per-group editor shows weight/reps/rest/note and always saves `sets=1`; keep template editor for adding more sets.

```kotlin
val newSets = List(template.sets) {
    template.copy(id = UUID.randomUUID().toString(), sets = 1)
}
changeExercise(parentId) { it.copy(blocks = it.blocks + newSets) }
```
- [ ] **Step 4: Replace the square outer action card with a vertical action container.** Render each block as a full-width square card in a column, label it by its position, and show action rest input/editing beside action controls. Keep reorder/delete action buttons and delete-group behavior. Ensure the entire page scrolls without nested fixed-height clipping.

```kotlin
Card(Modifier.fillMaxWidth()) {
    Column {
        Text(title)
        exercise.blocks.forEachIndexed { index, block ->
            Card(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Text("第 ${index + 1} 组 · ${block.weightKg} kg · ${block.reps} 次")
            }
        }
    }
}
```
- [ ] **Step 5: Run the targeted tests and commit.** Expected: PASS on batch creation, individual edit, persistence, validation, and narrow emulator screen. Commit message: `feat: plan workouts with individual set cards`.

### Task 3: 训练状态机与两种休息

**Files:**
- Modify: `app/src/main/java/com/fitflow/training/session/SessionModels.kt`
- Modify: `app/src/main/java/com/fitflow/training/session/SessionReducer.kt`
- Test: `app/src/test/java/com/fitflow/training/session/SessionReducerTest.kt`

**Interfaces:**
- Produce `enum class RestKind { SET, EXERCISE }` and optional `SessionSnapshot.restKind: RestKind? = null` for old JSON compatibility.
- `SessionReducer.completeSet(snapshot, nowMs)` completes the current single-set block and returns READY/RESTING/FINISHED as specified.
- `SessionReducer.reconcile(snapshot, nowMs)`, `skipRest`, and `extendRest` preserve automatic progression.

- [ ] **Step 1: Write failing reducer tests.** Build a plan with action A containing two single-set blocks with 30 and 45 second rest, `exerciseRestSeconds=120`, plus action B with one block. Assert first set starts SET rest for 30 seconds; A's last set starts EXERCISE rest for 120 seconds (not 45); B's final set returns FINISHED immediately with no deadline. Test 0-second SET and EXERCISE rests, skip, extend, and reconcile after reopening.

```kotlin
val afterA2 = SessionReducer.completeSet(readyForA2, 100_000L)
assertEquals(RestKind.EXERCISE, afterA2.restKind)
assertEquals(220_000L, afterA2.restEndsAtEpochMs)
assertEquals(Phase.FINISHED, SessionReducer.completeSet(readyForB1, 230_000L).phase)
```
- [ ] **Step 2: Run the targeted reducer test.** Expected: FAIL on action-rest timing and final-set phase.
- [ ] **Step 3: Implement transition selection.** After marking the current ID complete, search ordered blocks in the same action first, then subsequent actions with blocks. Set `RestKind.SET` and current block's rest when the next group shares the action; set `RestKind.EXERCISE` and `exerciseRestSeconds` when crossing action boundary; return FINISHED directly when none remain. `reconcile` and `skipRest` set the next cursor and clear deadline/kind.

```kotlin
val (nextExercise, nextBlock) = nextUnfinishedSet(updatedPlan, completedIds)
    ?: return snapshot.copy(phase = Phase.FINISHED, restEndsAtEpochMs = null, restKind = null)
val sameExercise = nextExercise.id == currentExercise.id
val duration = if (sameExercise) currentBlock.restSeconds else currentExercise.exerciseRestSeconds
return snapshot.copy(phase = Phase.RESTING, restKind = if (sameExercise) RestKind.SET else RestKind.EXERCISE,
    restEndsAtEpochMs = nowMs + duration * 1000L)
```
- [ ] **Step 4: Run reducer tests and commit.** Expected: PASS for all timing, zero-second and final-set cases. Commit message: `feat: add separate rest between exercises`.

### Task 4: 训练界面、后台提醒和进度编辑

**Files:**
- Modify: `app/src/main/java/com/fitflow/training/session/SessionViewModel.kt`
- Modify: `app/src/main/java/com/fitflow/training/session/SessionScreen.kt`
- Modify: `app/src/main/java/com/fitflow/training/reminder/RestAlarmReceiver.kt`
- Test: `app/src/androidTest/java/com/fitflow/training/session/SessionPersistenceTest.kt`
- Test: `app/src/androidTest/java/com/fitflow/training/WorkoutJourneyTest.kt`

**Interfaces:**
- `SessionViewModel.editUnfinishedBlock(block)` edits exactly one unfinished set and requires `sets==1`.
- Reminder scheduling continues to use `restEndsAtEpochMs`; notification title/body distinguish SET and EXERCISE rest.

- [ ] **Step 1: Write failing persistence/UI tests.** Complete A's last set, assert UI says `动作间休息`; reopen during countdown and assert same deadline, kind and completed set. After expiry assert B is current. Edit an uncompleted group during rest and assert completed IDs and deadline stay fixed; deleting the active rest target is rejected. Finish final group and assert the summary button appears without another countdown.

```kotlin
assertEquals(RestKind.EXERCISE, reopened.restKind)
assertEquals(saved.restEndsAtEpochMs, reopened.restEndsAtEpochMs)
assertEquals(Phase.FINISHED, model.session.value?.phase)
```
- [ ] **Step 2: Run targeted instrumentation tests.** Expected: FAIL on exercise-rest copy, per-set progress, or final completion.
- [ ] **Step 3: Update UI to show per-group parameters and group position, and distinguish `组间休息` from `动作间休息`.** Keep skip/extend controls; add per-set edit/delete only for unfinished IDs. Update count display from summed multi-set counts to completed single-set IDs.

```kotlin
val restLabel = if (snapshot.restKind == RestKind.EXERCISE) "动作间休息" else "组间休息"
Text("$restLabel：${remaining} 秒")
```
- [ ] **Step 4: Update the alarm receiver to reconcile the saved snapshot on expiry, persist the result, and use `restKind` to explain which interval ended.** Preserve stale-session and deadline checks; stop foreground service when the reconciled phase is FINISHED.

```kotlin
val kind = state.restKind
val updated = SessionReducer.reconcile(state, System.currentTimeMillis())
if (updated == state) return@launch
repository.saveSession(updated)
val title = if (kind == RestKind.EXERCISE) "动作间休息结束" else "组间休息结束"
```
- [ ] **Step 5: Run the targeted instrumentation tests and commit.** Expected: PASS, including background resume and final-summary flow. Commit message: `feat: show per-set progress and exercise rest`.

### Task 5: 集成验证与根目录 APK

**Files:**
- Modify: `README.md`
- Modify: `app/src/main/java/com/fitflow/training/stretch/FinishScreen.kt` only if its plan rendering assumes multi-set blocks.
- Modify: `app/src/main/java/com/fitflow/training/checkin/CheckInScreen.kt` only if its plan rendering assumes multi-set blocks.
- Output: `练序-力量训练.apk` at the project root (delivery artifact; do not commit the binary).

**Interfaces:** No new product API; APK must install over the existing app without deleting local data.

- [ ] **Step 1: Run `rg 'block.sets|completedBlockSets|休息' app/src/main` and update only remaining user-facing multi-set assumptions.** Update README to explain batch creation, per-set edits, action rest, and the final group finishing immediately.
- [ ] **Step 2: Run `.\\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug --console=plain` from the ASCII worktree.** Expected: exit 0; all emulator tests pass. Resolve only concrete failures, then repeat the failed gate.
- [ ] **Step 3: Install `app/build/outputs/apk/debug/app-debug.apk` over the existing emulator app with `adb install -r`, launch it, and verify old saved plan/check-in data is still readable.** Exercise at least one set edit and one action-rest countdown.
- [ ] **Step 4: Copy the built APK to the project root as `练序-力量训练.apk`, compare source and destination SHA-256 hashes, and report the absolute path.** If worktree differs from the user's main checkout, fast-forward the reviewed commits into main and place the APK in that checkout's root.
- [ ] **Step 5: Run `git diff --check`, commit source/docs changes, and confirm the source tree is clean apart from the intentionally untracked APK.** Include the test result and any device-specific reminder limitation in the delivery note.
