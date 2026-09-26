# Android Strength Training Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an installable offline Android app for planning and completing strength workouts, timing every rest interval, recommending stretches, and manually recording daily check-ins.

**Architecture:** A single Kotlin/Jetpack Compose Android app. Immutable domain models and a pure workout state machine own the rules; Room persists plans, active session snapshots, and check-ins. A small Android reminder adapter owns notifications and alarms. Bundled JSON plus original vector illustrations provide the curated catalog.

**Tech Stack:** Kotlin, Jetpack Compose, Room, AndroidX Lifecycle, kotlinx.serialization, JUnit, Android instrumented UI tests, Gradle/Android SDK.

**Spec:** `docs/superpowers/specs/2026-09-22-fitness-strength-training-design.md`

## Global Constraints

- First release supports Android phones only and has no account or server.
- Home shows 力量训练、徒手健身、有氧运动; only 力量训练 opens a complete flow.
- One exercise is a square parent card; it contains multiple editable child cards with weight, planned sets, planned reps per set, rest seconds, and notes.
- During training a tap on “完成本组” records completion only; it does not request actual reps or weight.
- Every set, including the last set of a child card and the last set of the workout, starts rest. Progress moves to the next card only after rest finishes or is skipped.
- Only “完成训练并打卡” creates a check-in. App reopen, exit, and partial completion never do.
- Exercise and stretch copy and illustrations are original, with a per-entry source, checked date, and externally opened video link. Custom exercises carry no inferred guidance or stretch mapping.
- Data stays in app-private Room storage. Uninstalling or clearing app data deletes it. Deliver a debug-installable APK first; store release work is outside this plan.

## Review Focus

- A second tap on “完成本组” while resting must not count another set (Task 5 unit test).
- A zero-second rest must still enter a coherent next-set/summary state without an alarm (Task 5 unit test).
- Reordering or deleting an unfinished card must not erase already completed work or point the active cursor at a missing card (Task 6 unit test).
- A denied notification or exact-alarm permission must preserve correct countdown and show a clear limitation message (Task 7 test).
- Reopening after the saved rest deadline has passed must advance exactly once, including after the final set (Task 7 test).

---

## File Map

- `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, `gradlew.bat`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`: Android build and app declaration.
- `app/src/main/java/com/fitflow/training/MainActivity.kt`, `ui/HomeScreen.kt`, `ui/AppNav.kt`: entry point and navigation.
- `catalog/Exercise.kt`, `catalog/CatalogRepository.kt`, `catalog/CatalogScreen.kt`, `app/src/main/assets/catalog.json`, `app/src/main/res/drawable/exercise_*.xml`: bundled exercise content and browser.
- `plan/PlanModels.kt`, `plan/PlanRules.kt`, `plan/PlanScreen.kt`: planned workout data, validation, and editor.
- `data/WorkoutDatabase.kt`, `data/WorkoutRepository.kt`: Room storage and persistence adapter.
- `session/SessionModels.kt`, `session/SessionReducer.kt`, `session/SessionScreen.kt`: set-by-set state machine and live UI.
- `reminder/RestReminder.kt`, `reminder/RestAlarmReceiver.kt`, `reminder/WorkoutForegroundService.kt`: Android timer notification boundary.
- `stretch/Stretch.kt`, `stretch/StretchRecommender.kt`, `stretch/FinishScreen.kt`, `app/src/main/assets/stretches.json`, `app/src/main/res/drawable/stretch_*.xml`: cooldown suggestions.
- `checkin/CheckInScreen.kt`, `settings/SettingsScreen.kt`: history and data/permission explanation.
- Corresponding JUnit tests in `app/src/test/java/com/fitflow/training/`; smoke/UI tests in `app/src/androidTest/java/com/fitflow/training/`.

## Task 1: Buildable Android shell

