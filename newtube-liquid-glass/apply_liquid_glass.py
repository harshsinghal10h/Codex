#!/usr/bin/env python3
from __future__ import annotations
import shutil, sys
from pathlib import Path

PATCH_ROOT = Path(__file__).resolve().parent
NEW_FILES = PATCH_ROOT / "new-files"
MOBILE = Path("smarttubetv/src/stmobile/java/com/newtube/mobile/ui/common/MobileActivity.java")
SETTINGS = Path("smarttubetv/src/stmobile/java/com/newtube/mobile/ui/settings/SettingsPages.java")

def fail(msg): raise SystemExit("[NewTube Glass] " + msg)
def require(text, needle, path):
    if needle not in text: fail(f"Expected integration point missing in {path}: {needle!r}")

def patch_mobile(root):
    p = root / MOBILE
    t = p.read_text(encoding="utf-8")
    original = t
    require(t, "public abstract class MobileActivity extends MotherActivity", MOBILE)

    if "import com.newtube.mobile.ui.glass.GlassRuntime;" not in t:
        require(t, "import com.newtube.mobile.ui.playback.MiniPlayerBridge;", MOBILE)
        t = t.replace(
            "import com.newtube.mobile.ui.playback.MiniPlayerBridge;",
            "import com.newtube.mobile.ui.playback.MiniPlayerBridge;\nimport com.newtube.mobile.ui.glass.GlassRuntime;",
            1)

    if "GlassRuntime.register(this);" not in t:
        require(t, "ThemeMode.register(this);", MOBILE)
        t = t.replace("ThemeMode.register(this);",
                      "ThemeMode.register(this);\n        GlassRuntime.register(this);", 1)

    if "GlassRuntime.unregister(this);" not in t:
        require(t, "ThemeMode.unregister(this);", MOBILE)
        t = t.replace("ThemeMode.unregister(this);",
                      "GlassRuntime.unregister(this);\n        ThemeMode.unregister(this);", 1)

    if "View glassContent = findViewById(android.R.id.content);" not in t:
        needle = "        installContentInsets();\n    }"
        require(t, needle, MOBILE)
        t = t.replace(
            needle,
            "        installContentInsets();\n"
            "        View glassContent = findViewById(android.R.id.content);\n"
            "        if (glassContent != null) glassContent.post(() -> GlassRuntime.apply(this));\n"
            "    }",
            1)

    if "GlassRuntime.restoreForThemeRefresh(this);" not in t:
        needle = "        ThemeMode.syncResources(this, night);"
        require(t, needle, MOBILE)
        t = t.replace(
            needle,
            "        GlassRuntime.restoreForThemeRefresh(this);\n"
            "        ThemeMode.syncResources(this, night);",
            1)

    if "GlassRuntime.rebaseline(this);" not in t:
        needle = '''        applyFullscreenModeIfNeeded();
        com.liskovsoft.smartyoutubetv2.common.misc.NetPath.log("theme screen="'''
        require(t, needle, MOBILE)
        replacement = '''        applyFullscreenModeIfNeeded();
        GlassRuntime.rebaseline(this);
        GlassRuntime.apply(this);
        com.liskovsoft.smartyoutubetv2.common.misc.NetPath.log("theme screen="'''
        t = t.replace(needle, replacement, 1)

    if "        checkTheme();\n        GlassRuntime.apply(this);" not in t:
        needle = "        mResumed = true;\n        checkTheme();"
        require(t, needle, MOBILE)
        t = t.replace(
            needle,
            "        mResumed = true;\n        checkTheme();\n        GlassRuntime.apply(this);",
            1)

    if t != original:
        p.write_text(t, encoding="utf-8")

def patch_settings(root):
    p = root / SETTINGS
    t = p.read_text(encoding="utf-8")
    if 'public static final String APPEARANCE = "appearance";' in t:
        return

    require(t, '    public static final String GENERAL = "general";', SETTINGS)
    require(t, "            case GENERAL:\n                return AppPages.general(context);", SETTINGS)

    t = t.replace(
        '    public static final String GENERAL = "general";',
        '    public static final String GENERAL = "general";\n'
        '    public static final String APPEARANCE = "appearance";',
        1)

    t = t.replace(
        "            case GENERAL:\n                return AppPages.general(context);",
        "            case GENERAL:\n                return AppPages.general(context);\n"
        "            case APPEARANCE:\n                return GlassSettingsPage.build(context);",
        1)

    needle = '''        rows.add(SettingsRow.page(R.drawable.ic_settings_general, context.getString(R.string.mobile_settings_general),
                context.getString(R.string.mobile_settings_general_summary), GENERAL)
                .summary(() -> generalSummary(context)));'''
    require(t, needle, SETTINGS)
    replacement = needle + '''
        rows.add(SettingsRow.page(R.drawable.ic_settings_appearance,
                context.getString(R.string.mobile_settings_appearance),
                context.getString(R.string.mobile_settings_appearance_summary), APPEARANCE));'''
    p.write_text(t.replace(needle, replacement, 1), encoding="utf-8")

def replace_once(root, relative, old, new):
    p = root / relative
    t = p.read_text(encoding="utf-8")
    if new in t:
        return
    require(t, old, relative)
    p.write_text(t.replace(old, new, 1), encoding="utf-8")

