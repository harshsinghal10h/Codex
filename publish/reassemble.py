#!/usr/bin/env python3
"""Reassemble the exact signed distribution after validating every part."""
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / 'publish/manifest.json').read_text())
EXPECTED = {
    'VLC-Glass-Android-0.2.0-arm64.apk':
        '24353a0bae718b8a373b7a281767c469f9bd5cea8d1b705d8e27f0b2237c0623',
    'VLC-Glass-Source-0.2.0.zip':
        '8fe304ee601010bbf5387ee1766e92ddd49d53b0c195b208566b3bcea0781cf4',
}
assert MANIFEST['version'] == '0.2.0'
assert len(MANIFEST['files']) == len(EXPECTED)
assert {f['name'] for f in MANIFEST['files']} == set(EXPECTED)
OUT = ROOT / 'dist'
OUT.mkdir(exist_ok=True)
checksums = []
for item in MANIFEST['files']:
    assert item['sha256'] == EXPECTED[item['name']]
    temp = OUT / (item['name'] + '.partial')
    digest, total, seen = hashlib.sha256(), 0, set()
    with temp.open('wb') as stream:
        for part in item['parts']:
            path = part['path']
            assert re.fullmatch(r'publish/parts/(android|source)\.part[0-9]{3}', path)
            assert path not in seen
            seen.add(path)
            data = (ROOT / path).read_bytes()
            assert len(data) == part['bytes'], path
            assert hashlib.sha256(data).hexdigest() == part['sha256'], path
            stream.write(data)
            digest.update(data)
            total += len(data)
    assert total == item['bytes'], item['name']
    assert digest.hexdigest() == item['sha256'], item['name']
    temp.replace(OUT / item['name'])
    checksums.append(f"{item['sha256']}  {item['name']}")
    print(f"Verified {item['name']}: {total} bytes")
(OUT / 'SHA256SUMS.txt').write_text('\n'.join(checksums) + '\n')
