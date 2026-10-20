package com.newtube.mobile.ui.glass;

import android.content.Context;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/** Styles the live sheet after Material and the caller have applied their window behaviour. */
public final class ClearPdfBottomSheetDialog extends BottomSheetDialog {
    public ClearPdfBottomSheetDialog(Context c) { super(c); }
    public ClearPdfBottomSheetDialog(Context c, int theme) { super(c, theme); }
    @Override public void show() {
        super.show();
        GlassRuntime.applyDialog(this);
        if (getWindow() != null) getWindow().getDecorView().post(() -> {
            if (isShowing()) GlassRuntime.applyDialog(this);
        });
    }
}
