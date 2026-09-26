"""Bake short latency-sensitive prompts with the same bundled offline model."""
import argparse
from pathlib import Path
import json
import sherpa_onnx
import numpy as np
p=argparse.ArgumentParser()
p.add_argument('--model',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--only',help='Regenerate one named prompt; leave other PCM files unchanged')
a=p.parse_args(); k=a.model
tts=sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
    kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(model=str(k/'model.int8.onnx'),voices=str(k/'voices.bin'),tokens=str(k/'tokens.txt'),
    data_dir=str(k/'espeak-ng-data'),dict_dir=str(k/'dict'),lexicon=str(k/'lexicon-zh.txt'),lang='zh'),num_threads=2),
    rule_fsts=','.join(str(k/f) for f in ['phone-zh.fst','date-zh.fst','number-zh.fst'])))
prompts={
    'prepare':'休息马上结束，请进入准备状态',
    'encourage':'保持自己的节奏，你做得很好，继续加油',
    'wake':'我在，请说指令',
    'skip':'已跳过休息，下一组已就绪',
    'extend':'已延长三十秒休息',
    'pause':'训练已暂停',
    'complete':'本组已完成，开始组间休息',
    'exercise':'本组已完成，开始动作间休息',
    'finish':'训练已完成，辛苦了',
    'unsupported':'对不起，我没有听清，请再说一次',
}
a.output.mkdir(parents=True,exist_ok=True)
generated=0
for voice,sid in [('male',58),('female',3)]:
    for name,text in prompts.items():
        if a.only and name != a.only:
            continue
        path=a.output/f'{voice}-{name}.pcm'
        spoken=text if text.endswith(('。','！','？')) else text+'。'
        audio=tts.generate(spoken,sid=sid,speed=0.92)
        assert audio.sample_rate==24000
        (np.clip(audio.samples,-1,1)*32767).astype('<i2').tofile(path)
        generated+=1
(a.output/'prompts.json').write_text(json.dumps(prompts,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
print('Cached',generated,'prompts at 24000 Hz mono PCM16')
