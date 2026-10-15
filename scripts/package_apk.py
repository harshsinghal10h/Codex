#!/usr/bin/env python3
"""Add the compiled integration to an official Brave APK, preserving every original payload byte."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from patch_manifest import patch_bytes

KNOWN = {
    '9b02840fb94dc172d3bf0073ce6877372bea0e311baba7952e4a0563df88ae89': 'arm64',
    'f5406dbf61dad16195ebb9d9696d10c9c01e31ed5532fb4162d20bebf2fcbc00': 'x64',
}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--dex',type=Path,required=True)
    parser.add_argument('--settings-dex',type=Path,required=True)
    parser.add_argument('--settings-activity-dex',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--japan',type=Path)
    parser.add_argument('--us',type=Path)
    args=parser.parse_args()
    digest=hashlib.sha256(args.apk.read_bytes()).hexdigest()
    if digest not in KNOWN: raise SystemExit('Unrecognized upstream APK: use the verified Brave 1.97.56 release.')
    if bool(args.japan) != bool(args.us): raise SystemExit('Supply both personal profiles, or neither.')
    with ZipFile(args.apk) as original, ZipFile(args.output,'w') as output:
        if 'classes7.dex' in original.namelist() or 'classes8.dex' in original.namelist(): raise SystemExit('Unexpected upstream multidex layout.')
        for item in original.infolist():
            if item.filename.startswith('META-INF/'): continue  # replace the official signing block
            data=original.read(item)
            if item.filename=='AndroidManifest.xml': data=patch_bytes(data)
            if item.filename=='classes5.dex': data=args.settings_dex.read_bytes()
            output.writestr(item,data)
        output.writestr('classes7.dex',args.dex.read_bytes(),compress_type=ZIP_DEFLATED)
        output.writestr('classes8.dex',args.settings_activity_dex.read_bytes(),compress_type=ZIP_DEFLATED)
        index=[]
        if args.japan:
            for path,id,label,name in [(args.japan,'jp','JP-FREE#16','jp.conf'),
                                       (args.us,'us','US-FREE#94','us.conf')]:
                blob=path.read_bytes()
                if len(blob)>65536: raise SystemExit('Profile is too large.')
                output.writestr('assets/proton_personal/'+name,blob,compress_type=ZIP_DEFLATED)
                index.append({'id':id,'label':label,'file':name})
        output.writestr('assets/proton_personal/index.json',json.dumps(index).encode(),compress_type=ZIP_DEFLATED)
    print('Packaged',KNOWN[digest],'personal integration.' if index else 'import-only integration.')
    print('Original resources, native libraries and five dex files preserved; Settings hook added.')

if __name__=='__main__': main()
