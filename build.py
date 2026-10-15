#!/usr/bin/env python3
"""Rebuild this personal Brave + Proton integration with pinned dependencies."""
import argparse
import hashlib
import json
import os
import secrets
from pathlib import Path
import shutil
import subprocess
import urllib.request
from zipfile import ZipFile, ZIP_DEFLATED

ROOT=Path(__file__).resolve().parent
BUILD=ROOT/'_build'
TOOLS=BUILD/'tools'
UPSTREAM='https://github.com/brave/brave-browser/releases/download/v1.97.56/Bravearm64Universal.apk'
UPSTREAM_SHA='9b02840fb94dc172d3bf0073ce6877372bea0e311baba7952e4a0563df88ae89'

def download(url,path,digest):
    if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest()==digest: return
    print('Downloading',path.name,flush=True)
    temporary=path.with_suffix(path.suffix+'.part')
    with urllib.request.urlopen(url,timeout=120) as source, temporary.open('wb') as target:
        shutil.copyfileobj(source,target)
    if hashlib.sha256(temporary.read_bytes()).hexdigest()!=digest:
        temporary.unlink(); raise RuntimeError('Dependency checksum mismatch: '+path.name)
    temporary.replace(path)

def run(args,quiet=False):
    result=subprocess.run([str(a) for a in args],cwd=ROOT,
        stdout=subprocess.PIPE if quiet else None,stderr=subprocess.STDOUT if quiet else None)
    if result.returncode:
        # Avoid printing signing commands or passwords in failures.
        raise RuntimeError('A build step failed. Check the preceding validation output.')
    return result

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--import-only',action='store_true',help='Produce an APK without bundled account profiles')
    parser.add_argument('--apk',type=Path,help='Use an already-downloaded official Brave 1.97.56 ARM64 APK')
    parser.add_argument('--generate-signing-key',action='store_true',help='Create a new local signing key if none exists')
    args=parser.parse_args()
    for d in (BUILD,TOOLS,BUILD/'classes',BUILD/'tests',BUILD/'dex',BUILD/'signed'):d.mkdir(parents=True,exist_ok=True)
    if not shutil.which('java'): raise SystemExit('Java 17 or later is required.')
    for item in json.loads((ROOT/'toolchain.json').read_text()):
        download(item['url'],TOOLS/item['file'],item['sha256'])
    android=TOOLS/'android.jar'
    with ZipFile(TOOLS/'android-platform.zip') as z:
        name=next(n for n in z.namelist() if n.endswith('/android.jar'))
        android.write_bytes(z.read(name))
    apk=args.apk.resolve() if args.apk else BUILD/'Brave-official-arm64-v1.97.56.apk'
    if not args.apk: download(UPSTREAM,apk,UPSTREAM_SHA)
    if hashlib.sha256(apk.read_bytes()).hexdigest()!=UPSTREAM_SHA:
        raise RuntimeError('The upstream APK must match the official pinned release checksum.')
    sources=list((ROOT/'src').rglob('*.java'))+list((ROOT/'stubs').rglob('*.java'))
    run(['java','-jar',TOOLS/'ecj.jar','-8','-proc:none','-cp',android,'-d',BUILD/'classes']+sources)
    tests=[ROOT/'src/dev/harsh/protonbridge/WireGuardProfile.java',
           ROOT/'src/dev/harsh/protonbridge/TunnelStats.java',ROOT/'tests/ProfileTests.java']
    run(['java','-jar',TOOLS/'ecj.jar','-8','-proc:none','-d',BUILD/'tests']+tests)
    test_args=[] if args.import_only else [ROOT/'personal/jp.conf',ROOT/'personal/us.conf']
    run(['java','-cp',BUILD/'tests','dev.harsh.protonbridge.ProfileTests']+test_args)
    jar=BUILD/'integration.jar'
    with ZipFile(jar,'w',ZIP_DEFLATED) as z:
        for f in (BUILD/'classes/dev/harsh/protonbridge').rglob('*.class'):
            z.write(f,f.relative_to(BUILD/'classes').as_posix())
    run(['java','-cp',TOOLS/'r8.jar','com.android.tools.r8.D8','--release','--min-api','29',
         '--lib',android,'--output',BUILD/'dex',jar])
    settings=BUILD/'settings-dex'
    run([os.sys.executable,ROOT/'scripts/patch_settings_dex.py','--apk',apk,'--tools',TOOLS,'--output',settings])
    unsigned=BUILD/'Brave-Proton-Free-unsigned.apk'
    packaging=['python',ROOT/'scripts/package_apk.py','--apk',apk,'--dex',BUILD/'dex/classes.dex',
               '--settings-dex',settings/'classes5.dex','--settings-activity-dex',settings/'classes8.dex','--output',unsigned]
    packaging[0]=os.sys.executable
    if not args.import_only: packaging += ['--japan',ROOT/'personal/jp.conf','--us',ROOT/'personal/us.conf']
    run(packaging)
    credential_path=ROOT/'signing/credentials.json'
    if not credential_path.exists() and args.generate_signing_key:
        credential_path.parent.mkdir(parents=True,exist_ok=True)
        os.chmod(credential_path.parent,0o700)
        password=secrets.token_urlsafe(32)
        credentials={'alias':'personal-proton','store_password':password,'key_password':password}
        run(['keytool','-genkeypair','-keystore',ROOT/'signing/personal-proton.p12','-storetype','PKCS12',
             '-alias',credentials['alias'],'-storepass',password,'-keypass',password,
             '-keyalg','RSA','-keysize','3072','-validity','3650','-dname','CN=Personal Brave Proton Build'],quiet=True)
        credential_path.write_text(json.dumps(credentials))
        os.chmod(credential_path,0o600)
    credentials=json.loads(credential_path.read_text())
    for f in (BUILD/'signed').glob('*.apk'): f.unlink()
    run(['java','-jar',TOOLS/'uber-apk-signer.jar','--apks',unsigned,'--ks',ROOT/'signing/personal-proton.p12',
         '--ksAlias',credentials['alias'],'--ksPass',credentials['store_password'],
         '--ksKeyPass',credentials['key_password'],'--out',BUILD/'signed'],quiet=True)
    signed=next((BUILD/'signed').glob('*-aligned-signed.apk'))
    target=ROOT/'Brave-Proton-Free-1.97.56-arm64.apk'
    shutil.copyfile(signed,target)
    run([os.sys.executable,ROOT/'scripts/validate_apk.py','--original',apk,'--apk',target])
    print('Build complete:',target)

if __name__=='__main__': main()
