#!/usr/bin/env python3
"""Build without downloading third-party runtime dependencies. Uses official SDK tools."""
import argparse, os, pathlib, subprocess, sys, zipfile, shutil
P=pathlib.Path(__file__).resolve().parents[1]
def run(*args): subprocess.run([str(x) for x in args], check=True)
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--sdk',default=os.environ.get('ANDROID_HOME'));parser.add_argument('--keystore',required=True);parser.add_argument('--alias',default='lively');parser.add_argument('--out',default=str(P/'build/Lively-Android-0.2.0.apk'));parser.add_argument('--test',action='store_true');a=parser.parse_args()
    sdk=pathlib.Path(a.sdk or '');platform=sdk/'platforms/android-35/android.jar'
    if not platform.exists():raise SystemExit('Install platforms;android-35 and build-tools;35.0.0, then set ANDROID_HOME.')
    candidates=[x for x in (sdk/'build-tools').iterdir() if (x/'aapt2').exists()];bt=next((x for x in candidates if x.name=='35.0.0'),sorted(candidates)[-1])
    build=P/'build';build.mkdir(exist_ok=True);classes=build/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    manifest=build/'AndroidManifest.xml';resources=P/'app/src/main/res';assets=P/'app/src/main/assets'
    run(sys.executable,P/'tools/compile-figure.py')
    manifest.write_text((P/'app/src/main/AndroidManifest.xml').read_text().replace('<manifest ', '<manifest package="io.lively.android" ',1))
    run(bt/'aapt2','compile','--dir',resources,'-o',build/'resources.zip')
    run(bt/'aapt2','link','-o',build/'base.apk','-I',platform,'--manifest',manifest,'--java',build/'generated','--min-sdk-version','26','--target-sdk-version','35','--version-code','2','--version-name','0.2.0','--auto-add-overlay','-A',assets,build/'resources.zip')
    sources=list((P/'app/src/main/java').rglob('*.java'))+list((build/'generated').rglob('*.java'))
    run('java','com.sun.tools.javac.Main','-encoding','UTF-8','--release','8','-classpath',platform,'-d',classes,*sources)
    with zipfile.ZipFile(build/'classes.jar','w',compression=zipfile.ZIP_DEFLATED) as z:
        for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
    dex=build/'dex';dex.mkdir(exist_ok=True)
    run('java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','26','--lib',platform,'--output',dex,build/'classes.jar')
    shutil.copyfile(build/'base.apk',build/'unsigned.apk')
    with zipfile.ZipFile(build/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
        for f in dex.glob('*.dex'):z.write(f,f.name)
    run(bt/'zipalign','-f','-p','4',build/'unsigned.apk',build/'aligned.apk')
    password=os.environ.get('LIVELY_KEY_PASSWORD')
    if not password:raise SystemExit('Set LIVELY_KEY_PASSWORD to the keystore password. Do not commit the key or password.')
    out=pathlib.Path(a.out);out.parent.mkdir(parents=True,exist_ok=True)
    run('java','-jar',bt/'lib/apksigner.jar','sign','--ks',a.keystore,'--ks-key-alias',a.alias,'--ks-pass','env:LIVELY_KEY_PASSWORD','--key-pass','env:LIVELY_KEY_PASSWORD','--out',out,build/'aligned.apk')
    run('java','-jar',bt/'lib/apksigner.jar','verify','--verbose',out)
    run(bt/'zipalign','-c','-p','4',out)
    print('Built:',out,'Bytes:',out.stat().st_size)
if __name__=='__main__':main()
