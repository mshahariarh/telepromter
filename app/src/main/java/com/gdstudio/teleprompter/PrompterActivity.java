package com.gdstudio.teleprompter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class PrompterActivity extends Activity implements Choreographer.FrameCallback {

    private ScrollView scroll;
    private TextView text, status;
    private boolean playing = false;
    private int speed;
    private float pos = 0f;
    private long lastFrame = 0;
    private SharedPreferences prefs;

    private final Runnable hideStatus = () -> status.animate().alpha(0f).setDuration(400);

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        speed = prefs.getInt(MainActivity.K_SPEED, 5);
        int font = prefs.getInt(MainActivity.K_FONT, 42);
        boolean mirror = prefs.getBoolean(MainActivity.K_MIRROR, false);
        String script = prefs.getString(MainActivity.K_SCRIPT, "");
        if (script.trim().isEmpty()) script = "(Script khali — pichone giye script likhun)";

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        int screenH = getResources().getDisplayMetrics().heightPixels;

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        text = new TextView(this);
        text.setText(script);
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, font);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setLineSpacing(0, 1.25f);
        text.setPadding(dp(24), screenH / 3, dp(24), screenH);
        scroll.addView(text, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (mirror) scroll.setScaleX(-1f);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Reading guide line at 1/3 of the screen
        View guide = new View(this);
        guide.setBackgroundColor(Color.argb(110, 255, 196, 0));
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        gl.topMargin = screenH / 3;
        root.addView(guide, gl);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setBackgroundColor(Color.argb(170, 40, 40, 40));
        status.setPadding(dp(14), dp(8), dp(14), dp(8));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sl.bottomMargin = dp(24);
        root.addView(status, sl);

        GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                togglePlay();
                return true;
            }
        });
        scroll.setOnTouchListener((v, e) -> {
            gd.onTouchEvent(e);
            return false; // let ScrollView handle manual dragging
        });

        setContentView(root);
        showStatus("Ready — Play chapun (tap / remote)");
    }

    private void togglePlay() {
        playing = !playing;
        if (playing) {
            pos = scroll.getScrollY();
            lastFrame = 0;
            Choreographer.getInstance().postFrameCallback(this);
            showStatus("▶ Playing  •  Speed " + speed);
        } else {
            Choreographer.getInstance().removeFrameCallback(this);
            showStatus("❚❚ Paused");
        }
    }

    private void changeSpeed(int delta) {
        speed = Math.max(1, Math.min(20, speed + delta));
        prefs.edit().putInt(MainActivity.K_SPEED, speed).apply();
        showStatus("Speed " + speed + (playing ? "" : "  (Paused)"));
    }

    private void jump(int direction) {
        int step = (int) (scroll.getHeight() * 0.35f) * direction;
        scroll.scrollBy(0, step);
        pos = scroll.getScrollY();
        showStatus(direction > 0 ? "▼ Samne" : "▲ Pichone");
    }

    private void showStatus(String s) {
        status.setText(s);
        status.removeCallbacks(hideStatus);
        status.animate().cancel();
        status.setAlpha(1f);
        status.postDelayed(hideStatus, 1800);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!playing) return;
        if (lastFrame != 0) {
            float dt = (frameTimeNanos - lastFrame) / 1_000_000_000f;
            // If the user dragged manually, follow their position
            if (Math.abs(scroll.getScrollY() - (int) pos) > dp(4)) pos = scroll.getScrollY();
            float pxPerSec = speed * 12f * getResources().getDisplayMetrics().density;
            pos += pxPerSec * dt;
            int max = Math.max(0, text.getHeight() - scroll.getHeight());
            if (pos >= max) {
                pos = max;
                scroll.scrollTo(0, max);
                playing = false;
                showStatus("Shesh ✓");
                return;
            }
            scroll.scrollTo(0, (int) pos);
        }
        lastFrame = frameTimeNanos;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private static final int A_NONE = 0, A_PLAY = 1, A_BACK = 2, A_FWD = 3,
            A_FASTER = 4, A_SLOWER = 5, A_EXIT = 6;

    private int actionFor(int keyCode) {
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
                return A_PLAY;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                return A_BACK;
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return A_FWD;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_EQUALS:
            case KeyEvent.KEYCODE_NUMPAD_ADD:
                return A_FASTER;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                return A_SLOWER;
            case KeyEvent.KEYCODE_ESCAPE:
                return A_EXIT;
        }
        return A_NONE;
    }

    // Intercept keys before the ScrollView sees them, so remotes always work
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = actionFor(event.getKeyCode());
        if (action == A_NONE) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean first = event.getRepeatCount() == 0;
            switch (action) {
                case A_PLAY: if (first) togglePlay(); break;
                case A_BACK: jump(-1); break;
                case A_FWD: jump(1); break;
                case A_FASTER: changeSpeed(1); break;
                case A_SLOWER: changeSpeed(-1); break;
                case A_EXIT: finish(); break;
            }
        }
        return true; // consume both down and up (stops volume change)
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playing) togglePlay();
    }
}
