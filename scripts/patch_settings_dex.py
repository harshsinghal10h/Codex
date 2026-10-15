#!/usr/bin/env python3
"""Move only BraveSettingsActivity into a small dex and add the Proton menu hook.

The original classes5 method table is full. Moving this one class preserves its
name and inheritance while leaving space for the new method reference. No
Chromium resources are rebuilt. Validation compares every remaining method.
"""
import argparse
from pathlib import Path
import shutil
import subprocess

CLASS=Path('org/chromium/chrome/browser/settings/BraveSettingsActivity.smali')
SIGNATURE='.method public final onCreateOptionsMenu(Landroid/view/Menu;)Z'
SUPER='    invoke-super {p0, p1}, Lorg/chromium/chrome/browser/settings/SettingsActivity;->onCreateOptionsMenu(Landroid/view/Menu;)Z'
HOOK='    invoke-static {p0, p1}, Ldev/harsh/protonbridge/BraveVpnMenuHook;->addMenu(Landroid/app/Activity;Landroid/view/Menu;)V'

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--apk',type=Path,required=True)
    p.add_argument('--tools',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args(); a.output.mkdir(parents=True,exist_ok=True)
    host=a.output/'host'; host.mkdir(exist_ok=True)
    jar=a.tools/'apktool.jar'
    subprocess.run(['java','-jar',str(a.tools/'ecj.jar'),'-17','-proc:none','-cp',str(jar),
                    '-d',str(host),str(Path(__file__).with_name('SmaliBridge.java'))],check=True)
    cp=str(host)+':'+str(jar)
    original=a.output/'original-settings'; moved=a.output/'moved-settings'
    for path in (original,moved):
        if path.exists(): shutil.rmtree(path)
        path.mkdir()
    # Serial decoding avoids truncated class files from the library's parallel writer.
    subprocess.run(['java','-XX:ActiveProcessorCount=1','-Xmx2g','-cp',cp,'SmaliBridge','decode',str(a.apk),str(original)],check=True)
    path=original/CLASS
    text=path.read_text()
    assert text.count(SIGNATURE)==1, 'Unexpected Settings method signature'
    begin=text.index(SIGNATURE); end=text.index('.end method',begin)
    method=text[begin:end]
    assert method.count(SUPER)==1, 'Unexpected Settings superclass call'
    assert method.count('    return p0')==1, 'Unexpected Settings return register'
    method=method.replace(SUPER,HOOK+'\n\n'+SUPER)
    method=method.replace('    return p0','    const/4 p0, 0x1\n\n    return p0')
    target=moved/CLASS; target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(text[:begin]+method+text[end:]); path.unlink()
    for directory,name in ((original,'classes5.dex'),(moved,'classes8.dex')):
        subprocess.run(['java','-Xmx2g','-cp',cp,'SmaliBridge','assemble',str(directory),str(a.output/name)],check=True)
    print('Native Brave Settings menu patched; controller remains inside the browser package.')

if __name__=='__main__': main()
