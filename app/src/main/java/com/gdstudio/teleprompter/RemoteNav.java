package com.gdstudio.teleprompter;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import java.util.ArrayList;

/**
 * Remote menu navigation: the joystick (or arrow keys while active) moves an orange
 * highlight over every button / slider / colour on screen, and Select / OK clicks it.
 * Sliders change with left / right. The highlight hides itself after a few idle seconds.
 */
public class RemoteNav {

    static final int LEFT = 1, RIGHT = 2, UP = 3, DOWN = 4;

    private final Activity a;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final GradientDrawable hl = new GradientDrawable();
    private View cur, defaultView;
    private boolean active = false;
    private int heldDir = 0;

    private final Runnable idle = this::exit;
    private final Runnable repeat = new Runnable() {
        @Override
        public void run() {
            if (heldDir != 0) {
                move(heldDir);
                h.postDelayed(this, 200);
            }
        }
    };

    RemoteNav(Activity activity) {
        a = activity;
        int dp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                a.getResources().getDisplayMetrics());
        hl.setColor(Color.argb(60, 255, 152, 0));
        hl.setStroke(3 * dp, Color.rgb(255, 152, 0));
        hl.setCornerRadius(8 * dp);
    }

    void setDefault(View v) { defaultView = v; }
    boolean isActive() { return active; }

    // ---------- input ----------

    static int joyDir(MotionEvent e) {
        float x = e.getAxisValue(MotionEvent.AXIS_X), y = e.getAxisValue(MotionEvent.AXIS_Y);
        float[][] alt = {
                {e.getAxisValue(MotionEvent.AXIS_HAT_X), e.getAxisValue(MotionEvent.AXIS_HAT_Y)},
                {e.getAxisValue(MotionEvent.AXIS_Z), e.getAxisValue(MotionEvent.AXIS_RZ)},
                {e.getAxisValue(MotionEvent.AXIS_RX), e.getAxisValue(MotionEvent.AXIS_RY)}};
        for (float[] p : alt)
            if (Math.abs(p[0]) + Math.abs(p[1]) > Math.abs(x) + Math.abs(y)) { x = p[0]; y = p[1]; }
        if (Math.max(Math.abs(x), Math.abs(y)) < 0.4f) return 0;
        if (Math.abs(x) > Math.abs(y)) return x < 0 ? LEFT : RIGHT;
        return y < 0 ? UP : DOWN;
    }

    static boolean isJoystick(MotionEvent e) {
        int s = e.getSource();
        return (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
    }

    /** Joystick movement. Returns true when handled. */
    boolean onMotion(MotionEvent e) {
        if (!isJoystick(e) || e.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        int d = joyDir(e);
        if (d != heldDir) {
            heldDir = d;
            h.removeCallbacks(repeat);
            if (d != 0) {
                move(d);
                h.postDelayed(repeat, 450);
            }
        }
        return true;
    }


    /** Keys: arrows / OK / Back while the highlight is shown (or from a gamepad). */
    boolean onKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        int dir = 0;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) dir = LEFT;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) dir = RIGHT;
        else if (code == KeyEvent.KEYCODE_DPAD_UP) dir = UP;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN) dir = DOWN;
        // learned "Menu up/down/left/right" buttons
        int act = Keys.actionFor(a, code);
        if (act >= Keys.NAV_UP && act <= Keys.NAV_RIGHT) dir = navDir(act);
        else if (Keys.isCustom(a, code)) dir = 0;
        // arrow keys / joystick-as-arrows always move the menu highlight
        if (dir != 0) {
            if (down) move(dir);
            return true;
        }
        if (!active) return false;
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A
                || code == KeyEvent.KEYCODE_BUTTON_THUMBL || code == KeyEvent.KEYCODE_BUTTON_SELECT
                || Keys.actionFor(a, code) == Keys.SELECT) {
            if (down && e.getRepeatCount() == 0) select();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B
                || code == KeyEvent.KEYCODE_ESCAPE) {
            if (down) exit();
            return true;
        }
        return false;
    }

    static int navDir(int action) {
        switch (action) {
            case Keys.NAV_UP: return UP;
            case Keys.NAV_DOWN: return DOWN;
            case Keys.NAV_LEFT: return LEFT;
            case Keys.NAV_RIGHT: return RIGHT;
        }
        return 0;
    }

    /** For Bluetooth (in-app) remotes: run a learned menu action. */
    boolean doNav(int action) {
        if (action == Keys.SELECT) {
            if (active) select();
            else start();
            return true;
        }
        int d = navDir(action);
        if (d == 0) return false;
        move(d);
        return true;
    }

    // ---------- actions ----------

    void start() {
        active = true;
        if (cur == null || !cur.isShown()) cur = pickDefault();
        show();
    }

    void select() {
        if (cur == null || !cur.isShown()) {
            start();
            return;
        }
        if (!(cur instanceof SeekBar)) cur.performClick();
        touch();
        // the screen may change after a click (panel opens / closes)
        h.postDelayed(() -> {
            if (cur != null && !cur.isShown()) cur = pickDefault();
            show();
        }, 120);
    }

    void exit() {
        active = false;
        heldDir = 0;
        h.removeCallbacks(repeat);
        h.removeCallbacks(idle);
        unhighlight();
        cur = null;
    }

    void move(int dir) {
        active = true;
        if (cur == null || !cur.isShown()) {
            cur = pickDefault();
        } else if (cur instanceof SeekBar && (dir == LEFT || dir == RIGHT)) {
            int code = dir == LEFT ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
            cur.onKeyDown(code, new KeyEvent(KeyEvent.ACTION_DOWN, code));
            cur.onKeyUp(code, new KeyEvent(KeyEvent.ACTION_UP, code));
        } else {
            View next = search(dir);
            if (next != null) cur = next;
        }
        show();
    }

    private View pickDefault() {
        if (defaultView != null && defaultView.isShown()) return defaultView;
        ArrayList<View> c = candidates();
        return c.isEmpty() ? null : c.get(0);
    }

    private View lastHl;

    private void show() {
        unhighlight();
        touch();
        if (cur == null) return;
        int w = cur.getWidth(), ht = cur.getHeight();
        hl.setBounds(0, 0, w, ht);
        cur.getOverlay().add(hl);
        lastHl = cur;
        cur.requestRectangleOnScreen(new Rect(0, 0, w, ht), false);
    }

    private void unhighlight() {
        if (lastHl != null) lastHl.getOverlay().remove(hl);
        lastHl = null;
    }

    private void touch() {
        h.removeCallbacks(idle);
        h.postDelayed(idle, 8000);
    }

    // ---------- finding the next item ----------

    private ArrayList<View> candidates() {
        ArrayList<View> out = new ArrayList<>();
        collect(a.getWindow().getDecorView(), out);
        return out;
    }

    private void collect(View v, ArrayList<View> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if ((v.hasOnClickListeners() || v instanceof SeekBar) && v.isEnabled()
                && v.getWidth() > 0 && v.getHeight() > 0 && v.isShown()) {
            out.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    private View search(int dir) {
        int[] p = new int[2];
        cur.getLocationOnScreen(p);
        float cx = p[0] + cur.getWidth() / 2f, cy = p[1] + cur.getHeight() / 2f;
        View best = null;
        float bestScore = Float.MAX_VALUE;
        for (View v : candidates()) {
            if (v == cur) continue;
            v.getLocationOnScreen(p);
            float vx = p[0] + v.getWidth() / 2f, vy = p[1] + v.getHeight() / 2f;
            float dx = vx - cx, dy = vy - cy, main, side;
            switch (dir) {
                case LEFT: main = -dx; side = Math.abs(dy); break;
                case RIGHT: main = dx; side = Math.abs(dy); break;
                case UP: main = -dy; side = Math.abs(dx); break;
                default: main = dy; side = Math.abs(dx); break;
            }
            if (main <= 4) continue;
            float score = main + side * 2.5f;
            if (score < bestScore) {
                bestScore = score;
                best = v;
            }
        }
        return best;
    }
}
