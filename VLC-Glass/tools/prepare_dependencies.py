#!/usr/bin/env python3
"""Fetch pinned build inputs. Never runs during playback or first launch."""
import concurrent.futures as cf
import hashlib
import json
import pathlib
import shutil
import sys
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
CACHE = ROOT.parent / 'downloads'
CACHE.mkdir(exist_ok=True)

def fetch(url, name, digest=None):
    path = CACHE / name
    if not path.exists():
        print('Downloading', name, flush=True)
        req = urllib.request.Request(url, headers={'User-Agent': 'VLC-Glass-build/0.3.0'})
        with urllib.request.urlopen(req, timeout=180) as src, open(path.with_suffix('.partial'), 'wb') as dst:
            shutil.copyfileobj(src, dst)
        path.with_suffix('.partial').replace(path)
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest and actual != digest:
        raise RuntimeError('Checksum mismatch: ' + name)
    return path, actual

def safe_extract(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for name in z.namelist():
            if not (destination / name).resolve().is_relative_to(destination.resolve()):
                raise ValueError('Invalid archive path')
        z.extractall(destination)

def main():
    urls = {
      'onnxruntime-android-1.20.0.aar': 'https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.20.0/onnxruntime-android-1.20.0.aar',
      'libvlc-all-3.7.6.aar': 'https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.7.6/libvlc-all-3.7.6.aar',
      'build-tools_r35_linux.zip': 'https://dl.google.com/android/repository/build-tools_r35_linux.zip',
      'platform-35_r02.zip': 'https://dl.google.com/android/repository/platform-35_r02.zip',
      'sentrysearch-ab688137.zip': 'https://github.com/ssrajadh/sentrysearch/archive/ab688137e2a4a9d6f67cac5677be26db16dc406c.zip',
    }
    if '--desktop' in sys.argv:
        urls={k:v for k,v in urls.items() if k.startswith('sentrysearch')}
    with urllib.request.urlopen('https://pypi.org/pypi/nudenet/3.4.2/json', timeout=120) as r:
        meta=json.load(r)
    wheel=next(u for u in meta['urls'] if u['filename'].endswith('.whl'))
    urls[wheel['filename']]=wheel['url']
    results={}
    with cf.ThreadPoolExecutor(max_workers=6) as pool:
        futures={pool.submit(fetch,url,name,wheel['digests']['sha256'] if name==wheel['filename'] else None):name for name,url in urls.items()}
        for f in cf.as_completed(futures):
            name=futures[f]
            try:
                results[name]=f.result()
                print('Verified',name,results[name][1],flush=True)
            except Exception as e:
                print('FAILED',name,str(e),flush=True)
    lock={name:{'url':urls[name],'sha256':sha} for name,(_,sha) in results.items()}
    (ROOT/'vendor/build-inputs.json').write_text(json.dumps(lock,indent=2)+'\n')
    for name in ['onnxruntime-android-1.20.0.aar','libvlc-all-3.7.6.aar']:
        if name not in results:continue
        dest=ROOT/'vendor' / ('onnx-android' if name.startswith('onnx') else 'libvlc-aar')
        safe_extract(results[name][0],dest)
    if wheel['filename'] in results:
        with zipfile.ZipFile(results[wheel['filename']][0]) as z:
            model=next(n for n in z.namelist() if n.endswith('/320n.onnx'))
            dest=ROOT/'assets/models';dest.mkdir(exist_ok=True)
            data=z.read(model);(dest/'320n.onnx').write_bytes(data)
            (dest/'model.json').write_text(json.dumps({'name':'NudeNet 320n','source':wheel['url'],'wheel_sha256':wheel['digests']['sha256'],'sha256':hashlib.sha256(data).hexdigest(),'input_size':320,'license':'AGPL-3.0','sampling_ms':500,'boundary_resolution_ms':100},indent=2)+'\n')
    sdk=ROOT.parent/'toolchain/sdk'
    for name in ['build-tools_r35_linux.zip','platform-35_r02.zip']:
        if name not in results:continue
        temp=ROOT.parent/'toolchain'/name.removesuffix('.zip')
        safe_extract(results[name][0],temp)
        if name.startswith('build-tools'):
            found=next(temp.rglob('aapt2')).parent
            target=sdk/'build-tools/35.0.0'
            if not target.exists():shutil.copytree(found,target)
            for p in target.iterdir():
                if p.is_file() and '.' not in p.name:p.chmod(0o755)
        else:
            found=next(temp.rglob('android.jar')).parent
            target=sdk/'platforms/android-35'
            if not target.exists():shutil.copytree(found,target)
    if 'sentrysearch-ab688137.zip' in results:
        safe_extract(results['sentrysearch-ab688137.zip'][0],ROOT/'upstream/sentrysearch')
    missing=set(urls)-set(results)
    if missing:sys.exit('Missing build inputs: '+', '.join(sorted(missing)))

if __name__=='__main__':main()
