package com.omarchytv.home;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

/**
 * CaskaydiaMono on every API this launcher installs on. {@code Resources.getFont} and
 * {@code android:fontFamily="@font/..."} are API 26; on Android 6 either one crashes while inflating
 * the home screen. The family is loaded through AndroidX, and a missing font becomes the system monospace
 * so the UI still comes up.
 */
final class Fonts {
    private static Typeface cached;

    private Fonts() {
    }

    static Typeface get(Context c) {
        Typeface ready = cached;
        if (ready != null) return ready;
        Typeface loaded = null;
        try {
            Context app = c.getApplicationContext();
            loaded = ResourcesCompat.getFont(app != null ? app : c, R.font.caskaydia);
        } catch (Throwable ignored) {
        }
        if (loaded == null) loaded = Typeface.MONOSPACE;
        cached = loaded;
        return loaded;
    }

    /** Call before the first {@code setContentView}. Later item layouts then pick up the font too. */
    static void install(Activity activity) {
        if (activity == null) return;
        LayoutInflater inflater = activity.getLayoutInflater();
        try {
            inflater.setFactory2(new LayoutInflater.Factory2() {
                @Override
                public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
                    return make(name, context, attrs);
                }

                @Override
                public View onCreateView(String name, Context context, AttributeSet attrs) {
                    return make(name, context, attrs);
                }
            });
        } catch (IllegalStateException ignored) {
            // The window already has a factory. apply() still covers trees we inflate ourselves.
        }
    }

    /** Sets the launcher font on every text view under {@code root}, keeping bold / italic. */
    static void apply(View root) {
        if (root == null) return;
        if (root instanceof TextView) {
            TextView tv = (TextView) root;
            Typeface had = tv.getTypeface();
            int style = had == null ? Typeface.NORMAL : had.getStyle();
            Typeface base = get(tv.getContext());
            tv.setTypeface(style == Typeface.NORMAL ? base : Typeface.create(base, style));
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i));
        }
    }

    private static View make(String name, Context context, AttributeSet attrs) {
        String simple = name;
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            if (!name.startsWith("android.widget.")) return null;
            simple = name.substring(dot + 1);
        }
        TextView tv;
        if ("TextView".equals(simple)) tv = new TextView(context, attrs);
        else if ("EditText".equals(simple)) tv = new EditText(context, attrs);
        else return null;
        apply(tv);
        return tv;
    }
}
