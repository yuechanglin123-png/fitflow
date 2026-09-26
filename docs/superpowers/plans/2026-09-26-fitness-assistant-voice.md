# Fitness Assistant Voice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the Android name “健身助手”, the “铁蛋” wake word, reliable command capture during the wake reply, slightly faster speech, and measured recognition improvements from the supplied recordings.

**Architecture:** Keep the existing sherpa-onnx ASR, strict intent parser, and Kokoro TTS. Split wake detection, duplex listening, and spoken prompt filtering into small units around `AssistantController`; evaluate command biasing against private real recordings before accepting any model change. Train weights only if a compatible full-precision checkpoint and adequate labeled, speaker-separated samples exist.

**Tech Stack:** Kotlin, Android AudioRecord/AudioTrack and AcousticEchoCanceler, sherpa-onnx 1.12.26, Kokoro, Python evaluation tools, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-26-fitness-assistant-voice-design.md`

## Global Constraints

- Preserve application ID `com.fitflow.training`, installed workout data, male/female voices, offline voice commands, and the five-second inactive wake timeout.
- Keep installed app size below 1 GB. Do not publish raw or identifiable derived user recordings or put them in the APK.
- An operation requires a final, unambiguous command in the active turn; reject negation, compound requests, replayed callbacks, and stale workout context.
- Do not call hotword biasing or text correction weight fine-tuning; report whether compatible checkpoint and recordings permitted actual supervised training.

## Review Focus

- A command spoken over the last word of “我在，请说指令” must be retained and executed once (Task 2 test).
- The assistant's own reply, heard through the microphone, must not execute a command or keep the wake window open (Task 2 test).
- “铁蛋，别暂停训练” and unrelated ambient speech must never pause training (Task 1 and Task 3 tests).
- Closing or finishing the workout during overlapping playback and capture must cancel both streams and discard late callbacks (Task 2 test).
- A speaker represented in training must not also count as an independent validation speaker; unpublished recordings must remain Git-ignored (Task 4 checks).

---

### Task 1: App name, wake phrase, and strict parsing

**Files:** Modify `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/fitflow/training/assistant/AssistantSpeechText.kt`, `AssistantController.kt` (stage label only), `AssistantPanel.kt`, `speech/SherpaRecognizer.kt`; test `app/src/test/java/com/fitflow/training/assistant/AssistantSpeechTextTest.kt`, `AssistantControllerTest.kt`.

**Interfaces:** `AssistantSpeechText.afterWake(text:String):String?` continues to return the suffix or null. `SherpaRecognizer.HOTWORDS` remains the source for Android stream creation.

- [ ] Write tests: `afterWake("铁蛋，完成本组") == "完成本组"`; old wake word, quoted wake, and negated command yield no action; app label is “健身助手”.
- [ ] Run `gradlew.bat :app:testDebugUnitTest` and confirm new wake assertions fail.
- [ ] Change only the exact Android label, wake recognition, UI copy, and hotword list. Add homophone variants only if Task 4 shows they are safe on real samples.
- [ ] Re-run unit tests; confirm expected passes and no old UI wake text remains in product source.
- [ ] Commit this cohesive change.

### Task 2: Duplex wake reply and cancellation

**Files:** Modify `app/src/main/java/com/fitflow/training/assistant/AssistantController.kt`, `app/src/main/java/com/fitflow/training/assistant/speech/SherpaRecognizer.kt`; create `app/src/main/java/com/fitflow/training/assistant/AssistantWakeReplyFilter.kt`; test `app/src/test/java/com/fitflow/training/assistant/AssistantControllerTest.kt`, `app/src/test/java/com/fitflow/training/assistant/AssistantWakeReplyFilterTest.kt`, `app/src/androidTest/java/com/fitflow/training/assistant/AssistantSessionTest.kt`.

**Interfaces:** `AssistantWakeReplyFilter.userText(recognized:String):String?` maps `"我在请说指令"` to null and `"我在请说指令完成本组"` to `"完成本组"`; `AssistantController` retains the current `SpeechRecognizerAdapter` and `SpeechSynthesizerAdapter` interfaces. `SherpaRecognizer` uses `VOICE_COMMUNICATION` and attaches `AcousticEchoCanceler` when available, releasing it with its `AudioRecord`.

- [ ] Write failing tests for command during an unfinished wake prompt, final result exactly once, self-prompt ignored, five seconds counted from wake reply start, and late callbacks after disable/finish ignored.
- [ ] Run focused `AssistantControllerTest` and `AssistantWakeReplyFilterTest`; confirm each new behavior fails before changes.
- [ ] Start the command capture before beginning the cached wake prompt; let that one prompt overlap recording. On a valid final user command, stop prompt audio and process through existing dispatcher. Keep other responses half-duplex, so reminders cannot wake the model.
- [ ] Add optional Android AEC to the capture lifecycle. If unavailable, keep capture functional and rely on strict prompt filtering; never treat acoustic cancellation as guaranteed.
- [ ] Run focused tests and Android session tests if an emulator is available; commit once overlap, timeout, and cancellation pass.

### Task 3: Speech speed and cached prompts

**Files:** Modify `app/src/main/java/com/fitflow/training/assistant/speech/SherpaSynthesizer.kt`, `tools/assistant/cache-prompts.py`, `app/src/main/assets/assistant/tts/prompts/*.pcm`; test `app/src/test/java/com/fitflow/training/assistant/SpeechSpeedTest.kt`, `app/src/androidTest/java/com/fitflow/training/assistant/NativeSpeechTest.kt`.

**Interfaces:** Keep `SpeechSynthesizerAdapter.speak(text:String,voice:VoiceChoice)` unchanged; cached prompt text and keys stay in `prompts.json`.

- [ ] Add `SpeechSpeedTest` asserting a shared `SherpaSynthesizer.SPEECH_SPEED == 1.0f`; extend native tests to check regenerated male/female wake and short response PCM for nonzero audio, duration, and intact tail.
- [ ] Run the focused unit test; confirm it fails with the old `0.92f` speed setting.
- [ ] Set Kokoro dynamic generation and `cache-prompts.py` to speed `1.0`; regenerate the ten prompts for each voice with the existing model and preserve AudioTrack head/tail padding.
- [ ] Run native speech tests and listen/inspect representative short prompts where playback is available; record source and output durations.
- [ ] Commit source and regenerated small PCM assets, excluding model weights.

### Task 4: Private real-speech corpus and measured adaptation

**Files:** Create `tools/assistant/evaluate-user-speech.py`, `tools/assistant/test_evaluate_user_speech.py` and a private manifest under `$env:TEMP/fitflow-user-speech/` outside the worktree; modify `tools/assistant/compare-recognition.py` only if needed; create `docs/validation/fitness-assistant-voice-evaluation.md` with aggregate results only.

**Interfaces:** Evaluation input is a private JSON manifest of `audio`, `speaker`, `environment`, `transcript`, `expectedIntent`, and `split`; output is aggregate baseline/candidate recognition, false operations, and latency. No audio path or speaker identity goes into committed report.

- [ ] Check the four M4A files, decode to 16 kHz mono PCM in `$env:TEMP/fitflow-user-speech`, obtain/verify utterance boundaries and transcripts; ask the user only where a phrase cannot be determined. Never copy recordings into the worktree.
- [ ] Write evaluator tests using tiny generated, non-identifiable samples: grouping by speaker rejects overlap between train and validation, and a negated/ambient phrase counts as a false operation if one fires.
- [ ] Run evaluator tests and confirm expected failures; implement manifest validation and baseline/candidate comparison with sherpa-onnx's existing streaming API.
- [ ] Measure old and changed hotword configurations on held-out real utterances and existing synthetic noise cases. Accept candidate only if held-out command accuracy improves without more false operations or material latency regression; otherwise retain baseline.
- [ ] Commit evaluator code and aggregate report only; run `git status --short` and inspect staged files to prove no user audio, transcript with identity, or local absolute path is included.

### Task 5: Supervised weight-training feasibility gate

**Files:** Extend `docs/validation/fitness-assistant-voice-evaluation.md`; if the gate passes, create `tools/assistant/train-command-adaptation.py`, its configuration and tests, and update `tools/assistant/model-manifest.json` for accepted exported weights.

**Interfaces:** Training consumes only the Task 4 private manifest and a source-verified full-precision checkpoint compatible with the bundled 14M tokenizer/architecture. Export produces sherpa-compatible encoder, decoder, and joiner ONNX; the current int8 inference files are never treated as checkpoints.

- [ ] Record checkpoint source/license/hash, tokenizer compatibility, usable utterance and speaker counts, and split quality. If any prerequisite fails, document “未完成权重微调” and the precise reason; stop this task's training branch.
- [ ] If prerequisites pass, add a failing corpus split/export test, fine-tune with reproducible parameters and augmentation, then export/quantize the candidate into `.model-cache`.
- [ ] Compare candidate with Task 4 baseline on untouched speakers, noisy negatives, initialization time, Android memory/latency, and installed size. Replace packaged weights only when all acceptance conditions in the spec pass.
- [ ] Commit training tooling, model manifest and aggregate measurements; keep raw audio, checkpoints, and temporary exports out of Git.

### Task 6: Release verification

**Files:** Update `docs/validation/fitness-assistant-voice-evaluation.md` and any necessary Android regression tests; produce debug APK in `app/build/outputs/apk/debug/`.

**Interfaces:** Preserve existing application ID and signed-upgrade behavior; no root-directory versioned APK is requested in this task.

- [ ] Run `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`; fix concrete failures.
- [ ] Run `:app:connectedDebugAndroidTest` on an available emulator, including app label, current workout data, mic/prompt overlap, and both voices; if no emulator, state that limit.
- [ ] Check APK bytes and installed-size evidence against 1 GB; check `git diff --check`, clean tracked status, and final aggregate report for unsupported accuracy claims.
- [ ] Request code review, address actionable findings, and commit final verification report.