**Files:** Create build files, manifest, `MainActivity.kt`, `ui/HomeScreen.kt`, `ui/AppNav.kt`, and `app/src/androidTest/java/com/fitflow/training/HomeScreenTest.kt`.

**Interfaces:** Produces `AppNav()` and route names `home`, `catalog`, `plan`, `session`, `finish`, `checkin`, `settings`. Later tasks replace route placeholders with real screens. The app ID/package is `com.fitflow.training`.

- [ ] **Step 1: Prepare the toolchain and create a current stable Empty Activity Compose project.** Install a JDK, Android SDK, and Gradle wrapper if absent; use Android Studio's current stable template with package `com.fitflow.training`, minSdk 26. Record selected plugin and library versions in the version catalog and commit wrapper files. Run `./gradlew.bat --version` and `./gradlew.bat :app:assembleDebug`; both must exit 0.
- [ ] **Step 2: Write a failing UI smoke test.**

```kotlin
@Test fun homeShowsThreeModules() {
    composeRule.onNodeWithText("力量训练").assertExists()
    composeRule.onNodeWithText("徒手健身").assertExists()
    composeRule.onNodeWithText("有氧运动").assertExists()
}
```

Run `./gradlew.bat :app:connectedDebugAndroidTest` on an emulator/device; expect failure before the home UI exists.
- [ ] **Step 3: Implement `HomeScreen` with three modules and navigation.** The latter two display “即将开放” and have no working navigation. Wire `AppNav()` from `MainActivity`. Expose stable text labels for the test.
- [ ] **Step 4: Run `./gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug` and inspect the home screen at narrow phone width.**
- [ ] **Step 5: Commit shell and test:** `git add . && git commit -m "feat: create Android app shell"`.

## Task 2: Curated strength catalog

**Files:** Create `catalog/Exercise.kt`, `catalog/CatalogRepository.kt`, `catalog/CatalogScreen.kt`, `assets/catalog.json`, `res/drawable/exercise_*.xml`, `catalog/CatalogRepositoryTest.kt`, and catalog UI test.

**Interfaces:** `Exercise(id: String, name: String, bodyParts: Set<BodyPart>, difficulty: Difficulty, caution: String, sourceUrl: String, checkedOn: String, videoUrl: String, illustrationName: String?)`; `CatalogRepository.all(): List<Exercise>`; `CatalogRepository.search(query: String, bodyPart: BodyPart?, difficulty: Difficulty?): List<Exercise>`. `BodyPart` is an enum shared by stretches. `CatalogScreen(onAdd: (Exercise) -> Unit)`.

- [ ] **Step 1: Write failing repository tests** for case-insensitive name search, body-part filtering, three difficulty values, and rejection of a bundled item lacking source/video/date. Include a test such as:

```kotlin
assertEquals(
    listOf("barbell_squat"),
    repo.search("深蹲", BodyPart.LEGS, Difficulty.INTERMEDIATE).map { it.id }
)
```

Run `./gradlew.bat :app:testDebugUnitTest --tests '*CatalogRepositoryTest'`; expect failure.
- [ ] **Step 2: Create the model, JSON decoder, and search implementation.** Use exact enum values `BEGINNER`, `INTERMEDIATE`, `ADVANCED`; normalize whitespace and case in searches; reject duplicate IDs and blank required fields at load time.
- [ ] **Step 3: Curate a small, complete starter catalog.** Include at least one exercise for chest, back, shoulders, legs, arms, and core, and at least one in each difficulty tier. Start with squat, goblet squat, hip hinge, push-up, bench press, row, overhead press, curl, and plank; check each chosen variation against its linked source before publishing. Write original Chinese cautions and original vector pose diagrams; add a checked-on date and directly matching external demonstration video URL to every item. Open each URL and verify the target before committing. Do not ship a catalog record if any required field or URL is missing.
- [ ] **Step 4: Implement directory/search/filter UI and the “+” callback.** Show illustration, caution, and `ACTION_VIEW` video/source links in the detail sheet. Add a UI test that filters by 腿部 and taps “+”.
- [ ] **Step 5: Run catalog unit and UI tests, then commit:** `git add app && git commit -m "feat: add curated strength catalog"`.

