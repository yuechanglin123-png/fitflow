# 完整训练计划预设 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为力量训练增加 7 个可编辑、可持久化的完整计划预设，并支持与今日计划互相复制、覆盖或追加。

**Architecture:** 新增独立 `preset` 功能包和 Room `presets` 表；预设顶层模型复用现有 `PlannedExercise`/`PlannedBlock`，但不保存日期。所有导入和保存通过纯转换函数深复制并重建 ID，界面层分别提供预设列表、预设编辑器和今日计划的槽位选择对话框。

**Tech Stack:** Kotlin 2.2.20、Jetpack Compose Material 3、Room 2.8.5、kotlinx.serialization 1.9.0、JUnit 4、AndroidX instrumentation tests、Android SDK API 36；minSdk 26。

**Spec:** `docs/superpowers/specs/2026-09-23-training-presets-design.md`

## Global Constraints

- 仅修改力量训练模块；预设槽位固定为 1–7。
- 预设支持动作库动作、自定义动作、动作顺序、重量、次数、每组休息、动作间休息和备注。
- 今日计划非空时，每次导入都让用户选择覆盖或追加；追加后的同名动作保持独立。
- 预设与今日计划必须深复制，后续修改互不影响；每次导入必须生成新的动作 ID 和小组 ID。
- 数据库升级必须保留现有今日计划、训练会话和打卡记录。
- 最终 APK 复制到主项目根目录并命名为 `第五版.apk`。

## Review Focus

- 连续快速点击同一预设两次：两次导入产生不同 ID，不得因主键或身份复用互相覆盖。
- 非空今日计划进入导入确认后预设被清空：确认动作必须重新读取并校验，失败时保留今日计划。
- 预设含动作但该动作没有小组：允许继续编辑和保存草稿，但禁止导入，并显示明确原因。
- 预设名称只有空格或超过 30 个字符：禁止保存名称，不写入损坏记录。
- 数据库从版本 3 升到版本 4：旧三张表的数据逐条可读，新表存在且 7 个空槽位由仓库层补齐。

---

### Task 1: 预设模型、校验与深复制规则

**Files:**
- Create: `app/src/main/java/com/fitflow/training/preset/TrainingPreset.kt`
- Create: `app/src/main/java/com/fitflow/training/preset/PresetRules.kt`
- Create: `app/src/main/java/com/fitflow/training/preset/PresetTransforms.kt`
- Test: `app/src/test/java/com/fitflow/training/preset/PresetTransformsTest.kt`
- Test: `app/src/test/java/com/fitflow/training/preset/PresetRulesTest.kt`

**Interfaces:**
- Produce `TrainingPreset(slot: Int, name: String, exercises: List<PlannedExercise>)`.
- Produce `PresetSlot(slot: Int, preset: TrainingPreset?)` and `ImportMode { REPLACE, APPEND }`.
- Produce `PresetRules.validateDraft(preset)` and `PresetRules.validateImport(preset)`.
- Produce `PresetTransforms.fromPlan(slot, name, plan)` and `PresetTransforms.importInto(plan, preset, mode, idFactory)`.

- [ ] **Step 1: Write failing model, rule and cloning tests.** Assert slot range 1–7, trimmed name length 1–30, a named empty draft is valid to store but not importable, and an action without blocks is not importable. Import the same preset twice with a deterministic `idFactory`; assert dates remain the target plan date, all generated IDs differ from preset IDs, and APPEND keeps duplicate catalog actions as two ordered entries.

```kotlin
val imported = PresetTransforms.importInto(today, preset, ImportMode.APPEND) { "new-${ids++}" }
assertEquals(today.date, imported.date)
assertEquals(listOf("existing", "bench", "bench"), imported.exercises.map { it.exerciseId })
assertTrue(imported.exercises.flatMap { it.blocks }.none { it.id in presetBlockIds })
```

- [ ] **Step 2: Run `.\gradlew.bat :app:testDebugUnitTest --tests 'com.fitflow.training.preset.*' --console=plain`.** Expected: FAIL because the preset types and transforms do not exist.

- [ ] **Step 3: Implement serializable models and two validation levels.** Draft validation accepts an empty exercise list but validates slot, trimmed name and every existing action/block. Import validation additionally requires at least one action and at least one block in every action.

