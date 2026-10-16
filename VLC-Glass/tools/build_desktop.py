#!/usr/bin/env python3
"""Package the real LibVLC desktop host with its local detector."""
import hashlib,json,os,pathlib,shutil,subprocess,sys,urllib.request,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
vlc=ROOT/'vendor/vlc';vlc.mkdir(parents=True,exist_ok=True)
if sys.platform=='win32':
    name='vlc-3.0.21-win64.zip';url='https://download.videolan.org/pub/videolan/vlc/3.0.21/win64/'+name
    archive=ROOT/'build'/name;archive.parent.mkdir(exist_ok=True)
    if not archive.exists():
        with urllib.request.urlopen(url,timeout=180) as src,open(archive,'wb') as dst:shutil.copyfileobj(src,dst)
    with urllib.request.urlopen(url+'.sha256',timeout=60) as r:expected=r.read().decode().split()[0]
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected:raise SystemExit('LibVLC Windows checksum mismatch')
    with zipfile.ZipFile(archive) as z:
        for info in z.infolist():
            relative=pathlib.PurePosixPath(info.filename).parts[1:]
            if not relative or info.is_dir():continue
            if relative[0] not in ('libvlc.dll','libvlccore.dll','plugins','COPYING.txt','AUTHORS.txt','THANKS.txt'):continue
            target=vlc.joinpath(*relative)
            if not target.resolve().is_relative_to(vlc.resolve()):raise ValueError('Unsafe VLC archive')
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(info))
elif sys.platform=='darwin':
    source=pathlib.Path('/Applications/VLC.app/Contents/MacOS')
    if not source.exists():raise SystemExit('Install the official VLC application first.')
    for name in ['lib','plugins','share']:
        if (source/name).exists():
            if (vlc/name).exists():shutil.rmtree(vlc/name)
            shutil.copytree(source/name,vlc/name,symlinks=True)
else:
    source=pathlib.Path('/usr/lib/x86_64-linux-gnu')
    for name in ['libvlc.so.5','libvlccore.so.9']:
        shutil.copy2((source/name).resolve(),vlc/name)
    shutil.copytree(source/'vlc/plugins',vlc/'plugins',dirs_exist_ok=True)

if '--prepare-only' in sys.argv:raise SystemExit(0)

cmd=[sys.executable,'-m','PyInstaller','--noconfirm','--clean','--name','VLC-Glass-0.3.2',
 '--paths',str(ROOT),'--paths',str(next((ROOT/'upstream/sentrysearch').glob('sentrysearch-*'))),
 '--add-data',str(ROOT/'assets')+os.pathsep+'assets',
 '--add-data',str(ROOT/'upstream')+os.pathsep+'upstream',
 '--add-binary',str(vlc)+os.pathsep+'vendor/vlc',
 '--collect-all','onnxruntime','--collect-all','chromadb','--collect-all','imageio_ffmpeg',
 '--collect-submodules','sentrysearch','--collect-submodules','google.genai',
 '--hidden-import','keyring.backends.Windows','--hidden-import','keyring.backends.macOS',
 '--hidden-import','keyring.backends.SecretService']
cmd+=['--onefile','--windowed'] if sys.platform=='win32' else ['--onedir','--windowed']
cmd.append(str(ROOT/'desktop/main.py'))
subprocess.run(cmd,check=True,cwd=ROOT)
