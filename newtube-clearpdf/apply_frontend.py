#!/usr/bin/env python3
"""Install the complete ClearPDF frontend overlay onto the pinned NewTube tree."""
from pathlib import Path
import shutil
import subprocess
import sys

PINNED_COMMIT = '756e7e56acba61ccdf5b4d5a802963f0eb62616c'

def main():
    if len(sys.argv) != 2:
        raise SystemExit('Usage: apply_frontend.py /path/to/newtube')
    root = Path(sys.argv[1]).resolve()
    actual = subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'],text=True).strip()
    if actual != PINNED_COMMIT:
        raise SystemExit('Expected pinned NewTube commit '+PINNED_COMMIT+'; got '+actual)
    source = Path(__file__).resolve().parent/'new-files'
    count = 0
    for p in sorted(source.rglob('*')):
        if not p.is_file(): continue
        rel=p.relative_to(source)
        target=root/rel
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(p,target)
        count+=1
    print('Applied ClearPDF frontend:',count,'files')

if __name__ == '__main__': main()
