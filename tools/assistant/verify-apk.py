"""Verify release assets and size without trusting a successful Gradle build alone."""
import argparse, hashlib, json, zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('apk',type=Path);a=p.parse_args()
project=Path(__file__).resolve().parents[2]
manifest=json.loads((project/'tools/assistant/model-manifest.json').read_text(encoding='utf-8-sig'))
assert a.apk.stat().st_size<1_000_000_000
with zipfile.ZipFile(a.apk) as z:
    for entry in manifest['files']:
        path=entry['path'].replace('app/src/main/assets/','assets/',1)
        data=z.read(path)
        assert len(data)==entry['bytes'],path
        assert hashlib.sha256(data).hexdigest()==entry['sha256'],path
    for abi in ['arm64-v8a','x86_64']:
        assert f'lib/{abi}/libsherpa-onnx-jni.so' in z.namelist()
    assert 'assets/assistant/licenses/espeak-ng-GPL-3.0.txt' in z.namelist()
    assert 'assets/assistant/licenses/NOTICE.txt' in z.namelist()
print(json.dumps({'apkBytes':a.apk.stat().st_size,'sha256':hashlib.sha256(a.apk.read_bytes()).hexdigest(),'verifiedModelFiles':len(manifest['files'])}))