```kotlin
@Serializable
data class TrainingPreset(val slot: Int, val name: String, val exercises: List<PlannedExercise>)
data class PresetSlot(val slot: Int, val preset: TrainingPreset?)
enum class ImportMode { REPLACE, APPEND }

fun validateImport(preset: TrainingPreset): ValidationResult {
    val errors = validateDraft(preset).errors.toMutableMap()
    if (preset.exercises.isEmpty()) errors["exercises"] = "预设至少需要一个动作"
    if (preset.exercises.any { it.blocks.isEmpty() }) errors["blocks"] = "每个动作至少需要一组"
    return ValidationResult(errors)
}
```

- [ ] **Step 4: Implement copying with a supplied ID factory.** Rebuild every action and block ID, retain exercise catalog/custom identity and parameters, and concatenate only for APPEND.

```kotlin
private fun cloneExercises(items: List<PlannedExercise>, idFactory: () -> String) = items.map { exercise ->
    exercise.copy(
        id = idFactory(),
        blocks = exercise.blocks.map { block -> block.copy(id = idFactory(), sets = 1) },
    )
}
```

- [ ] **Step 5: Run the targeted tests and commit.** Expected: PASS for invalid names, empty drafts, duplicate action preservation, target date, unique IDs and source immutability. Commit message: `feat: define reusable training presets`.

### Task 2: Room 表、版本迁移与仓库存取

**Files:**
- Modify: `app/src/main/java/com/fitflow/training/data/WorkoutDatabase.kt`
- Modify: `app/src/main/java/com/fitflow/training/data/WorkoutRepository.kt`
- Test: `app/src/androidTest/java/com/fitflow/training/data/PresetRepositoryTest.kt`
- Test: `app/src/androidTest/java/com/fitflow/training/data/WorkoutDatabaseMigrationTest.kt`

**Interfaces:**
- Produce `StoredPreset(slot: Int, name: String, payload: String)` and `PresetDao`.
- Produce repository methods `listPresetSlots()`, `loadPreset(slot)`, `savePreset(preset)` and `clearPreset(slot)`.
- Expose `WorkoutRepository.MIGRATION_3_4` as `internal` for the migration test.

- [ ] **Step 1: Write a failing repository test.** Save presets in slots 2 and 7, reopen the repository, assert `listPresetSlots()` always returns slots 1 through 7 in order with the other five empty, then clear slot 2 and assert only slot 7 remains populated.

```kotlin
assertEquals((1..7).toList(), repository.listPresetSlots().map { it.slot })
assertEquals("腿部", repository.loadPreset(2)?.name)
repository.clearPreset(2)
assertNull(repository.loadPreset(2))
```

- [ ] **Step 2: Write a failing version-3 migration test.** Create a version-3 SQLite database containing `plans`, `sessions` and `checkins`, insert one marker row in each, set `PRAGMA user_version = 3`, then open it with Room plus `MIGRATION_3_4`. Assert all marker rows remain and `SELECT name FROM sqlite_master WHERE name='presets'` returns a row.

```sql
CREATE TABLE IF NOT EXISTS `presets` (`slot` INTEGER NOT NULL, `name` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`slot`))
```

- [ ] **Step 3: Run the two instrumentation test classes.** Expected: FAIL because version 4, the DAO and repository methods do not exist.

- [ ] **Step 4: Add the entity, DAO and migration.** Increment `WorkoutDatabase` to version 4; add `presetDao()`. `savePreset` must call `PresetRules.validateDraft`, serialize with `Json.encodeToString`, and use the slot/name from the model. `listPresetSlots` maps database rows by slot and fills missing slots with `PresetSlot(slot, null)`.

```kotlin
@Dao interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY slot") suspend fun all(): List<StoredPreset>
    @Query("SELECT * FROM presets WHERE slot = :slot LIMIT 1") suspend fun find(slot: Int): StoredPreset?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: StoredPreset)
    @Query("DELETE FROM presets WHERE slot = :slot") suspend fun delete(slot: Int)
}
```

- [ ] **Step 5: Run repository and migration tests, then commit.** Expected: PASS with seven ordered slots and preserved v3 marker rows. Commit message: `feat: persist seven training preset slots`.

### Task 3: 预设编辑状态与页面

**Files:**
- Create: `app/src/main/java/com/fitflow/training/preset/PresetViewModel.kt`
- Create: `app/src/main/java/com/fitflow/training/preset/PresetListScreen.kt`
- Create: `app/src/main/java/com/fitflow/training/preset/PresetEditScreen.kt`
- Test: `app/src/androidTest/java/com/fitflow/training/preset/PresetViewModelTest.kt`
- Test: `app/src/androidTest/java/com/fitflow/training/preset/PresetScreensTest.kt`

