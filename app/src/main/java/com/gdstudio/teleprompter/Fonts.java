package com.gdstudio.teleprompter;

import android.content.Context;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/** Bangla / custom fonts bundled in assets/fonts (downloaded at build time). */
public class Fonts {

    static final String SYSTEM = "System";
    private static final HashMap<String, Typeface> cache = new HashMap<>();

    static String[] list(Context c) {
        ArrayList<String> l = new ArrayList<>();
        l.add(SYSTEM);
        try {
            String[] a = c.getAssets().list("fonts");
            if (a != null) {
                Arrays.sort(a);
                for (String f : a)
                    if (f.endsWith(".ttf") || f.endsWith(".otf")) l.add(f.substring(0, f.lastIndexOf('.')));
            }
        } catch (Exception ignored) {
        }
        return l.toArray(new String[0]);
    }

    static String label(String name) {
        return name == null ? SYSTEM : name.replace('_', ' ');
    }

    static Typeface get(Context c, String name) {
        if (name == null || name.equals(SYSTEM)) return Typeface.DEFAULT;
        Typeface t = cache.get(name);
        if (t == null) {
            try {
                String[] a = c.getAssets().list("fonts");
                if (a != null) for (String f : a) {
                    if (f.startsWith(name + ".")) {
                        t = Typeface.createFromAsset(c.getAssets(), "fonts/" + f);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            if (t == null) t = Typeface.DEFAULT;
            cache.put(name, t);
        }
        return t;
    }
}
