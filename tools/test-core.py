#!/usr/bin/env python3
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1];out=root/'build/core-tests';out.mkdir(parents=True,exist_ok=True)
src=root/'app/src/main/java/io/lively/android'
subprocess.run(['java','com.sun.tools.javac.Main','-d',str(out),str(src/'EnginePolicy.java'),str(src/'ZipSandbox.java'),str(root/'tests/CoreTests.java')],check=True)
subprocess.run(['java','-cp',str(out),'io.lively.android.CoreTests'],check=True)
