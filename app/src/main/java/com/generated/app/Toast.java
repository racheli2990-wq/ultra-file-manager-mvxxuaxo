package com.generated.app;

import android.content.Context;

public class Toast {
    public static final int LENGTH_SHORT = android.widget.Toast.LENGTH_SHORT;
    public static final int LENGTH_LONG = android.widget.Toast.LENGTH_LONG;

    public static void stringToast(Context context, String text, int duration) {
        android.widget.Toast.makeText(context, text, duration).show();
    }
}
