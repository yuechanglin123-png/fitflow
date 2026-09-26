"""Generate clearly labelled synthetic Chinese samples; evaluate real offline ASR.

Usage: python evaluate-models.py --work <validation-directory> [--recognize]
Synthetic round trips do not establish real gym recognition performance.
"""
import argparse
import json
import time
from pathlib import Path

def main():
    import numpy as np
    import soundfile as sf
    import sherpa_onnx
    p = argparse.ArgumentParser()
    p.add_argument('--work', type=Path, required=True)
    p.add_argument('--recognize', action='store_true')
    p.add_argument('--sapi-samples', action='store_true', help='Use independently generated Windows SAPI samples')
    p.add_argument('--zipformer', action='store_true')
    p.add_argument('--standard-tts', action='store_true')
    p.add_argument('--kokoro', action='store_true')
    p.add_argument('--hotwords', action='store_true')
    args = p.parse_args()
    root = args.work
    model = root / ('vits-zh-aishell3' if args.standard_tts else 'vits-icefall-zh-aishell3')
    wavdir = root / ('sapi-samples' if args.sapi_samples else 'kokoro-samples' if args.kokoro else 'standard-samples' if args.standard_tts else 'samples')
    wavdir.mkdir(exist_ok=True)
    start = time.perf_counter()
    tts = None if args.sapi_samples else sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=str(model/('vits-aishell3.int8.onnx' if args.standard_tts else 'model.onnx')), lexicon=str(model/'lexicon.txt'),
                tokens=str(model/'tokens.txt')),
            num_threads=2),
        rule_fsts=','.join(str(model/f) for f in ['phone.fst','date.fst','number.fst'])
    )) if not args.kokoro else None
    if args.kokoro and not args.sapi_samples:
        k = root/'kokoro-int8-multi-lang-v1_1'
        tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
                model=str(k/'model.int8.onnx'), voices=str(k/'voices.bin'), tokens=str(k/'tokens.txt'),
                data_dir=str(k/'espeak-ng-data'), dict_dir=str(k/'dict'),
                lexicon=str(k/'lexicon-zh.txt'), lang='zh'), num_threads=2),
            rule_fsts=','.join(str(k/f) for f in ['phone-zh.fst','date-zh.fst','number-zh.fst'])))
    init = time.perf_counter()-start
    phrases = [
        '跳过休息', '结束休息', '延长三十秒休息时间', '延长休息三十秒',
        '加三十秒', '暂停训练', '暂停锻炼', '完成本组', '这组完成了', '完成这一组',
        '现在几点', '现在几点了', '今天几号', '今天星期几', '现在练什么',
        '还剩几组', '休息还剩多久', '今天天气怎么样', '北京今天天气', '上海今天天气怎么样',
    ]
    negatives = ['不要完成本组', '别暂停训练', '完成本组然后暂停训练', '今天天气真好',
                 '我们聊会天', '旁边有人训练', '这首音乐很好听', '我正在喝水', '请给我一杯咖啡', '现在不要跳过休息']
    results = []
    voices = [(58,'male-zm_009'),(3,'female-zf_001')] if args.kokoro else [(10,'male-SSB0073'),(99,'female-SSB0851')]
    for sid, label in voices:
        for n, phrase in enumerate(phrases+negatives):
            wav = wavdir/f'{sid}-{n:02d}.wav'
            text = '小练小练，'+phrase if n < len(phrases) else phrase
            started = time.perf_counter()
            if not wav.exists() or wav.stat().st_size < 100:
                if args.sapi_samples:
                    raise ValueError(f'Missing independent sample: {wav}')
                audio = tts.generate(text, sid=sid, speed=1.0)
                sf.write(str(wav), audio.samples, audio.sample_rate, subtype='PCM_16')
            results.append(dict(file=wav.name, text=text, phrase=phrase, voice=label,
                                source='synthetic-windows-sapi' if args.sapi_samples else 'synthetic-kokoro' if args.kokoro else 'synthetic-aishell3', commandSample=n<len(phrases),
                                synthesisSeconds=time.perf_counter()-started))
    report = dict(ttsInitializationSeconds=init, samples=results,
                  limitation='Synthetic samples only; real people, noise and phones require separate validation.')
    if args.recognize:
        import vosk
        vosk.SetLogLevel(-1)
        started = time.perf_counter()
        if args.zipformer:
            z = root/'sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23-mobile'
            asr = sherpa_onnx.OnlineRecognizer.from_transducer(
                tokens=str(z/'tokens.txt'), encoder=str(z/'encoder-epoch-99-avg-1.int8.onnx'),
                decoder=str(z/'decoder-epoch-99-avg-1.onnx'), joiner=str(z/'joiner-epoch-99-avg-1.int8.onnx'),
                num_threads=2, sample_rate=16000, feature_dim=80,
                decoding_method='modified_beam_search' if args.hotwords else 'greedy_search',
                modeling_unit='cjkchar', hotwords_score=1.5, enable_endpoint_detection=True)
        else:
            asr = vosk.Model(str(root/'vosk-model-small-cn-0.22'))
        report['asrInitializationSeconds'] = time.perf_counter()-started
        for entry in results:
            samples, sr = sf.read(str(wavdir/entry['file']), dtype='float32')
            # TTS is 8kHz; linear resampling to the recognizer's expected 16kHz.
            if sr != 16000:
                samples = np.interp(np.arange(round(len(samples)*16000/sr))*sr/16000,
                                    np.arange(len(samples)), samples).astype(np.float32)
            samples = np.concatenate([samples, np.zeros(16000, dtype=np.float32)])
            if args.zipformer:
                started = time.perf_counter()
                stream = asr.create_stream(hotwords='小练小练/跳过休息/延长三十秒休息时间/暂停训练/完成本组/现在练什么/今天天气/北京/上海' if args.hotwords else '')
                stream.accept_waveform(16000, samples)
                stream.input_finished()
                while asr.is_ready(stream):
                    asr.decode_stream(stream)
                entry['recognized'] = asr.get_result(stream).replace(' ','')
                entry['recognitionSeconds'] = time.perf_counter()-started
                continue
            rec = vosk.KaldiRecognizer(asr, 16000)
            pcm = (np.clip(samples,-1,1)*32767).astype('<i2').tobytes()
            started = time.perf_counter()
            segments = []
            for offset in range(0,len(pcm),8000):
                if rec.AcceptWaveform(pcm[offset:offset+8000]):
                    segments.append(json.loads(rec.Result()).get('text',''))
            segments.append(json.loads(rec.FinalResult()).get('text',''))
            entry['recognized'] = ''.join(segments).replace(' ','')
            entry['recognitionSeconds'] = time.perf_counter()-started
    output = root / ('evaluation' + ('-kokoro' if args.kokoro else '') + ('-standard' if args.standard_tts else '') + ('-sapi' if args.sapi_samples else '') + ('-zipformer' if args.zipformer else '') + ('-hotwords' if args.hotwords else '') + '.json')
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2), encoding='utf-8')
    print(json.dumps({'samples':len(results),'ttsInitializationSeconds':init,'report':str(output)}))

if __name__ == '__main__':
    main()
