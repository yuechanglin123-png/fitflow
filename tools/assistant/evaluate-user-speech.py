"""Evaluate private, speaker-split workout speech against the bundled ASR.

The manifest and row-level output contain user audio references and must stay
outside the Git worktree. Only aggregate results may be copied into docs.
"""

import argparse
import json
import re
import time
import unicodedata
import wave
from collections import defaultdict
from pathlib import Path


COMMANDS = {
    "跳过休息": "SKIP_REST", "结束休息": "SKIP_REST",
    "完成本组": "COMPLETE_SET", "本组完成": "COMPLETE_SET", "这组完成了": "COMPLETE_SET",
    "延长三十秒": "EXTEND_REST_30", "延长30秒": "EXTEND_REST_30",
    "延长三十秒休息时间": "EXTEND_REST_30", "延长30秒休息时间": "EXTEND_REST_30",
    "暂停训练": "PAUSE", "暂停锻炼": "PAUSE",
}
EXPECTED = set(COMMANDS.values()) | {"WAKE", "NO_ACTION"}
COMMON_HOTWORDS = ("跳过休息/延长三十秒休息时间/暂停训练/完成本组/现在练什么/今天天气/北京/上海"
                   "/结束休息/延长休息三十秒/再休息三十秒/休息加三十秒/暂停一下训练"
                   "/这一组做完了/现在几点/今天星期几/还剩几组/还要休息多久")
CONFIGURATIONS = {
    "baseline": ("小练小练/" + COMMON_HOTWORDS, 2.0),
    "candidate": ("铁蛋/" + COMMON_HOTWORDS, 2.0),
    "candidate-high": ("铁蛋/" + COMMON_HOTWORDS, 2.5),
    "larger-2025": ("", 1.5),
}


def normalize(text: str) -> str:
    return "".join(char for char in text if not char.isspace() and not unicodedata.category(char).startswith("P"))


def classify(text: str, awake: bool = False) -> str:
    clean = normalize(text)
    if clean.startswith("铁蛋"):
        clean = clean.removeprefix("铁蛋")
        if not clean:
            return "WAKE"
    elif not awake:
        return "NO_ACTION"
    clean = re.sub(r"^(请问|请|麻烦|帮我)", "", clean)
    clean = re.sub(r"(吧|一下)$", "", clean)
    return COMMANDS.get(clean, "NO_ACTION")


def validate_manifest(rows: list[dict]) -> None:
    if not rows:
        raise ValueError("empty manifest")
    speakers: dict[str, set[str]] = defaultdict(set)
    for row in rows:
        for key in ("audio", "speaker", "environment", "transcript", "expectedIntent", "split", "startSecond", "endSecond"):
            if key not in row:
                raise ValueError(f"missing {key}")
        if row["split"] not in {"train", "validation"} or row["expectedIntent"] not in EXPECTED:
            raise ValueError("invalid split or expectedIntent")
        if not 0 <= row["startSecond"] < row["endSecond"]:
            raise ValueError("invalid audio interval")
        speakers[row["speaker"]].add(row["split"])
    if any(len(splits) > 1 for splits in speakers.values()):
        raise ValueError("speaker appears in both train and validation")


def summarize(rows: list[dict]) -> dict:
    result: dict[str, dict] = {}
    for split in ("train", "validation"):
        selected = [row for row in rows if row["split"] == split]
        result[split] = {
            "total": len(selected),
            "correct": sum(row["expectedIntent"] == classify(row["recognized"], row["expectedIntent"] not in {"WAKE", "NO_ACTION"}) for row in selected),
            "falseOperations": sum(row["expectedIntent"] == "NO_ACTION" and classify(row["recognized"]) in set(COMMANDS.values()) for row in selected),
            "recognitionSeconds": round(sum(row.get("recognitionSeconds", 0) for row in selected), 3),
        }
    return result


def recognize(rows: list[dict], repo: Path, configuration: str) -> list[dict]:
    import numpy as np
    import sherpa_onnx

    larger = configuration == "larger-2025"
    model = (repo / ".model-cache/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30"
             if larger else repo / "app/src/main/assets/assistant/asr")
    hotwords, score = CONFIGURATIONS[configuration]
    engine = sherpa_onnx.OnlineRecognizer.from_transducer(
        tokens=str(model / "tokens.txt"),
        encoder=str(model / ("encoder.int8.onnx" if larger else "encoder-epoch-99-avg-1.int8.onnx")),
        decoder=str(model / ("decoder.onnx" if larger else "decoder-epoch-99-avg-1.onnx")),
        joiner=str(model / ("joiner.int8.onnx" if larger else "joiner-epoch-99-avg-1.int8.onnx")),
        num_threads=2, sample_rate=16000, feature_dim=80,
        decoding_method="greedy_search" if larger else "modified_beam_search", modeling_unit="cjkchar",
        hotwords_score=score, enable_endpoint_detection=True,
        rule2_min_trailing_silence=0.65,
    )
    output = []
    for row in rows:
        with wave.open(row["audio"], "rb") as source:
            if source.getframerate() != 16000 or source.getnchannels() != 1 or source.getsampwidth() != 2:
                raise ValueError("audio must be 16 kHz mono PCM16 WAV")
            begin = max(0, round(row["startSecond"] * 16000))
            end = min(source.getnframes(), round(row["endSecond"] * 16000))
            source.setpos(begin)
            samples = np.frombuffer(source.readframes(end - begin), dtype="<i2").astype("float32") / 32768
        stream = engine.create_stream() if larger else engine.create_stream(hotwords=hotwords)
        start = time.perf_counter()
        segments = []
        signal = np.concatenate((samples, np.zeros(16000, dtype="float32")))
        for offset in range(0, len(signal), 1600):
            stream.accept_waveform(16000, signal[offset : offset + 1600])
            while engine.is_ready(stream):
                engine.decode_stream(stream)
            if engine.is_endpoint(stream):
                segments.append(engine.get_result(stream))
                engine.reset(stream)
        stream.input_finished()
        while engine.is_ready(stream):
            engine.decode_stream(stream)
        segments.append(engine.get_result(stream))
        output.append({**row, "recognized": "".join(segments).replace(" ", ""), "recognitionSeconds": round(time.perf_counter() - start, 3)})
    return output


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--configuration", choices=CONFIGURATIONS, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = json.loads(args.manifest.read_text(encoding="utf-8"))
    validate_manifest(rows)
    result = recognize(rows, Path(__file__).resolve().parents[2], args.configuration)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"configuration": args.configuration, "aggregate": summarize(result)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
