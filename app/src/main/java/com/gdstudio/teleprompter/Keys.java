package com.gdstudio.teleprompter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** Remote button mapping (defaults + user-learned buttons) and connected-remote detection. */
public class Keys {

    static final int NONE = 0, PLAY = 1, BACK = 2, FWD = 3, FASTER = 4, SLOWER = 5, EXIT = 6,
            TOP = 7, FONT_UP = 8, FONT_DN = 9, MIRROR = 10, LOOP = 11, SELECT = 12,
            NAV_UP = 13, NAV_DOWN = 14, NAV_LEFT = 15, NAV_RIGHT = 16;
    /** last action that can be learned in "Button set" */
    static final int LAST = NAV_RIGHT;
    static final String[] NAMES = {"", "Play / Pause", "Scroll back", "Scroll forward",
            "Speed +", "Speed −", "Exit prompter", "Go to start", "Font bigger",
            "Font smaller", "Mirror on/off", "Loop on/off", "Select / OK (menu)",
            "Menu up", "Menu down", "Menu left", "Menu right"};

    static int actionFor(Context c, int keyCode) {
        SharedPreferences p = Store.prefs(c);
        for (int a = PLAY; a <= LAST; a++) if (p.getInt("km_" + a, -1) == keyCode) return a;
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_BUTTON_A:
                return PLAY;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                return BACK;
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return FWD;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_EQUALS:
            case KeyEvent.KEYCODE_NUMPAD_ADD:
                return FASTER;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                return SLOWER;
            case KeyEvent.KEYCODE_ESCAPE:
                return EXIT;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                return SELECT;
        }
        return NONE;
    }

    static void assign(Context c, int action, int keyCode) {
        SharedPreferences p = Store.prefs(c);
        SharedPreferences.Editor e = p.edit();
        for (int a = PLAY; a <= LAST; a++)
            if (p.getInt("km_" + a, -1) == keyCode) e.remove("km_" + a);
        e.putInt("km_" + action, keyCode).apply();
    }

    /** true when the user assigned this key to an action in "Button set" */
    static boolean isCustom(Context c, int keyCode) {
        SharedPreferences p = Store.prefs(c);
        for (int a = PLAY; a <= LAST; a++) if (p.getInt("km_" + a, -1) == keyCode) return true;
        return false;
    }

    static int custom(Context c, int action) {
        return Store.prefs(c).getInt("km_" + action, -1);
    }

    static void reset(Context c) {
        SharedPreferences.Editor e = Store.prefs(c).edit();
        for (int a = PLAY; a <= LAST; a++) e.remove("km_" + a);
        e.apply();
    }

    static String keyName(int code) {
        String s = KeyEvent.keyCodeToString(code);
        return s.startsWith("KEYCODE_") ? s.substring(8) : s;
    }

    /** Names of external key-sending devices (Bluetooth remotes, clickers, keyboards). */
    static List<String> remotes() {
        List<String> out = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual()) continue;
            if (Build.VERSION.SDK_INT >= 29 && !d.isExternal()) continue;
            int src = d.getSources();
            boolean keys = (src & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
                    || (src & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
                    || (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
            if (!keys) continue;
            String n = d.getName();
            if (Build.VERSION.SDK_INT < 29) {
                String l = n.toLowerCase();
                if (l.contains("gpio") || l.contains("pmic") || l.contains("pon")
                        || l.contains("touch") || l.contains("fingerprint") || l.contains("qwerty")
                        || l.contains("headset") || l.contains("hall") || l.contains("keypad"))
                    continue;
            }
            if (!out.contains(n)) out.add(n);
        }
        return out;
    }
}
