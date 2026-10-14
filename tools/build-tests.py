#!/usr/bin/env python3
import pathlib,subprocess,zipfile,os,shutil,argparse
P=pathlib.Path(__file__).resolve().parents[1]
def run(*a):subprocess.run([str(x) for x in a],check=True)
a=argparse.ArgumentParser();a.add_argument('--sdk',required=True);a.add_argument('--keystore',required=True);a=a.parse_args();sdk=pathlib.Path(a.sdk);bt=next(x for x in (sdk/'build-tools').iterdir() if (x/'aapt2').exists());jar=sdk/'platforms/android-35/android.jar';build=P/'build/native-tests';build.mkdir(parents=True,exist_ok=True);classes=build/'classes';classes.mkdir(exist_ok=True)
run(bt/'aapt2','link','-o',build/'base.apk','-I',jar,'--manifest',P/'tests/AndroidManifest.xml','--min-sdk-version','26','--target-sdk-version','35','-A',P/'tests/fixtures')
run('java','com.sun.tools.javac.Main','--release','8','-cp',str(jar)+':'+str(P/'build/classes'),'-d',classes,P/'tests/NativeTests.java',P/'tests/WallpaperFlowTests.java',P/'tests/FixtureProvider.java')
with zipfile.ZipFile(build/'tests.jar','w') as z:
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
run('java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',jar,'--classpath',P/'build/classes.jar','--output',build,build/'tests.jar')
shutil.copyfile(build/'base.apk',build/'unsigned.apk')
with zipfile.ZipFile(build/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:z.write(build/'classes.dex','classes.dex')
run(bt/'zipalign','-f','4',build/'unsigned.apk',build/'aligned.apk')
run('java','-jar',bt/'lib/apksigner.jar','sign','--ks',a.keystore,'--ks-key-alias','lively','--ks-pass','env:LIVELY_KEY_PASSWORD','--key-pass','env:LIVELY_KEY_PASSWORD','--out',build/'native-tests.apk',build/'aligned.apk')