def patch_components(root):
    base = "smarttubetv/src/stmobile/"
    replace_once(root, base + "res/layout/activity_mobile_browse.xml",
                 "<com.google.android.material.bottomnavigation.BottomNavigationView",
                 "<com.newtube.mobile.ui.glass.GlassNavigationView")
    replace_once(root, base + "res/layout/item_mobile_settings_row.xml",
                 '    android:layout_width="match_parent"',
                 '    android:id="@+id/newtube_glass_settings_row"\n    android:layout_width="match_parent"')
    replace_once(root, base + "res/layout/item_mobile_you_row.xml",
                 '    android:layout_width="match_parent"',
                 '    android:id="@+id/newtube_glass_you_row"\n    android:layout_width="match_parent"')
    replace_once(root, base + "java/com/newtube/mobile/ui/browse/MobileBrowseActivity.java",
                 "        mYouPanel.setVisibility(View.VISIBLE);",
                 "        mYouPanel.setVisibility(View.VISIBLE);\n        com.newtube.mobile.ui.glass.GlassRuntime.apply(this);")
    replace_once(root, base + "res/layout/fragment_mobile_settings_page.xml",
                 '    android:layout_width="match_parent"',
                 '    android:id="@+id/newtube_glass_settings_page"\n    android:layout_width="match_parent"')
    replace_once(root, base + "java/com/newtube/mobile/ui/settings/SettingsPageFragment.java",
                 "        super.onViewCreated(view, savedInstanceState);",
                 "        super.onViewCreated(view, savedInstanceState);\n"
                 "        if (SettingsPages.APPEARANCE.equals(mPageId)) {\n"
                 "            ((android.widget.LinearLayout) view).addView(\n"
                 "                    new com.newtube.mobile.ui.glass.GlassProfilePicker(requireContext(), this::rebuild), 1,\n"
                 "                    new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,\n"
                 "                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT));\n"
                 "        }\n"
                 "        view.post(() -> { if (getActivity() != null) com.newtube.mobile.ui.glass.GlassRuntime.apply(requireActivity()); });")
    replace_once(root, base + "java/com/newtube/mobile/ui/playback/MiniPlayerListInset.java",
                 "        int extra = requiredExtra();",
                 "        int extra = requiredExtra() + com.newtube.mobile.ui.glass.GlassShell.navigationInset(mList);")
    replace_once(root, base + "java/com/newtube/mobile/ui/playback/MiniPlayerListInset.java",
                 "if (extra != mAppliedExtra) {",
                 "if (extra != mAppliedExtra || mList.getPaddingBottom() != mBasePaddingBottom + extra) {")
    replace_once(root, base + "java/com/newtube/mobile/ui/browse/MobileBrowseActivity.java",
                 "View itemView = mBottomNav.findViewById(toMenuItemId(section.getId()));",
                 "View itemView = ((com.newtube.mobile.ui.glass.GlassNavigationView) mBottomNav).findNavigationItem(toMenuItemId(section.getId()));")
    replace_once(root, base + "java/com/newtube/mobile/ui/browse/MobileBrowseActivity.java",
                 "itemView.setOnLongClickListener(v -> {",
                 "((com.newtube.mobile.ui.glass.GlassNavigationView) mBottomNav).setNavigationLongClickListener(toMenuItemId(sectionId), v -> {")
    # Every bitmap that can appear behind the dock must support a software canvas.
    replace_once(root, base + "java/com/newtube/mobile/NewTubeGlideModule.java",
                 "new RequestOptions().set(HttpGlideUrlLoader.TIMEOUT, HTTP_TIMEOUT_MS)",
                 "new RequestOptions().disallowHardwareConfig().set(HttpGlideUrlLoader.TIMEOUT, HTTP_TIMEOUT_MS)")

def patch_fork_identity(root):
    p = root / "smarttubetv/build.gradle"
    t = p.read_text(encoding="utf-8")
    t = t.replace('applicationId "io.github.aleixrodriala.arc"', 'applicationId "io.github.harshsinghal10h.newtubeglass"')
    t = t.replace('versionCode 11500', 'versionCode 11502').replace('versionName "1.15.0"', 'versionName "1.15.0-glass.2"')
    t = t.replace('buildConfigField "boolean", "IN_APP_UPDATES", project.hasProperty(\'fdroid\') ? "false" : "true"',
                  'buildConfigField "boolean", "IN_APP_UPDATES", "false"')
    p.write_text(t, encoding="utf-8")
    replace_once(root, "smarttubetv/src/stmobile/AndroidManifest.xml",
                 'android:theme="@style/Theme.NewTube"\n        tools:replace="android:name,android:theme">',
                 'android:theme="@style/Theme.NewTube"\n        android:label="@string/newtube_glass_app_name"\n        tools:replace="android:name,android:theme,android:label">')

def main():
    if len(sys.argv) != 2:
        fail("Usage: apply_liquid_glass.py /path/to/newtube")
    root = Path(sys.argv[1]).resolve()
    if not (root / MOBILE).is_file() or not (root / SETTINGS).is_file():
        fail("Not a compatible NewTube v1.15.0 tree")

    patch_mobile(root)
    patch_settings(root)
    patch_components(root)
    patch_fork_identity(root)

    for src in NEW_FILES.rglob("*"):
        if src.is_file():
            dst = root / src.relative_to(NEW_FILES)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)

    print("[NewTube Glass] Applied successfully")

if __name__ == "__main__":
    main()
