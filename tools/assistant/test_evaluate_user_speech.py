import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("evaluate-user-speech.py")
spec = importlib.util.spec_from_file_location("evaluate_user_speech", SCRIPT)
evaluation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evaluation)


class UserSpeechEvaluationTest(unittest.TestCase):
    def test_same_speaker_cannot_enter_train_and_validation(self):
        rows = [
            dict(audio="a.wav", speaker="s1", environment="quiet", transcript="铁蛋", expectedIntent="WAKE", split="train", awake=False, startSecond=0, endSecond=1),
            dict(audio="b.wav", speaker="s1", environment="quiet", transcript="铁蛋", expectedIntent="WAKE", split="validation", awake=False, startSecond=0, endSecond=1),
        ]
        with self.assertRaisesRegex(ValueError, "speaker"):
            evaluation.validate_manifest(rows)

    def test_negated_or_ambient_utterance_does_not_count_as_command(self):
        self.assertEqual("NO_ACTION", evaluation.classify("铁蛋别暂停训练", awake=False))
        self.assertEqual("NO_ACTION", evaluation.classify("暂停训练", awake=False))
        self.assertEqual("PAUSE", evaluation.classify("暂停训练", awake=True))
        self.assertEqual("PAUSE", evaluation.classify("铁蛋暂停训练", awake=False))

    def test_false_operation_is_counted(self):
        rows = [{"expectedIntent": "NO_ACTION", "recognized": "暂停训练", "split": "validation", "awake": True}]
        summary = evaluation.summarize(rows)
        self.assertEqual(1, summary["validation"]["falseOperations"])

    def test_command_aliases_match_android_intent_parser(self):
        self.assertEqual("PAUSE", evaluation.classify("暂停一下训练", awake=True))
        self.assertEqual("EXTEND_REST_30", evaluation.classify("休息加三十秒", awake=True))
        self.assertEqual("COMPLETE_SET", evaluation.classify("这一组做完了", awake=True))


if __name__ == "__main__":
    unittest.main()
