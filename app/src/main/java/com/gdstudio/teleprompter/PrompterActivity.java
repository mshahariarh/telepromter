package com.gdstudio.teleprompter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class PrompterActivity extends Activity implements Choreographer.FrameCallback {

    // ---- Settings (all saved, all live) ----
    private int fontSize, lineSp, letterSp, speed, margin, textColor, bgColor;
    private boolean mirror, bold, center;

    private static final int[] TEXT_COLORS = {
            Color.WHITE, Color.rgb(255, 214, 0), Color.rgb(0, 230, 118),
            Color.rgb(0, 229, 255), Color.rgb(255, 128, 171), Color.rgb(255, 145, 0),
            Color.rgb(30, 30, 30)};
    private static final int[] BG_COLORS = {
            Color.BLACK, Color.rgb(28, 28, 32), Color.rgb(13, 27, 62),
            Color.rgb(0, 50, 30), Color.WHITE};

    private FrameLayout root;
    private ScrollView scroll;
    private TextView text, status, playBtn;
    private LinearLayout topBar;
    private ScrollView panel;
    private LinearLayout textSwatches, bgSwatches;
    private TextView mirrorBtn, boldBtn, alignBtn;

    private boolean playing = false;
    private float pos = 0f;
    private long lastFrame = 0;
    private int screenH;
    private SharedPreferences prefs;

    private final Runnable hideStatus = () -> status.animate().alpha(0f).setDuration(400);
    private final Runnable hideBar = () -> {
        if (playing && panel.getVisibility() != View.VISIBLE)
            topBar.animate().alpha(0f).setDuration(400)
                    .withEndAction(() -> topBar.setVisibility(View.GONE));
    };

    interface IntCb { void on(int v); }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    // ================= Setup =================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        loadSettings();
        screenH = getResources().getDisplayMetrics().heightPixels;

        root = new FrameLayout(this);

        // --- Script view ---
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        text = new TextView(this);
        String s = prefs.getString(MainActivity.K_SCRIPT, "");
        text.setText(s.trim().isEmpty() ? "(Script khali — ✎ chepe script likhun)" : s);
        scroll.addView(text, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // --- Reading guide line ---
        View guide = new View(this);
        guide.setBackgroundColor(Color.argb(110, 255, 196, 0));
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(-1, dp(2));
        gl.topMargin = screenH / 3;
        root.addView(guide, gl);

        buildTopBar();
        buildPanel();

        // --- Status pill ---
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        status.setBackground(round(Color.argb(190, 40, 40, 40), dp(20)));
        status.setPadding(dp(16), dp(8), dp(16), dp(8));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        sl.topMargin = dp(70);
        root.addView(status, sl);

        GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (panel.getVisibility() == View.VISIBLE) togglePanel();
                else togglePlay();
                return true;
            }
        });
        scroll.setOnTouchListener((v, e) -> {
            gd.onTouchEvent(e);
            return false;
        });

        setContentView(root);
        applyStyle(false);
        showStatus("Ready — ▶ chapun ba remote use korun");
    }

    private void loadSettings() {
        fontSize = prefs.getInt(MainActivity.K_FONT, 42);
        speed = prefs.getInt(MainActivity.K_SPEED, 5);
        mirror = prefs.getBoolean(MainActivity.K_MIRROR, false);
        lineSp = prefs.getInt("lineSp", 13);      // 1.3x
        letterSp = prefs.getInt("letterSp", 0);   // 0.00 em
        margin = prefs.getInt("margin", 24);      // dp
        textColor = prefs.getInt("textColor", Color.WHITE);
        bgColor = prefs.getInt("bgColor", Color.BLACK);
        bold = prefs.getBoolean("bold", true);
        center = prefs.getBoolean("center", false);
    }

    private void saveSettings() {
        prefs.edit()
                .putInt(MainActivity.K_FONT, fontSize)
                .putInt(MainActivity.K_SPEED, speed)
                .putBoolean(MainActivity.K_MIRROR, mirror)
                .putInt("lineSp", lineSp).putInt("letterSp", letterSp)
                .putInt("margin", margin)
                .putInt("textColor", textColor).putInt("bgColor", bgColor)
                .putBoolean("bold", bold).putBoolean("center", center)
                .apply();
    }

    // ================= Top bar =================

    private void buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.argb(200, 15, 17, 26));
        topBar.setPadding(dp(8), dp(6), dp(8), dp(6));

        topBar.addView(iconBtn("←", v -> finish()));
        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        topBar.addView(iconBtn("⏮", v -> { scroll.scrollTo(0, 0); pos = 0; showStatus("Shurute"); }));
        topBar.addView(iconBtn("✎", v -> editScript()));
        topBar.addView(iconBtn("⚙", v -> togglePanel()));
        playBtn = iconBtn("▶", v -> togglePlay());
        playBtn.setBackground(round(Color.rgb(255, 196, 0), dp(24)));
        playBtn.setTextColor(Color.rgb(20, 24, 38));
        topBar.addView(playBtn);

        root.addView(topBar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    }

    private TextView iconBtn(String label, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(22);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(Color.argb(60, 255, 255, 255), dp(24)));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.leftMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    // ================= Settings panel =================

    private void buildPanel() {
        panel = new ScrollView(this);
        panel.setBackground(round(Color.argb(240, 22, 25, 36), dp(18)));
        panel.setVisibility(View.GONE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(18));
        panel.addView(box);

        TextView head = new TextView(this);
        head.setText("⚙ Settings  —  live preview");
        head.setTextColor(Color.rgb(255, 196, 0));
        head.setTextSize(15);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(head);

        slider(box, "Font size", 16, 120, fontSize, v -> v + "", v -> { fontSize = v; applyStyle(true); });
        slider(box, "Line spacing", 8, 30, lineSp, v -> String.format("%.1fx", v / 10f),
                v -> { lineSp = v; applyStyle(true); });
        slider(box, "Letter spacing", 0, 40, letterSp, v -> String.format("%.2f", v / 100f),
                v -> { letterSp = v; applyStyle(true); });
        slider(box, "Speed", 1, 30, speed, v -> v + "", v -> { speed = v; saveSettings(); });
        slider(box, "Side margin", 0, 120, margin, v -> v + "dp", v -> { margin = v; applyStyle(true); });

        box.addView(sectionLabel("Text colour"));
        textSwatches = swatchRow(box, TEXT_COLORS, true);
        box.addView(sectionLabel("Background"));
        bgSwatches = swatchRow(box, BG_COLORS, false);

        LinearLayout toggles = new LinearLayout(this);
        toggles.setPadding(0, dp(14), 0, 0);
        mirrorBtn = toggle(toggles, "⇋ Mirror", v -> { mirror = !mirror; applyStyle(false); });
        boldBtn = toggle(toggles, "B Bold", v -> { bold = !bold; applyStyle(true); });
        alignBtn = toggle(toggles, "≡ Center", v -> { center = !center; applyStyle(false); });
        box.addView(toggles);

        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, (int) (screenH * 0.48f),
                Gravity.BOTTOM);
        pl.leftMargin = pl.rightMargin = dp(8);
        pl.bottomMargin = dp(8);
        root.addView(panel, pl);
    }

    interface Fmt { String f(int v); }

    private void slider(LinearLayout box, String name, int min, int max, int value, Fmt fmt, IntCb cb) {
        TextView lbl = sectionLabel(name + ":  " + fmt.f(value));
        box.addView(lbl);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                lbl.setText(name + ":  " + fmt.f(p + min));
                if (u) cb.on(p + min);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(bar);
    }

    private TextView sectionLabel(String t) {
        TextView l = new TextView(this);
        l.setText(t);
        l.setTextColor(Color.rgb(205, 210, 225));
        l.setTextSize(13);
        l.setPadding(0, dp(12), 0, dp(2));
        return l;
    }

    private LinearLayout swatchRow(LinearLayout box, int[] colors, boolean forText) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(4), 0, 0);
        for (int c : colors) {
            View sw = new View(this);
            sw.setTag(c);
            sw.setOnClickListener(v -> {
                if (forText) textColor = c; else bgColor = c;
                applyStyle(false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38));
            lp.rightMargin = dp(10);
            row.addView(sw, lp);
        }
        hs.addView(row);
        box.addView(hs);
        return row;
    }

    private TextView toggle(LinearLayout row, String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(10), dp(8), dp(10));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = dp(8);
        row.addView(t, lp);
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void togglePanel() {
        boolean show = panel.getVisibility() != View.VISIBLE;
        panel.setVisibility(show ? View.VISIBLE : View.GONE);
        showBar();
    }

    // ================= Live styling =================

    private void applyStyle(boolean keepPosition) {
        int oldMax = Math.max(1, text.getHeight() - scroll.getHeight());
        float ratio = scroll.getScrollY() / (float) oldMax;

        root.setBackgroundColor(bgColor);
        text.setTextColor(textColor);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
        text.setLineSpacing(0, lineSp / 10f);
        text.setLetterSpacing(letterSp / 100f);
        text.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        text.setGravity(center ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        text.setPadding(dp(margin), screenH / 3, dp(margin), screenH);
        scroll.setScaleX(mirror ? -1f : 1f);

        // swatch highlight
        markSwatches(textSwatches, textColor);
        markSwatches(bgSwatches, bgColor);
        styleToggle(mirrorBtn, mirror);
        styleToggle(boldBtn, bold);
        styleToggle(alignBtn, center);

        saveSettings();

        if (keepPosition) {
            scroll.post(() -> {
                int newMax = Math.max(0, text.getHeight() - scroll.getHeight());
                int y = (int) (ratio * newMax);
                scroll.scrollTo(0, y);
                pos = y;
            });
        }
    }

    private void markSwatches(LinearLayout row, int selected) {
        if (row == null) return;
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            int c = (Integer) v.getTag();
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(c);
            d.setStroke(c == selected ? dp(4) : dp(1),
                    c == selected ? Color.rgb(255, 196, 0) : Color.rgb(90, 95, 110));
            v.setBackground(d);
        }
    }

    private void styleToggle(TextView t, boolean on) {
        if (t == null) return;
        t.setBackground(round(on ? Color.rgb(255, 196, 0) : Color.argb(50, 255, 255, 255), dp(10)));
        t.setTextColor(on ? Color.rgb(20, 24, 38) : Color.WHITE);
    }

    // ================= Edit script =================

    private void editScript() {
        boolean wasPlaying = playing;
        if (playing) togglePlay();
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setMinLines(6);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setText(prefs.getString(MainActivity.K_SCRIPT, ""));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Script edit")
                .setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    String s = et.getText().toString();
                    prefs.edit().putString(MainActivity.K_SCRIPT, s).apply();
                    text.setText(s);
                    applyStyle(true);
                    if (wasPlaying) togglePlay();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ================= Playback =================

    private void togglePlay() {
        playing = !playing;
        playBtn.setText(playing ? "❚❚" : "▶");
        if (playing) {
            pos = scroll.getScrollY();
            lastFrame = 0;
            Choreographer.getInstance().postFrameCallback(this);
            showStatus("▶ Playing  •  Speed " + speed);
        } else {
            Choreographer.getInstance().removeFrameCallback(this);
            showStatus("❚❚ Paused");
        }
        showBar();
    }

    private void showBar() {
        topBar.removeCallbacks(hideBar);
        topBar.animate().cancel();
        topBar.setVisibility(View.VISIBLE);
        topBar.setAlpha(1f);
        if (playing && panel.getVisibility() != View.VISIBLE) topBar.postDelayed(hideBar, 2500);
    }

    private void changeSpeed(int delta) {
        speed = Math.max(1, Math.min(30, speed + delta));
        saveSettings();
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
            if (Math.abs(scroll.getScrollY() - (int) pos) > dp(4)) pos = scroll.getScrollY();
            float pxPerSec = speed * 10f * getResources().getDisplayMetrics().density;
            pos += pxPerSec * dt;
            int max = Math.max(0, text.getHeight() - scroll.getHeight());
            if (pos >= max) {
                pos = max;
                scroll.scrollTo(0, max);
                playing = false;
                playBtn.setText("▶");
                showBar();
                showStatus("Shesh ✓");
                return;
            }
            scroll.scrollTo(0, (int) pos);
        }
        lastFrame = frameTimeNanos;
        Choreographer.getInstance().postFrameCallback(this);
    }

    // ================= Remote keys =================

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
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playing) togglePlay();
        saveSettings();
    }
}