## Task 3: Plan rules and local persistence

**Files:** Create `plan/PlanModels.kt`, `plan/PlanRules.kt`, `data/WorkoutDatabase.kt`, `data/WorkoutRepository.kt`, `plan/PlanRulesTest.kt`, and Room migration/round-trip tests.

**Interfaces:** `PlannedBlock(id: String, weightKg: BigDecimal, sets: Int, reps: Int, restSeconds: Int, note: String)`; `PlannedExercise(id: String, exerciseId: String?, customName: String?, blocks: List<PlannedBlock>)`; `WorkoutPlan(date: LocalDate, exercises: List<PlannedExercise>)`. `ValidationResult(errors: Map<String, String>)` is valid when `errors.isEmpty()`; `PlanRules.validate(block): ValidationResult`; `WorkoutRepository.savePlan(plan)`, `loadPlan(date)`, `saveSession(snapshot)`, `loadActiveSession()`, `saveCheckIn(checkIn)`, `checkIns()` are suspend functions. Use a serialization converter for immutable nested models in Room and preserve a schema version.

- [ ] **Step 1: Write failing rule tests** for negative weight, zero sets/reps, negative rest, blank custom name, duplicate IDs, and reorder/delete preserving other blocks. Pin boundaries: weight `0..1000 kg`, sets `1..100`, reps `1..1000`, rest `0..3600 s`, note at most 500 characters.
- [ ] **Step 2: Implement immutable plan models and validation.** Return field-specific errors, not a generic Boolean. A custom exercise has `exerciseId=null` and nonblank `customName`; a library exercise has a known `exerciseId`.
- [ ] **Step 3: Write and run a failing Room round-trip test** that saves two exercises with multiple blocks and notes, closes/reopens the database, and asserts exact order and values.
- [ ] **Step 4: Implement Room entities/DAO/repository.** Save a plan transactionally; include a unique date key for one current-day plan. Version the database and write an explicit migration if the schema changes later; never use destructive migration for user workouts.
- [ ] **Step 5: Run `./gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest`; commit:** `git add app && git commit -m "feat: persist validated workout plans"`.

## Task 4: Today's plan editor

**Files:** Create `plan/PlanScreen.kt`, `plan/PlanViewModel.kt`, `plan/PlanScreenTest.kt`; modify `catalog/CatalogScreen.kt` and `ui/AppNav.kt`.

**Interfaces:** `PlanViewModel.addExercise(exerciseId)`, `addCustomExercise(name)`, `addBlock(parentId, block)`, `editBlock(parentId, block)`, `deleteBlock(parentId, blockId)`, `deleteExercise(parentId)`, `moveExercise(parentId, toIndex)`, `startWorkout()`. All edits call `PlanRules.validate` and `WorkoutRepository.savePlan`.

- [ ] **Step 1: Write a failing Compose UI test**: choose a catalog exercise, enter `重量=40`, `组数=3`, `每组次数=8`, `休息=90`, `备注=热身后进行`, save, add a second block, reorder exercises, reopen screen, and assert persisted order/fields.
- [ ] **Step 2: Implement square parent cards and a modal child-card editor.** Keep cards square at phone width without clipping text; scroll child content if necessary. Add edit/delete/reorder controls and a confirmation before deleting the active plan.
- [ ] **Step 3: Wire custom-name creation, validation errors, and disabled Start when no valid block exists.** A manually added name can be planned like a catalog exercise but shows no fabricated illustration or advice.
- [ ] **Step 4: Run UI tests on narrow and standard emulators; commit:** `git add app && git commit -m "feat: edit today's workout plan"`.

## Task 5: Pure session progression

