#!/usr/bin/env python3
"""Build architecture-specific APKs from pinned official AARs and bundled model."""
import argparse,hashlib,json,os,pathlib,shutil,subprocess,zipfile
P=pathlib.Path(__file__).resolve().parents[1]
a=argparse.ArgumentParser();a.add_argument('--sdk',default=os.environ.get('ANDROID_SDK_ROOT') or str(P.parent/'toolchain/sdk'));a.add_argument('--keystore');a.add_argument('--abis',default='arm64-v8a,armeabi-v7a,x86_64');a.add_argument('--universal',action='store_true');args=a.parse_args()
SDK=pathlib.Path(args.sdk);BT=next((x for x in sorted((SDK/'build-tools').iterdir(),reverse=True) if (x/'aapt2').exists()),None);AJ=SDK/'platforms/android-35/android.jar'
if not BT or not AJ.exists():raise SystemExit('Install Android platform 35 and build tools, or run prepare_dependencies.py.')
B=P/'build';B.mkdir(exist_ok=True)
for name in ['classes','dex','packed-assets']:
 if (B/name).exists():shutil.rmtree(B/name)
 if name!='packed-assets':(B/name).mkdir()
shutil.copytree(P/'assets',B/'packed-assets',ignore=shutil.ignore_patterns('__qa*'))
def run(cmd):subprocess.run(list(map(str,cmd)),check=True,cwd=P)
run(['node',P/'tools/build_ui.mjs',B/'packed-assets/ui'])
def quote_digest(f):return hashlib.sha256(f.read_bytes()).hexdigest()
ort=P/'vendor/onnx-android';vlc=P/'vendor/libvlc-aar'
if not (ort/'classes.jar').exists() or not (vlc/'classes.jar').exists():raise SystemExit('Missing pinned AARs. Run tools/prepare_dependencies.py first.')
model=P/'assets/models/320n.onnx';meta=json.loads((model.parent/'model.json').read_text())
if quote_digest(model)!=meta['sha256']:raise SystemExit('Detector checksum mismatch')
run([BT/'aapt2','compile','--dir',P/'android/res','-o',B/'resources.zip'])
run([BT/'aapt2','link','-o',B/'base.apk','--manifest',P/'android/AndroidManifest.xml','-I',AJ,'-A',B/'packed-assets',B/'resources.zip'])
sources=sorted((P/'android/src').rglob('*.java'))
run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-source','8','-target','8','-cp',os.pathsep.join(map(str,[AJ,ort/'classes.jar'])),'-d',B/'classes',*sources])
jar=B/'app.jar'
with zipfile.ZipFile(jar,'w') as z:
 for f in (B/'classes').rglob('*.class'):z.write(f,f.relative_to(B/'classes'))
run(['java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',AJ,'--output',B/'dex',jar,ort/'classes.jar',vlc/'classes.jar'])
key=pathlib.Path(args.keystore) if args.keystore else P/'private/signing.p12'
if not key.exists():
 key.parent.mkdir(parents=True,exist_ok=True)
 run(['keytool','-genkeypair','-keystore',key,'-storetype','PKCS12','-storepass','android','-keypass','android','-alias','vlcglass','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=VLC Glass Development, O=Personal build'])
 os.chmod(key,0o600)
abis=args.abis.split(',');builds=[(abi,[abi]) for abi in abis]
if args.universal:builds.append(('universal',abis))
out=P/'dist';out.mkdir(exist_ok=True);evidence=[]
for label,selected in builds:
 unsigned=B/('unsigned-'+label+'.apk');shutil.copy(B/'base.apk',unsigned)
 with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED,compresslevel=7) as z:
  for f in (B/'dex').glob('*.dex'):z.write(f,f.name)
  for abi in selected:
   for root in [vlc,ort]:
    lib=root/'jni'/abi
    # ORT does not ship 32-bit x86 in newer packages; never advertise a missing ABI.
    if not lib.exists():raise SystemExit('Missing runtime ABI: '+str(lib))
    for f in lib.glob('*.so'):z.write(f,'lib/'+abi+'/'+f.name)
 aligned=B/('aligned-'+label+'.apk');run(['python3',P/'tools/align_apk.py',unsigned,aligned])
 suffix={'arm64-v8a':'arm64','armeabi-v7a':'armv7'}.get(label,label)
 apk=out/('VLC-Glass-Android-0.3.3-'+suffix+'.apk')
 run([BT/'apksigner','sign','--ks',key,'--ks-key-alias','vlcglass','--ks-pass','pass:android','--key-pass','pass:android','--out',apk,aligned])
 run([BT/'apksigner','verify','--verbose',apk])
 evidence.append({'file':apk.name,'sha256':quote_digest(apk),'bytes':apk.stat().st_size,'abis':selected})
 print('BUILT',apk.name,apk.stat().st_size,flush=True)
(P/'build-evidence-0.3.3.json').write_text(json.dumps({'version':'0.3.3','android':evidence,'device_tested':False,'classifier_accuracy_evaluated':False},indent=2)+'\n')
