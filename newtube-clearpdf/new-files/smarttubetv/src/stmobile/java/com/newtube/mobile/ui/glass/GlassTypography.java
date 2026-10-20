package com.newtube.mobile.ui.glass;

import android.content.Context;
import android.graphics.Typeface;
import androidx.core.content.res.ResourcesCompat;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Bundled OFL Google Sans Flex instances: the same rounded type family as LastWave. */
final class GlassTypography {
    private static Typeface display, body, label;
    static Typeface display(Context c) { if(display==null) display=ResourcesCompat.getFont(c,R.font.glass_display); return display; }
    static Typeface body(Context c) { if(body==null) body=ResourcesCompat.getFont(c,R.font.glass_body); return body; }
    static Typeface label(Context c) { if(label==null) label=ResourcesCompat.getFont(c,R.font.glass_label); return label; }
}
