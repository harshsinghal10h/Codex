package com.newtube.mobile.ui.glass;

import android.content.Context;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Uses attachment so callers retain ownership of their dialog listeners. */
public final class ClearPdfAlertDialogBuilder extends MaterialAlertDialogBuilder {
    public ClearPdfAlertDialogBuilder(Context context) { super(context); }
    public ClearPdfAlertDialogBuilder(Context context, int theme) { super(context, theme); }
    @Override public AlertDialog create() {
        AlertDialog dialog=super.create();
        if (dialog.getWindow()!=null) dialog.getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                view.post(() -> { if (dialog.isShowing()) GlassRuntime.applyDialog(dialog); });
            }
            @Override public void onViewDetachedFromWindow(View view) {}
        });
        return dialog;
    }
}