**Files:** Create `session/SessionModels.kt`, `session/SessionReducer.kt`, and `session/SessionReducerTest.kt`.

**Interfaces:** `SessionSnapshot(id: String, plan: WorkoutPlan, completedBlockSets: Map<String, Int>, phase: Phase, currentBlockId: String?, restEndsAtEpochMs: Long?)`; `Phase = READY | RESTING | FINISHED`. `SessionReducer.start(plan, sessionId)`, `completeSet(snapshot, nowMs)`, `skipRest(snapshot)`, `extendRest(snapshot, seconds)`, `reconcile(snapshot, nowMs)` return a new snapshot. Only the reducer may change session progress.

- [ ] **Step 1: Write failing tests** for a two-exercise, multiple-block plan. Assert each tap increments one set; every set enters `RESTING`; a tap during `RESTING` changes nothing; a last-block last-set rest precedes `FINISHED`; zero rest advances immediately; skip and extend preserve set counts.
- [ ] **Step 2: Implement pure reducer and cursor lookup by stable block ID.** Use a deterministic `nowMs` input rather than reading the clock inside rules. Clamp completed counts to planned counts. Reject empty plans at start.
- [ ] **Step 3: Run `./gradlew.bat :app:testDebugUnitTest --tests '*SessionReducerTest'` and commit:** `git add app && git commit -m "feat: define workout session progression"`.

## Task 6: Session UI, editing, and recovery

**Files:** Create `session/SessionScreen.kt`, `session/SessionViewModel.kt`, `session/SessionViewModelTest.kt`; modify `plan/PlanScreen.kt`, `data/WorkoutRepository.kt`, and navigation.

**Interfaces:** `SessionViewModel.completeSet()`, `skipRest()`, `extendRest(seconds)`, `editUnfinishedBlock(...)`, `deleteUnfinishedBlock(id)`, `resume()`; each action persists the returned `SessionSnapshot` before UI navigation. Editing completed sets is rejected; pending cards may be changed with a confirmation explaining impact.

- [ ] **Step 1: Write failing state tests** for reopen after one completed set, edit of an unfinished block, deletion of the next block, and reorder while resting. Assert completed sets remain and the current cursor resolves to an existing block.
- [ ] **Step 2: Implement the ViewModel around repository plus reducer.** Serialize state mutations, save snapshots atomically, and reconcile with current time on resume. Show current action, set index, next action, countdown, “完成本组”, skip, and extend controls.
- [ ] **Step 3: Add a UI test** that completes one set, recreates the activity, and sees the same completed count and remaining rest.
- [ ] **Step 4: Run unit/UI tests and commit:** `git add app && git commit -m "feat: run and restore workouts"`.

## Task 7: Android rest reminder

**Files:** Create `reminder/RestReminder.kt`, `reminder/RestAlarmReceiver.kt`, `reminder/WorkoutForegroundService.kt`, `reminder/RestReminderTest.kt`; modify `AndroidManifest.xml`, `session/SessionViewModel.kt`, `settings/SettingsScreen.kt`.

**Interfaces:** `RestReminder.schedule(restEndEpochMs: Long, sessionId: String): ReminderStatus`, `cancel(sessionId)`, `capability(): ReminderCapability`. The ViewModel schedules when phase becomes `RESTING`, cancels on skip/extend/replacement, and schedules the new deadline on extend. `ReminderCapability` exposes notification and exact-alarm availability to UI.

- [ ] **Step 1: Write failing tests** using a fake `RestReminder`: completion schedules once, skip cancels, extend replaces the deadline, denied capability emits the limitation banner while the reducer still advances at the saved time, and reopen after deadline reconciles exactly once.
- [ ] **Step 2: Implement a foreground workout notification and one-shot system alarm for the rest end.** Create notification channel, request `POST_NOTIFICATIONS` when required, check exact-alarm access before exact scheduling, and use a documented best-effort fallback with visible warning if denied. The receiver must look up the current session ID/deadline before alerting so an old alarm cannot notify after skip or extension.
- [ ] **Step 3: Test on a device/emulator with permission allowed and denied, screen locked, app backgrounded, and app reopened after the deadline.** Compare displayed remaining time against the saved deadline; do not promise that vendor power management will deliver every alert exactly on time.
- [ ] **Step 4: Run all unit/UI tests and commit:** `git add app && git commit -m "feat: notify when rest finishes"`.

