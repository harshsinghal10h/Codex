#!/usr/bin/env python3
"""Isolate the unmodified LibVLC namespace from a pinned build artifact.

No code from the donor application is executed. D8 re-encodes the selected
class definitions, discarding the donor's unrelated bytecode and resources.
"""
import hashlib, json, pathlib, struct, zipfile, zlib

BASE=pathlib.Path(__file__).resolve().parents[1]
ARTIFACT=BASE.parent/'dependencies/vlc-dependency-artifact.zip'
EXPECTED='6ce7e21f3612357d7b4797305040386e708b8e124d640d195db188348506872d'
assert hashlib.sha256(ARTIFACT.read_bytes()).hexdigest()==EXPECTED, 'Artifact digest mismatch'
OUT=BASE/'vendor'; OUT.mkdir(exist_ok=True)
for stale in OUT.glob('vlc-*.dex'):stale.unlink()

def uleb(b,o):
 n=s=0
 while True:
  x=b[o];o+=1;n|=(x&127)<<s;s+=7
  if x<128:return n,o

def parse(b):
 u=lambda o:struct.unpack_from('<I',b,o)[0]
 strings=[]
 for i in range(u(56)):
  o=u(u(60)+i*4);_,o=uleb(b,o)
  strings.append(bytes(b[o:b.index(0,o)]).decode('utf-8',errors='replace'))
 types=[strings[u(u(68)+i*4)] for i in range(u(64))]
 defs=[(types[u(u(100)+i*32)],u(100)+i*32) for i in range(u(96))]
 return u,strings,types,defs

evidence=[]; apis={}
with zipfile.ZipFile(BASE.parent/'dependencies/dependency.apk') as z:
 for n in z.namelist():
  if n.endswith('.dex'):
   b=bytearray(z.read(n));u,strings,types,defs=parse(b)
   chosen=[(name,off) for name,off in defs if name.startswith('Lorg/videolan/libvlc/')]
   if not chosen:continue
   start=u(100)
   table=b''.join(bytes(b[o:o+32]) for _,o in chosen)
   b[start:start+len(table)]=table
   struct.pack_into('<I',b,96,len(chosen))
   for i in range(u(u(52))):
    o=u(52)+4+i*12
    if struct.unpack_from('<H',b,o)[0]==6:struct.pack_into('<I',b,o+4,len(chosen))
   b[12:32]=hashlib.sha1(b[32:]).digest()
   struct.pack_into('<I',b,8,zlib.adler32(b[12:])&0xffffffff)
   dest=OUT/('vlc-'+n);dest.write_bytes(b)
   evidence.append({'source_dex':n,'selected_classes':[x[0] for x in chosen],'sha256':hashlib.sha256(b).hexdigest()})
   for i in range(u(88)):
    o=u(92)+i*8; cls=types[struct.unpack_from('<H',b,o)[0]]
    if cls.startswith('Lorg/videolan/libvlc/'):
     proto=struct.unpack_from('<H',b,o+2)[0];p=u(76)+proto*12
     ret=types[u(p+4)]; po=u(p+8)
     params=[types[struct.unpack_from('<H',b,po+4+j*2)[0]] for j in range(u(po))] if po else []
     apis.setdefault(cls,[]).append({'name':strings[u(o+4)],'parameters':params,'returns':ret})
 for n in z.namelist():
  if n.startswith('lib/arm64-v8a/') and pathlib.Path(n).name in ['libvlc.so','libvlcjni.so','libc++_shared.so']:
   dest=OUT/n;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(n))
   evidence.append({'native':n,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()})
(OUT/'api-inventory.json').write_text(json.dumps(apis,indent=2))
(OUT/'provenance.json').write_text(json.dumps({'libvlc':'3.7.6','artifact_sha256':EXPECTED,'artifact_url':'https://github.com/AndreyPavlenko/Fermata/actions/runs/35517061248','upstream':'https://code.videolan.org/videolan/libvlcjni','upstream_version_evidence':'https://github.com/AndreyPavlenko/Fermata/blob/7d2bd42e0fea98a796ba069b56c48ef07d874549/gradle/libs.versions.toml','evidence':evidence},indent=2))
print('Selected',sum(len(x.get('selected_classes',[])) for x in evidence),'LibVLC classes; no donor application classes retained.')
