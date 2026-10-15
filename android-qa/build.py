from pathlib import Path
import subprocess,zipfile,sys,shutil
P=Path(__file__).resolve().parent;root=P.parent
sdk=root/'toolchain/sdk';bt=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar'
b=P/'build';b.mkdir(exist_ok=True);(b/'classes').mkdir(exist_ok=True);(b/'dex').mkdir(exist_ok=True)
def run(cmd):subprocess.run([str(v) for v in cmd],check=True)
run([bt/'aapt2','link','-o',b/'base.apk','--manifest',P/'AndroidManifest.xml','-I',android,'-A',P/'assets'])
run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-source','8','-target','8','-cp',android,'-d',b/'classes',*list((P/'src').rglob('*.java'))])
with zipfile.ZipFile(b/'qa.jar','w') as z:
 for p in (b/'classes').rglob('*.class'):z.write(p,p.relative_to(b/'classes'))
run(['java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',android,'--output',b/'dex',b/'qa.jar'])
shutil.copyfile(b/'base.apk',b/'unsigned.apk')
with zipfile.ZipFile(b/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
 for p in (b/'dex').glob('*.dex'):z.write(p,p.name)
run([sys.executable,root/'VLC-Glass/tools/align_apk.py',b/'unsigned.apk',b/'aligned.apk'])
run([bt/'apksigner','sign','--ks',root/'private-signing/signing.p12','--ks-key-alias','vlcglass','--ks-pass','pass:android','--key-pass','pass:android','--out',P/'VLC-Glass-QA-0.3.1.apk',b/'aligned.apk'])
run([bt/'apksigner','verify',P/'VLC-Glass-QA-0.3.1.apk'])
print((P/'VLC-Glass-QA-0.3.1.apk').stat().st_size)