## Task 8: Stretch suggestions and manual check-ins

**Files:** Create `stretch/Stretch.kt`, `stretch/StretchRecommender.kt`, `stretch/FinishScreen.kt`, `assets/stretches.json`, `res/drawable/stretch_*.xml`, `checkin/CheckInScreen.kt`, `stretch/StretchRecommenderTest.kt`, and `checkin/CheckInTest.kt`; modify repository/navigation.

**Interfaces:** `Stretch(id, bodyParts, steps, caution, holdSeconds, sourceUrl, checkedOn, videoUrl, illustrationName)`; `StretchRecommender.forSession(snapshot, catalog): List<Stretch>`. `CheckIn(date: LocalDate, completedExerciseIds: List<String>, planSnapshot: WorkoutPlan)`; `WorkoutRepository.saveCheckIn` is idempotent by date.

- [ ] **Step 1: Write failing tests**: suggestions follow only completed catalog exercises; duplicate body-part matches do not duplicate stretches; custom exercises without mapping produce no invented stretch; partial sessions cannot check in; double confirm on one date produces one record.
- [ ] **Step 2: Implement a curated stretch set** for the catalog's body parts using original Chinese steps, cautions, vector diagrams, source/date, and verified external video links. Validate nonblank source/video/date and positive hold time when loading JSON.
- [ ] **Step 3: Implement finish summary, external video `ACTION_VIEW`, manual “完成训练并打卡”, and a calendar/list of checked-in days with the saved plan parameters.** Mark the session closed only after the check-in transaction succeeds.
- [ ] **Step 4: Run unit and UI tests; commit:** `git add app && git commit -m "feat: recommend stretches and record check-ins"`.

## Task 9: Release verification and APK

**Files:** Modify `settings/SettingsScreen.kt`, `app/src/main/res/values/strings.xml`, `README.md`; create `app/src/androidTest/java/com/fitflow/training/WorkoutJourneyTest.kt`.

**Interfaces:** No new domain interface. Output `app/build/outputs/apk/debug/app-debug.apk` and documented install command.

- [ ] **Step 1: Write an end-to-end UI test**: add a catalog action and two child blocks, start, complete all sets including final rests, open a stretch video link, manually check in, and confirm one history entry. Assert that backing out before confirmation creates none.
- [ ] **Step 2: Add Settings text** explaining local-only storage, uninstall/clear-data deletion, notification limitations, and content sources. Add `README.md` with prerequisites, `./gradlew.bat :app:assembleDebug`, and `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 3: Run `./gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug`.** Inspect UI manually on a narrow Android phone/emulator, lock the device during a rest interval, reopen after expiry, and verify all links. Record any device-specific reminder limitation in README.
- [ ] **Step 4: Check the APK exists and installs with `adb install -r app/build/outputs/apk/debug/app-debug.apk`; launch and complete one workout. Commit:** `git add app README.md && git commit -m "test: verify Android workout release"`.

## Execution Notes

- The current machine did not expose `java`, `gradle`, or `adb` on PATH when this plan was written. Task 1 must inspect installed Android Studio/SDK locations and set up only missing tools before running builds.
- Keep dependency versions together in `gradle/libs.versions.toml`; use versions supported by the installed stable Android Gradle plugin and SDK. Do not silently substitute a web wrapper for the native Android app.
- The debug APK is for direct installation and review. Public store distribution, signing key custody, and any cross-device synchronization require a later plan.
