#!/usr/bin/env python3
"""Reproducible APK assembly using Android SDK build tools, JDK 17 and Python."""
import argparse, pathlib, subprocess, shutil, zipfile, os, hashlib
P=pathlib.Path(__file__).resolve().parents[1]
a=argparse.ArgumentParser();a.add_argument('--sdk',default=os.environ.get('ANDROID_SDK_ROOT') or str(P.parent/'toolchain/sdk'));a.add_argument('--keystore');args=a.parse_args()
SDK=pathlib.Path(args.sdk);BT=next((x for x in sorted((SDK/'build-tools').iterdir(),reverse=True) if (x/'aapt2').exists()),None);AJ=SDK/'platforms/android-35/android.jar'
if not BT or not AJ.exists():raise SystemExit('Android SDK platform 35 and build tools are required. Pass --sdk PATH.')
B=P/'build';B.mkdir(exist_ok=True)
for name in ['classes','dex']:
 if (B/name).exists():shutil.rmtree(B/name)
 (B/name).mkdir()
if (B/'packed-assets').exists():shutil.rmtree(B/'packed-assets')
shutil.copytree(P/'assets',B/'packed-assets',ignore=shutil.ignore_patterns('__qa*'))
def run(cmd):subprocess.run(list(map(str,cmd)),check=True,cwd=P)
run([BT/'aapt2','compile','--dir',P/'android/res','-o',B/'resources.zip'])
run([BT/'aapt2','link','-o',B/'base.apk','--manifest',P/'android/AndroidManifest.xml','-I',AJ,'-A',B/'packed-assets',B/'resources.zip'])
sources=sorted((P/'android/src').rglob('*.java'))
run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-source','8','-target','8','-cp',AJ,'-d',B/'classes',*sources])
jar=B/'app.jar'
with zipfile.ZipFile(jar,'w') as z:
 for f in (B/'classes').rglob('*.class'):z.write(f,f.relative_to(B/'classes'))
vendor=P/'vendor/libvlc.dex'
if not vendor.exists():
 raw=list((P/'vendor').glob('vlc-*.dex'))
 if not raw:raise SystemExit('Missing vendor/libvlc.dex: prepare the pinned LibVLC dependency first.')
 (B/'vendor-dex').mkdir(exist_ok=True)
 run(['java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--no-desugaring','--output',B/'vendor-dex',*raw])
 shutil.copy(B/'vendor-dex/classes.dex',vendor)
run(['java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',AJ,'--output',B/'dex',jar,vendor])
unsigned=B/'unsigned.apk';shutil.copy(B/'base.apk',unsigned)
with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for f in (B/'dex').glob('*.dex'):z.write(f,f.name)
 for f in (P/'vendor/lib').rglob('*.so'):z.write(f,f.relative_to(P/'vendor'))
run(['python3',P/'tools/align_apk.py',unsigned,B/'aligned.apk'])
key=pathlib.Path(args.keystore) if args.keystore else P/'private/signing.p12'
if not key.exists():
 key.parent.mkdir(parents=True,exist_ok=True)
 run(['keytool','-genkeypair','-keystore',key,'-storetype','PKCS12','-storepass','android','-keypass','android','-alias','vlcglass','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=VLC Glass Development, O=Personal build'])
 os.chmod(key,0o600)
out=P/'dist';out.mkdir(exist_ok=True);apk=out/'VLC-Glass-Android-0.2.0-arm64.apk'
run([BT/'apksigner','sign','--ks',key,'--ks-key-alias','vlcglass','--ks-pass','pass:android','--key-pass','pass:android','--out',apk,B/'aligned.apk'])
run([BT/'apksigner','verify','--verbose',apk])
print('APK:',apk,'bytes:',apk.stat().st_size,'sha256:',hashlib.sha256(apk.read_bytes()).hexdigest())