**Interfaces:**
- `PresetViewModel(repository, slot)` exposes `StateFlow<TrainingPreset>` and mirrors plan editing operations for one slot.
- `PresetListScreen(onBack, onEditSlot)` displays exactly seven slots and performs confirmed clearing.
- `PresetEditScreen(slot, selectedExerciseId, onBack, onAddCatalog)` edits one preset.

- [ ] **Step 1: Write failing ViewModel tests.** Load an empty slot, rename it, add one catalog action, batch-add three groups, edit group 2, move/delete actions, change exercise rest and reopen. Assert all changes persist and only group 2 changes.

```kotlin
model.rename("胸部训练")
val parentId = model.addExercise("barbell_bench_press")
model.addBlock(parentId, template.copy(sets = 3))
assertEquals(3, model.preset.value.exercises.single().blocks.size)
```

- [ ] **Step 2: Run the targeted ViewModel test.** Expected: FAIL because `PresetViewModel` does not exist.

- [ ] **Step 3: Implement `PresetViewModel` with focused mutation methods.** Reuse `PlanRules` for action/block validation and `PresetRules.validateDraft` before persistence. Generate one ID per generated group and trim names before saving.

```kotlin
private suspend fun update(next: TrainingPreset) {
    require(PresetRules.validateDraft(next).valid)
    repository.savePreset(next)
    mutablePreset.value = next
}
```

- [ ] **Step 4: Write failing Compose tests for the list and editor.** Assert seven numbered slots, empty labels, summary counts, clear confirmation, name editing, custom action creation, catalog-add callback, batch group cards, two-line clickable notes and parameter editors.

```kotlin
rule.onAllNodesWithText("尚未设置").assertCountEquals(7)
rule.onNodeWithText("预设 1").performClick()
rule.onNodeWithText("预设名称").performTextReplacement("胸部训练")
```

- [ ] **Step 5: Implement the two screens using existing visual conventions.** Extract or reuse `BlockEditor`, `SetEditor`, `CompactNote` and `NoteDetailDialog`; do not copy parameter validation logic. The editor shows an import-blocking message when `validateImport` fails but still saves a valid draft.

- [ ] **Step 6: Run targeted tests and commit.** Expected: PASS for seven slots, edits, clearing, persistence, summaries and invalid-draft messaging. Commit message: `feat: edit complete workout presets`.

### Task 4: 动作库入口与预设导航

**Files:**
- Modify: `app/src/main/java/com/fitflow/training/catalog/CatalogScreen.kt`
- Modify: `app/src/main/java/com/fitflow/training/ui/AppNav.kt`
- Test: `app/src/androidTest/java/com/fitflow/training/catalog/CatalogPresetNavigationTest.kt`

**Interfaces:**
- Add routes `PRESETS` and `PRESET_EDIT`.
- Extend navigation state with `presetSlot: Int?` and catalog destination ownership.
- Add `CatalogScreen.onOpenPresets` beside `onOpenPlan`.

- [ ] **Step 1: Write a failing navigation test.** From the action library, assert “训练预设” is beside “今日计划”; open slot 3, choose “从动作库添加”, add one exercise, and assert returning to slot 3 shows that exercise rather than modifying today's plan.

- [ ] **Step 2: Run the targeted test.** Expected: FAIL because the button and routes are missing.

- [ ] **Step 3: Replace the two-field back-stack tuple with a typed navigation state.** Persist route, selected exercise and preset slot with `rememberSaveable`; when catalog is opened from a preset editor, its add callback returns to the same slot.

```kotlin
data class NavState(val route: String, val exerciseId: String? = null, val presetSlot: Int? = null)
const val PRESETS = "presets"
const val PRESET_EDIT = "preset-edit"
```

- [ ] **Step 4: Add the action-library header button and route wiring.** Keep “今日计划” and “训练预设” in one compact row beneath the title. Wire list back navigation and editor catalog navigation without losing the slot.

- [ ] **Step 5: Run navigation plus existing catalog/plan tests and commit.** Expected: PASS with no regression to adding an action to today's plan. Commit message: `feat: navigate between catalog and presets`.

### Task 5: 今日计划保存与导入预设

