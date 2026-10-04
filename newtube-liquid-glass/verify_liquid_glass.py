#!/usr/bin/env python3
import sys, xml.etree.ElementTree as ET
from pathlib import Path

REQ=[
"smarttubetv/src/stmobile/java/com/newtube/mobile/ui/glass/GlassPreferences.java",
"smarttubetv/src/stmobile/java/com/newtube/mobile/ui/glass/GlassGeometry.java",
"smarttubetv/src/stmobile/java/com/newtube/mobile/ui/glass/GlassDrawable.java",
"smarttubetv/src/stmobile/java/com/newtube/mobile/ui/glass/GlassRuntime.java",
"smarttubetv/src/stmobile/java/com/newtube/mobile/ui/settings/GlassSettingsPage.java",
"smarttubetv/src/stmobile/res/values/strings_glass.xml",
"smarttubetv/src/stmobile/res/values/ids_glass.xml",
"smarttubetv/src/stmobile/res/drawable/ic_settings_appearance.xml"]
def main():
    if len(sys.argv)!=2: return 2
    root=Path(sys.argv[1]).resolve(); errors=[]
    for rel in REQ:
        p=root/rel
        if not p.is_file(): errors.append("missing "+rel)
        elif p.suffix==".xml":
            try: ET.parse(p)
            except Exception as e: errors.append(f"bad XML {rel}: {e}")
    mobile=(root/"smarttubetv/src/stmobile/java/com/newtube/mobile/ui/common/MobileActivity.java").read_text()
    settings=(root/"smarttubetv/src/stmobile/java/com/newtube/mobile/ui/settings/SettingsPages.java").read_text()
    for needle in ["GlassRuntime.register(this);","GlassRuntime.unregister(this);","GlassRuntime.restoreForThemeRefresh(this);","GlassRuntime.rebaseline(this);"]:
        if needle not in mobile: errors.append("missing mobile hook "+needle)
    for needle in ['APPEARANCE = "appearance"',"GlassSettingsPage.build(context)","ic_settings_appearance"]:
        if needle not in settings: errors.append("missing settings hook "+needle)
    if errors:
        print("[NewTube Glass] VERIFY FAILED"); [print(" -",e) for e in errors]; return 1
    print("[NewTube Glass] VERIFY PASS"); return 0
if __name__=="__main__": raise SystemExit(main())
