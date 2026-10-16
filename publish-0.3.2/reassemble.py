"""Recreate locally signed release APKs and verify exact bytes before publishing."""
from pathlib import Path
import hashlib,json,os,subprocess,zipfile
P=Path(__file__).resolve().parent
manifest=json.loads((P/'manifest.json').read_text())
out=P/'release';out.mkdir(exist_ok=True)
sdk=Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','/usr/local/lib/android/sdk')))
signer=next(p/'apksigner' for p in sorted((sdk/'build-tools').iterdir(),reverse=True) if (p/'apksigner').exists())
cert='83ddb46de53ad31d665044aa022cd72d71a2c8feb23a495e14fd3ab178fa92a4'
checks=[];evidence=[]
for f in manifest['files']:
 target=out/f['name']
 if target.name!=f['name'] or target.suffix!='.apk':raise ValueError('Invalid release filename')
 with target.open('wb') as dst:
  for chunk in f['chunks']:
   sha=chunk if isinstance(chunk,str) else chunk['sha']
   if len(sha)!=40 or any(c not in '0123456789abcdef' for c in sha):raise ValueError('Invalid blob identifier')
   blob=P/'blobs'/(sha+'.bin')
   if not blob.exists():blob=P.parent/'publish-0.3.1/blobs'/(sha+'.bin')
   b=blob.read_bytes()
   if hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()!=sha:raise ValueError('Corrupt Git blob')
   if not isinstance(chunk,str):
    offset=chunk['offset'];length=chunk['bytes']
    if type(offset)!=int or type(length)!=int or offset<0 or length<=0 or offset+length>len(b):raise ValueError('Invalid blob slice')
    b=b[offset:offset+length]
   dst.write(b)
 digest=hashlib.sha256(target.read_bytes()).hexdigest()
 if target.stat().st_size!=f['bytes'] or digest!=f['sha256']:raise ValueError('APK checksum mismatch')
 verified=subprocess.run([str(signer),'verify','--verbose','--print-certs',str(target)],text=True,capture_output=True,check=True)
 if 'certificate SHA-256 digest: '+cert not in verified.stdout:raise ValueError('Unexpected APK signer')
 checks.append(digest+'  '+target.name+'\n')
 evidence.append({'file':target.name,'sha256':digest,'certificate_sha256':cert,'signature_verified':True,'physical_device_tested':False})
(out/'SHA256SUMS-Android.txt').write_text(''.join(checks))
(out/'Android-verification-0.3.2.json').write_text(json.dumps(evidence,indent=2)+'\n')
print('Verified',len(evidence),'signed Android release packages')