**Files:**
- Modify: `app/src/main/java/com/fitflow/training/plan/PlanViewModel.kt`
- Modify: `app/src/main/java/com/fitflow/training/plan/PlanScreen.kt`
- Create: `app/src/main/java/com/fitflow/training/preset/PresetPickerDialog.kt`
- Test: `app/src/androidTest/java/com/fitflow/training/plan/PlanPresetFlowTest.kt`
- Test: `app/src/test/java/com/fitflow/training/preset/PresetTransformsTest.kt`

**Interfaces:**
- `PlanViewModel.saveAsPreset(slot, name)` deep-copies the current plan.
- `PlanViewModel.importPreset(slot, mode)` reloads the preset, validates it and atomically saves the resulting plan.
- `PresetPickerDialog` supports import selection and save-target selection.

- [ ] **Step 1: Extend failing transform tests for atomic failure cases.** Invalid/empty presets throw before producing a plan; APPEND with an empty current plan remains valid; 100 repeated imports yield no duplicate IDs.

- [ ] **Step 2: Write a failing full Compose flow test.** Create a two-action today plan, save it into empty slot 1 with a name, change today's plan, import slot 1 via REPLACE and assert exact preset order. Repeat with APPEND and assert both existing and duplicate catalog actions remain. Save to an occupied slot and assert overwrite confirmation appears.

```kotlin
rule.onNodeWithText("保存为预设").performClick()
rule.onNodeWithText("槽位 1").performClick()
rule.onNodeWithText("导入预设").performClick()
rule.onNodeWithText("追加到末尾").performClick()
```

- [ ] **Step 3: Run the targeted tests.** Expected: FAIL because PlanViewModel and PlanScreen have no preset operations.

- [ ] **Step 4: Implement atomic ViewModel operations.** `importPreset` reloads the slot immediately before applying the selected mode, calls `PresetRules.validateImport`, then performs one `savePlan`/state update. `saveAsPreset` calls `PresetTransforms.fromPlan`, preserving the slot but never sharing list objects or IDs.

```kotlin
suspend fun importPreset(slot: Int, mode: ImportMode) {
    val preset = requireNotNull(repository.loadPreset(slot))
    require(PresetRules.validateImport(preset).valid)
    update(PresetTransforms.importInto(plan.value, preset, mode) { UUID.randomUUID().toString() })
}
```

- [ ] **Step 5: Implement picker and confirmation dialogs.** Disable empty/invalid slots for import. If today's plan is empty, import immediately; otherwise always show “覆盖今日计划” and “追加到末尾”. Saving to an occupied slot shows the preset name and requires explicit overwrite confirmation.

- [ ] **Step 6: Run preset flow, plan screen and end-to-end workout tests, then commit.** Expected: PASS for direct import, replace, append, occupied-slot overwrite and existing workout start flow. Commit message: `feat: save and import workout presets`.

### Task 6: 文档、全量验证、复核与第五版 APK

**Files:**
- Modify: `README.md`
- Output: `第五版.apk` at the main project root; do not commit the binary.

**Interfaces:** No new product API. APK must install over the existing application without clearing local data.

- [ ] **Step 1: Update README with the seven-slot workflow.** Document editing presets, saving today's plan, importing with replace/append, independent copies and invalid draft behavior.

- [ ] **Step 2: Run source consistency checks.** Run `rg 'WorkoutDatabase|version =|MIGRATION_3_4|PRESETS|PRESET_EDIT|importPreset|saveAsPreset' app/src/main` and `git diff --check`; verify the version, route names and method signatures match this plan.

- [ ] **Step 3: Run full verification in separate stable gates.** First run `.\gradlew.bat :app:lintDebug --console=plain`; then run `.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug --console=plain`. Expected: both commands exit 0 and every emulator test passes.

- [ ] **Step 4: Request whole-branch code review and fix every Critical or Important finding.** Re-run the smallest affected test first, then both full gates if production code changes.

- [ ] **Step 5: Install and launch the built APK.** Run `adb install -r app/build/outputs/apk/debug/app-debug.apk`, launch `com.fitflow.training`, create one preset, import it with APPEND and verify existing saved plans/check-ins remain visible.

- [ ] **Step 6: Integrate and deliver.** Fast-forward the reviewed commits into the user's main checkout, copy `app/build/outputs/apk/debug/app-debug.apk` to its root as `第五版.apk`, compare SHA-256 hashes, and leave the APK untracked.

- [ ] **Step 7: Report completion.** Link the absolute `第五版.apk` path and summarize database migration, preset workflows, final test count and install verification.

