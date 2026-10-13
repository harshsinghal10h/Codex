The design brief comes from the user's request: preserve Lively Wallpaper's layout and adapt it to Android, adding Liquid Glass without changing the application's structure.

The source reference is lively-community/lively at 25a0a5f4ed1988ff42117d3a17feb321062237fe. MainWindow.xaml uses top Library/Gallery navigation, Add, active wallpaper configuration and Settings. LibraryView.xaml uses thumbnail tiles followed by title and description. These are preserved, with touch-sized actions and a responsive two/three/four-column grid.

The selected direction is native Lively glass: neutral dark content, real scene artwork, a single blue action accent, and clear refracted navigation. Light and system themes are available. A wholesale replacement with editorial, saturated or immersive layouts would conflict with the request to preserve Lively. The App Designer workflow is therefore constrained to implementation and critique of the familiar layout.

Typography: Android's native sans with deliberate 24/20/16/14/12/11 sp hierarchy. Buttons have 48 dp touch targets. Cards retain the original thumbnail/title/description relationship; long titles truncate accessibly. Header chrome samples a bounded 192×64 backing texture and uses an AGSL edge lens on Android 13+, with a static native material fallback below that version. There is no full-screen blur or WebView hosting the application UI.

Arc informs short native state changes, accessible controls, and the process-memory chart. The chart samples actual app PSS; it makes no battery or CPU claims. Anatomy provides a small display/decoder/memory assembly with 48 seated solids, rendered as a native image so settings do not need a browser engine.

Signature interaction: an offline scene opens into a touch-responsive live preview; applying it invokes Android's wallpaper preview. Choosing another scene and cancelling the system picker preserves the current wallpaper. Reduced-motion preferences keep the interface still; animation of the selected live wallpaper remains a separate user choice.

Keep: top Library/Gallery navigation; thumbnail library; search; import; preview; wallpaper customisation; fit; audio; performance rules; active wallpaper controls; settings categories; screensaver access; attribution. Windows-only executable, monitor and privileged API features are described in the compatibility matrix rather than shown as working Android controls.
