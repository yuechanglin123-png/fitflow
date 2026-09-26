"""Compare real streaming ASR on reproducible SAPI speech and artificial noise.

These samples are a regression probe, not measured real-world gym accuracy.
"""
import argparse
import json
import re
import time
from pathlib import Path
import numpy as np
import soundfile as sf
import sherpa_onnx

HOTWORDS = '铁蛋/跳过休息/延长三十秒休息时间/暂停训练/完成本组/现在练什么/今天天气/北京/上海'
EXPANDED_HOTWORDS = HOTWORDS + '/结束休息/延长休息三十秒/再休息三十秒/休息加三十秒/暂停一下训练/这一组做完了/现在几点/今天星期几/还剩几组/还要休息多久'

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--old', type=Path, required=True)
    p.add_argument('--candidate', type=Path, required=True)
    p.add_argument('--samples', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--modes', default='14m,14m-tuned,2025-greedy')
    p.add_argument('--fixtures',type=Path,help='Export selected independent PCM samples for Android regression tests')
    a = p.parse_args()
    entries = json.loads((a.samples/'samples.json').read_text(encoding='utf-8-sig'))
    def load(file):
        x,sr = sf.read(a.samples/file,dtype='float32')
        if sr != 16000:
            x = np.interp(np.arange(round(len(x)*16000/sr))*sr/16000,np.arange(len(x)),x).astype(np.float32)
        return x
    babble = np.concatenate([load(e['file']) for e in entries if e['negative'] and not e['text'].startswith('铁蛋')])
    rng = np.random.default_rng(20260924)
    cases = []
    for entry in entries:
        speech = load(entry['file'])
        for kind in ['clean','noise10db','speech8db']:
            x = speech.copy()
            if kind != 'clean':
                n = rng.normal(size=len(x)) if kind == 'noise10db' else np.resize(np.roll(babble,int(rng.integers(len(babble)))),len(x))
                snr = 10 if kind == 'noise10db' else 8
                n = n*np.sqrt(np.mean(x*x)/(np.mean(n*n)*10**(snr/10)))
                x = np.clip(x+n,-1,1).astype(np.float32)
            cases.append((entry,kind,x))
    if a.fixtures:
        a.fixtures.mkdir(parents=True,exist_ok=True)
        selected = {('Kangkang-15.wav','noise10db'):'rest-extra-noisy-16k.pcm',
                    ('Huihui-16.wav','clean'):'rest-extra-clean-16k.pcm'}
        for entry,kind,x in cases:
            name = selected.get((entry['file'],kind))
            if name: (np.clip(x,-1,1)*32767).astype('<i2').tofile(a.fixtures/name)
    reports = []
    for label,path,names in [
        ('14m',a.old,('encoder-epoch-99-avg-1.int8.onnx','decoder-epoch-99-avg-1.onnx','joiner-epoch-99-avg-1.int8.onnx')),
        ('14m-tuned',a.old,('encoder-epoch-99-avg-1.int8.onnx','decoder-epoch-99-avg-1.onnx','joiner-epoch-99-avg-1.int8.onnx')),
        ('2025-greedy',a.candidate,('encoder.int8.onnx','decoder.onnx','joiner.int8.onnx')),
    ]:
        if label not in a.modes.split(','): continue
        greedy = label == '2025-greedy'
        tuned = label == '14m-tuned'
        start = time.perf_counter()
        asr = sherpa_onnx.OnlineRecognizer.from_transducer(
            tokens=str(path/'tokens.txt'),encoder=str(path/names[0]),decoder=str(path/names[1]),joiner=str(path/names[2]),
            num_threads=2,sample_rate=16000,feature_dim=80,decoding_method='greedy_search' if greedy else 'modified_beam_search',
            modeling_unit='cjkchar',hotwords_score=2.0 if tuned else 1.5,enable_endpoint_detection=True,rule2_min_trailing_silence=0.65)
        init = time.perf_counter()-start
        rows = []
        for entry,kind,x in cases:
            start = time.perf_counter()
            stream = asr.create_stream() if greedy else asr.create_stream(hotwords=EXPANDED_HOTWORDS if tuned else HOTWORDS)
            parts = []
            signal = np.concatenate([x,np.zeros(16000,dtype=np.float32)])
            for i in range(0,len(signal),1600):
                stream.accept_waveform(16000,signal[i:i+1600])
                while asr.is_ready(stream): asr.decode_stream(stream)
                if asr.is_endpoint(stream):
                    parts.append(asr.get_result(stream)); asr.reset(stream)
            stream.input_finished()
            while asr.is_ready(stream): asr.decode_stream(stream)
            parts.append(asr.get_result(stream))
            text = ''.join(parts).replace(' ','')
            expected = re.sub(r'[\s，。！？、]','',entry['text'])
            rows.append(dict(**entry,condition=kind,recognized=text,segments=[s for s in parts if s],
                exact=text==expected,seconds=time.perf_counter()-start,audioSeconds=len(x)/16000))
        report = dict(model=label,initializationSeconds=init,samples=rows)
        reports.append(report)
        a.output.write_text(json.dumps(reports,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(dict(model=label,initializationSeconds=init,conditions={k:dict(
            exact=sum(r['exact'] for r in rows if r['condition']==k),total=sum(r['condition']==k for r in rows),
            realtimeFactor=sum(r['seconds'] for r in rows if r['condition']==k)/sum(r['audioSeconds'] for r in rows if r['condition']==k))
            for k in ['clean','noise10db','speech8db']})),flush=True)
        del asr

if __name__ == '__main__': main()
