#!/usr/bin/env python3
"""Verify final APKs, actual embedded dependencies, signatures and ELF alignment."""
import hashlib,json,pathlib,re,struct,subprocess,zipfile
P=pathlib.Path(__file__).resolve().parents[1]
BT=P.parent/'toolchain/sdk/build-tools/35.0.0'
EXPECTED={'arm64':{'arm64-v8a'},'armv7':{'armeabi-v7a'},'x86_64':{'x86_64'},'universal':{'arm64-v8a','armeabi-v7a','x86_64'}}
MODEL='c15d8273adad2d0a92f014cc69ab2d6c311a06777a55545f2c4eb46f51911f0f'
def classes(data):
 u=lambda o:struct.unpack_from('<I',data,o)[0]
 strings=[]
 for i in range(u(56)):
  o=u(u(60)+i*4)
  while data[o]&128:o+=1
  o+=1;strings.append(data[o:data.index(0,o)].decode('utf8',errors='replace'))
 types=[strings[u(u(68)+i*4)] for i in range(u(64))]
 return {types[u(u(100)+i*32)] for i in range(u(96))}
def elf_alignment(data):
 assert data[:4]==b'\x7fELF'
 if data[4]!=2:return
 assert data[5]==1
 offset=struct.unpack_from('<Q',data,32)[0];size,count=struct.unpack_from('<HH',data,54)
 for i in range(count):
  at=offset+i*size
  if struct.unpack_from('<I',data,at)[0]==1:
   align=struct.unpack_from('<Q',data,at+48)[0]
   assert align>=16384,('64-bit ELF needs 16 KB load alignment',align)
for suffix,abis in EXPECTED.items():
 apk=P/'dist'/('VLC-Glass-Android-0.3.1-'+suffix+'.apk')
 with zipfile.ZipFile(apk) as z:
  names=set(z.namelist());bundled={n.split('/')[1] for n in names if n.startswith('lib/')}
  assert bundled==abis,(suffix,bundled)
  for f in ['index.html','app.js','intelligence.js','intelligence.css','style.css','glass.js']:
   assert 'assets/ui/'+f in names,f
  assert hashlib.sha256(z.read('assets/models/320n.onnx')).hexdigest()==MODEL
  assert 'GNU AFFERO GENERAL PUBLIC LICENSE' in z.read('assets/notices/NudeNet-AGPL-3.0.txt').decode()
  assert not any('signing' in n.lower() or n.endswith(('.p12','.jks')) for n in names)
  found=set()
  for n in names:
   if re.fullmatch(r'classes[0-9]*\.dex',n):found.update(classes(z.read(n)))
   if n.startswith('lib/') and n.endswith('.so'):elf_alignment(z.read(n))
  for cls in ['Lcom/harsh/vlcglass/SafetyScanner;','Lcom/harsh/vlcglass/PlaybackService;','Lai/onnxruntime/OrtSession;','Lorg/videolan/libvlc/MediaPlayer;']:
   assert cls in found,cls
  for abi in abis:
   for lib in ['libvlc.so','libvlcjni.so','libonnxruntime.so','libonnxruntime4j_jni.so']:
    assert 'lib/'+abi+'/'+lib in names,(abi,lib)
 signature=subprocess.run([str(BT/'apksigner'),'verify','--verbose','--print-certs',str(apk)],text=True,capture_output=True,check=True)
 certificate=re.search(r'certificate SHA-256 digest: (\w+)',signature.stdout).group(1)
 badging=subprocess.run([str(BT/'aapt2'),'dump','badging',str(apk)],text=True,capture_output=True,check=True).stdout
 assert "versionCode='4'" in badging and "versionName='0.3.1'" in badging
 assert "sdkVersion:'28'" in badging and "targetSdkVersion:'35'" in badging
 assert 'android.intent.category.LEANBACK_LAUNCHER' in badging
 print(json.dumps({'file':apk.name,'verified':True,'certificate_sha256':certificate,'abis':sorted(abis),'64_bit_elf_alignment':'16 KB or greater'}))
