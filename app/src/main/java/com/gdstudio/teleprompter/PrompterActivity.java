package com.gdstudio.teleprompter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public class PrompterActivity extends Activity
        implements Choreographer.FrameCallback, InputManager.InputDeviceListener {

    // ---- settings (saved, all live) ----
    private int fontSize, lineSp, letterSp, speed, margin, textColor, bgColor, countdown;
    private boolean mirror, bold, center, loop, landscape;

    private static final int[] TEXT_COLORS = {
            Color.WHITE, Color.rgb(229, 57, 53), Color.rgb(255, 214, 0), Color.rgb(0, 230, 118),
            Color.rgb(0, 229, 255), Color.rgb(255, 128, 171), Color.rgb(255, 145, 0),
            Color.rgb(25, 25, 25)};
    private static final int[] BG_COLORS = {
            Color.BLACK, Color.rgb(28, 28, 32), Color.rgb(13, 27, 62), Color.rgb(0, 50, 30),
            Color.rgb(240, 240, 240), Color.WHITE};
    private static final int ACCENT = Color.rgb(255, 152, 0);

    private SharedPreferences prefs;
    private Store.Script script;
    private InputManager im;

    private FrameLayout content;
    private ScrollView scroll, textPanel;
    private LinearLayout speedPanel, textSwatches, bgSwatches;
    private TextView text, status, timerTv, titleTv, countTv, boldBtn, alignBtn;
    private View guide;
    private ImageView playIv, textIv, speedIv, loopIv, rotateIv, mirrorIv;
    private SeekBar speedBar;
    private TextView speedLabel;

    private int panel = 0; // 0 none, 1 text, 2 speed
    private boolean playing = false;
    private float pos = 0f;
    private long lastFrame = 0, elapsedMs = 0, shownSec = -1;
    private int guideY = 0, countLeft = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable hideStatus = () -> status.animate().alpha(0f).setDuration(400);
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            countLeft--;
            if (countLeft <= 0) {
                countTv.setVisibility(View.GONE);
                setPlaying(true);
            } else {
                countTv.setText(String.valueOf(countLeft));
                handler.postDelayed(this, 1000);
            }
        }
    };

    interface IntCb { void on(int v); }
    interface Fmt { String f(int v); }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    // ===================== setup =====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = Store.prefs(this);
        loadSettings();
        String id = getIntent().getStringExtra("id");
        script = id == null ? null : Store.get(this, id);
        if (landscape) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(52)));
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(buildBottomBar(), new LinearLayout.LayoutParams(-1, dp(58)));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        text = new TextView(this);
        scroll.addView(text, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        guide = new View(this);
        guide.setBackgroundColor(Color.argb(170, 255, 152, 0));
        content.addView(guide, new FrameLayout.LayoutParams(-1, dp(2)));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        status.setBackground(round(Color.argb(200, 30, 30, 30), dp(20)));
        status.setPadding(dp(16), dp(8), dp(16), dp(8));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        sl.topMargin = dp(10);
        content.addView(status, sl);

        countTv = new TextView(this);
        countTv.setTextSize(120);
        countTv.setTypeface(Typeface.DEFAULT_BOLD);
        countTv.setTextColor(ACCENT);
        countTv.setShadowLayer(12, 0, 0, Color.BLACK);
        countTv.setVisibility(View.GONE);
        content.addView(countTv, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        buildTextPanel();
        buildSpeedPanel();

        content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int ny = (int) (h * 0.3f);
            if (ny != guideY) {
                guideY = ny;
                v.post(() -> {
                    FrameLayout.LayoutParams gl = (FrameLayout.LayoutParams) guide.getLayoutParams();
                    gl.topMargin = guideY;
                    guide.setLayoutParams(gl);
                    FrameLayout.LayoutParams pl = (FrameLayout.LayoutParams) textPanel.getLayoutParams();
                    pl.height = Math.min(dp(360), (int) (h * 0.62f));
                    textPanel.setLayoutParams(pl);
                    applyStyle(true);
                });
            }
        });

        GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (panel != 0) showPanel(panel);
                else togglePlay();
                return true;
            }
        });
        scroll.setOnTouchListener((v, e) -> {
            gd.onTouchEvent(e);
            return false;
        });

        setContentView(root);
        loadText();
        applyStyle(false);
        updateToggles();
        updateTimer();
        showStatus("Ready — ▶ chapun ba remote use korun");
    }

    private void loadSettings() {
        fontSize = prefs.getInt(MainActivity.K_FONT, 42);
        speed = prefs.getInt(MainActivity.K_SPEED, 5);
        mirror = prefs.getBoolean(MainActivity.K_MIRROR, false);
        lineSp = prefs.getInt("lineSp", 13);
        letterSp = prefs.getInt("letterSp", 0);
        margin = prefs.getInt("margin", 20);
        textColor = prefs.getInt("textColor", Color.WHITE);
        bgColor = prefs.getInt("bgColor", Color.BLACK);
        bold = prefs.getBoolean("bold", false);
        center = prefs.getBoolean("center", false);
        loop = prefs.getBoolean("loop", false);
        landscape = prefs.getBoolean("landscape", false);
        countdown = prefs.getInt("countdown", 3);
    }

    private void saveSettings() {
        prefs.edit()
                .putInt(MainActivity.K_FONT, fontSize).putInt(MainActivity.K_SPEED, speed)
                .putBoolean(MainActivity.K_MIRROR, mirror)
                .putInt("lineSp", lineSp).putInt("letterSp", letterSp).putInt("margin", margin)
                .putInt("textColor", textColor).putInt("bgColor", bgColor)
                .putBoolean("bold", bold).putBoolean("center", center)
                .putBoolean("loop", loop).putBoolean("landscape", landscape)
                .putInt("countdown", countdown)
                .apply();
    }

    private void loadText() {
        String body = script == null ? "" : script.text;
        text.setText(body == null || body.trim().isEmpty()
                ? "(Script khali — upore ✎ chepe likhun)" : body);
        titleTv.setText(script == null ? "Teleprompter" : script.title);
    }

    // ===================== bars =====================

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(Color.BLACK);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(4), 0);
        bar.addView(barIcon(R.drawable.ic_back, v -> finish()));
        titleTv = new TextView(this);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(16);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(titleTv, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(barIcon(R.drawable.ic_bluetooth,
                v -> startActivity(new Intent(this, RemoteActivity.class))));
        bar.addView(barIcon(R.drawable.ic_edit, v -> {
            if (script != null)
                startActivity(new Intent(this, EditorActivity.class).putExtra("id", script.id));
        }));
        return bar;
    }

    private ImageView barIcon(int res, View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setPadding(dp(11), dp(11), dp(11), dp(11));
        iv.setOnClickListener(l);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
        return iv;
    }

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(Color.BLACK);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(4), 0);

        LinearLayout tbox = new LinearLayout(this);
        tbox.setGravity(Gravity.CENTER);
        timerTv = new TextView(this);
        timerTv.setTextColor(Color.WHITE);
        timerTv.setTextSize(12);
        timerTv.setTypeface(Typeface.MONOSPACE);
        tbox.addView(timerTv);
        ImageView reset = new ImageView(this);
        reset.setImageResource(R.drawable.ic_replay);
        reset.setPadding(dp(5), dp(5), dp(5), dp(5));
        reset.setOnClickListener(v -> resetAll());
        tbox.addView(reset, new LinearLayout.LayoutParams(dp(30), dp(30)));
        bar.addView(tbox, new LinearLayout.LayoutParams(0, -1, 2.1f));

        playIv = tool(bar, R.drawable.ic_play, v -> togglePlay());
        textIv = tool(bar, R.drawable.ic_text, v -> showPanel(1));
        speedIv = tool(bar, R.drawable.ic_speed, v -> showPanel(2));
        loopIv = tool(bar, R.drawable.ic_loop, v -> {
            loop = !loop;
            saveSettings();
            updateToggles();
            showStatus(loop ? "Loop ON — shesh hole abar shuru" : "Loop OFF");
        });
        ImageView up = tool(bar, R.drawable.ic_up, v -> jump(-1));
        up.setOnLongClickListener(v -> { toTop(); return true; });
        ImageView down = tool(bar, R.drawable.ic_down, v -> jump(1));
        down.setOnLongClickListener(v -> { toEnd(); return true; });
        rotateIv = tool(bar, R.drawable.ic_rotate, v -> toggleRotate());
        mirrorIv = tool(bar, R.drawable.ic_mirror, v -> {
            mirror = !mirror;
            applyStyle(false);
            updateToggles();
            showStatus(mirror ? "Mirror ON" : "Mirror OFF");
        });
        return bar;
    }

    private ImageView tool(LinearLayout bar, int res, View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setPadding(dp(7), dp(7), dp(7), dp(7));
        iv.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.leftMargin = lp.rightMargin = dp(2);
        bar.addView(iv, lp);
        return iv;
    }

    private void updateToggles() {
        activeBg(textIv, panel == 1);
        activeBg(speedIv, panel == 2);
        activeBg(loopIv, loop);
        activeBg(rotateIv, landscape);
        activeBg(mirrorIv, mirror);
        playIv.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void activeBg(View v, boolean on) {
        if (!on) {
            v.setBackground(null);
            return;
        }
        GradientDrawable d = new GradientDrawable();
        d.setStroke(dp(2), ACCENT);
        d.setCornerRadius(dp(6));
        v.setBackground(d);
    }

    // ===================== panels =====================

    private void showPanel(int n) {
        panel = (panel == n) ? 0 : n;
        textPanel.setVisibility(panel == 1 ? View.VISIBLE : View.GONE);
        speedPanel.setVisibility(panel == 2 ? View.VISIBLE : View.GONE);
        updateToggles();
    }

    private LinearLayout panelBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(10), dp(18), dp(16));
        return box;
    }

    private void buildTextPanel() {
        textPanel = new ScrollView(this);
        textPanel.setBackground(round(Color.argb(242, 20, 22, 30), dp(16)));
        textPanel.setVisibility(View.GONE);
        LinearLayout box = panelBox();
        textPanel.addView(box);

        box.addView(head("T  Text style — live"));
        slider(box, "Font size", 14, 140, fontSize, v -> v + "",
                v -> { fontSize = v; applyStyle(true); });
        slider(box, "Line spacing", 8, 30, lineSp, v -> String.format(Locale.US, "%.1fx", v / 10f),
                v -> { lineSp = v; applyStyle(true); });
        slider(box, "Letter spacing", 0, 40, letterSp, v -> String.format(Locale.US, "%.2f", v / 100f),
                v -> { letterSp = v; applyStyle(true); });
        slider(box, "Side margin", 0, 120, margin, v -> v + "dp",
                v -> { margin = v; applyStyle(true); });

        box.addView(label("Text colour"));
        textSwatches = swatchRow(box, TEXT_COLORS, true);
        box.addView(label("Background"));
        bgSwatches = swatchRow(box, BG_COLORS, false);

        LinearLayout toggles = new LinearLayout(this);
        toggles.setPadding(0, dp(14), 0, 0);
        boldBtn = chip(toggles, "B  Bold", v -> { bold = !bold; applyStyle(true); });
        alignBtn = chip(toggles, "≡  Center", v -> { center = !center; applyStyle(false); });
        box.addView(toggles);

        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, dp(340), Gravity.BOTTOM);
        pl.leftMargin = pl.rightMargin = pl.bottomMargin = dp(8);
        content.addView(textPanel, pl);
    }

    private void buildSpeedPanel() {
        speedPanel = panelBox();
        speedPanel.setBackground(round(Color.argb(242, 20, 22, 30), dp(16)));
        speedPanel.setVisibility(View.GONE);
        speedPanel.addView(head("Speed — live"));

        speedLabel = label("Speed: " + speed);
        speedLabel.setTextSize(15);
        speedPanel.addView(speedLabel);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = chip(row, "−", v -> changeSpeed(-1));
        minus.setTextSize(22);
        speedBar = new SeekBar(this);
        speedBar.setMax(29);
        speedBar.setProgress(speed - 1);
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                if (u) {
                    speed = p + 1;
                    speedLabel.setText("Speed: " + speed);
                    saveSettings();
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        row.addView(speedBar, new LinearLayout.LayoutParams(0, -2, 4f));
        TextView plus = chip(row, "+", v -> changeSpeed(1));
        plus.setTextSize(22);
        speedPanel.addView(row);

        slider(speedPanel, "Countdown (play er age)", 0, 10, countdown,
                v -> v == 0 ? "Off" : v + " sec", v -> { countdown = v; saveSettings(); });

        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        pl.leftMargin = pl.rightMargin = pl.bottomMargin = dp(8);
        content.addView(speedPanel, pl);
    }

    private TextView head(String t) {
        TextView h = new TextView(this);
        h.setText(t);
        h.setTextColor(ACCENT);
        h.setTextSize(15);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, dp(4));
        return h;
    }

    private TextView label(String t) {
        TextView l = new TextView(this);
        l.setText(t);
        l.setTextColor(Color.rgb(205, 210, 225));
        l.setTextSize(13);
        l.setPadding(0, dp(12), 0, dp(2));
        return l;
    }

    private void slider(LinearLayout box, String name, int min, int max, int value, Fmt fmt, IntCb cb) {
        TextView lbl = label(name + ":  " + fmt.f(value));
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

    private TextView chip(LinearLayout row, String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(8), dp(8), dp(8));
        t.setBackground(round(Color.argb(50, 255, 255, 255), dp(10)));
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

    // ===================== live styling =====================

    private void applyStyle(boolean keepPosition) {
        int oldMax = Math.max(1, text.getHeight() - scroll.getHeight());
        float ratio = scroll.getScrollY() / (float) oldMax;

        content.setBackgroundColor(bgColor);
        text.setTextColor(textColor);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
        text.setLineSpacing(0, lineSp / 10f);
        text.setLetterSpacing(letterSp / 100f);
        text.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        text.setGravity(center ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        int h = content.getHeight() > 0 ? content.getHeight()
                : getResources().getDisplayMetrics().heightPixels;
        text.setPadding(dp(margin), guideY > 0 ? guideY : h / 3, dp(margin), h);
        scroll.setScaleX(mirror ? -1f : 1f);

        markSwatches(textSwatches, textColor);
        markSwatches(bgSwatches, bgColor);
        chipState(boldBtn, bold);
        chipState(alignBtn, center);
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
                    c == selected ? ACCENT : Color.rgb(90, 95, 110));
            v.setBackground(d);
        }
    }

    private void chipState(TextView t, boolean on) {
        if (t == null) return;
        t.setBackground(round(on ? ACCENT : Color.argb(50, 255, 255, 255), dp(10)));
        t.setTextColor(on ? Color.BLACK : Color.WHITE);
    }

    // ===================== playback =====================

    private void togglePlay() {
        if (countLeft > 0) {
            handler.removeCallbacks(tick);
            countLeft = 0;
            countTv.setVisibility(View.GONE);
            showStatus("Countdown cancel");
            return;
        }
        if (!playing && countdown > 0) {
            countLeft = countdown;
            countTv.setText(String.valueOf(countLeft));
            countTv.setVisibility(View.VISIBLE);
            handler.postDelayed(tick, 1000);
            return;
        }
        setPlaying(!playing);
    }

    private void setPlaying(boolean p) {
        playing = p;
        if (p) {
            pos = scroll.getScrollY();
            lastFrame = 0;
            Choreographer.getInstance().postFrameCallback(this);
            showStatus("▶  Speed " + speed);
        } else {
            Choreographer.getInstance().removeFrameCallback(this);
            showStatus("❚❚  Paused");
        }
        updateToggles();
    }

    private void resetAll() {
        if (playing) setPlaying(false);
        handler.removeCallbacks(tick);
        countLeft = 0;
        countTv.setVisibility(View.GONE);
        toTop();
        elapsedMs = 0;
        updateTimer();
        showStatus("Reset");
    }

    private void toTop() {
        scroll.scrollTo(0, 0);
        pos = 0;
    }

    private void toEnd() {
        int max = Math.max(0, text.getHeight() - scroll.getHeight());
        scroll.scrollTo(0, max);
        pos = max;
    }

    private void toggleRotate() {
        landscape = !landscape;
        saveSettings();
        setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        updateToggles();
    }

    private void changeSpeed(int delta) {
        speed = Math.max(1, Math.min(30, speed + delta));
        saveSettings();
        speedBar.setProgress(speed - 1);
        speedLabel.setText("Speed: " + speed);
        showStatus("Speed " + speed + (playing ? "" : "  (Paused)"));
    }

    private void jump(int direction) {
        int step = (int) (scroll.getHeight() * 0.35f) * direction;
        scroll.scrollBy(0, step);
        pos = scroll.getScrollY();
        showStatus(direction > 0 ? "▼  Samne" : "▲  Pichone");
    }

    private void updateTimer() {
        long s = elapsedMs / 1000;
        if (s == shownSec) return;
        shownSec = s;
        timerTv.setText(String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
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
            elapsedMs += (long) (dt * 1000);
            updateTimer();
            if (Math.abs(scroll.getScrollY() - (int) pos) > dp(4)) pos = scroll.getScrollY();
            pos += speed * 10f * getResources().getDisplayMetrics().density * dt;
            int max = Math.max(0, text.getHeight() - scroll.getHeight());
            if (pos >= max) {
                if (loop) {
                    pos = 0;
                    scroll.scrollTo(0, 0);
                    showStatus("↻  Abar shuru");
                } else {
                    scroll.scrollTo(0, max);
                    setPlaying(false);
                    showStatus("Shesh ✓");
                    return;
                }
            } else {
                scroll.scrollTo(0, (int) pos);
            }
        }
        lastFrame = frameTimeNanos;
        Choreographer.getInstance().postFrameCallback(this);
    }

    // ===================== remote =====================

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = Keys.actionFor(this, event.getKeyCode());
        if (action == Keys.NONE) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean first = event.getRepeatCount() == 0;
            switch (action) {
                case Keys.PLAY: if (first) togglePlay(); break;
                case Keys.BACK: jump(-1); break;
                case Keys.FWD: jump(1); break;
                case Keys.FASTER: changeSpeed(1); break;
                case Keys.SLOWER: changeSpeed(-1); break;
                case Keys.EXIT: finish(); break;
            }
        }
        return true;
    }

    @Override
    public void onInputDeviceAdded(int id) {
        InputDevice d = InputDevice.getDevice(id);
        if (d != null && Keys.remotes().contains(d.getName()))
            showStatus("🎮  Remote connected: " + d.getName());
    }

    @Override
    public void onInputDeviceRemoved(int id) {
        if (Keys.remotes().isEmpty()) showStatus("⚠  Remote disconnected");
    }

    @Override
    public void onInputDeviceChanged(int id) {}

    @Override
    protected void onResume() {
        super.onResume();
        im = (InputManager) getSystemService(INPUT_SERVICE);
        im.registerInputDeviceListener(this, null);
        if (script != null) {
            Store.Script fresh = Store.get(this, script.id);
            if (fresh != null && (!fresh.text.equals(script.text) || !fresh.title.equals(script.title))) {
                script = fresh;
                loadText();
                applyStyle(true);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (im != null) im.unregisterInputDeviceListener(this);
        if (playing) setPlaying(false);
        handler.removeCallbacks(tick);
        countLeft = 0;
        countTv.setVisibility(View.GONE);
        saveSettings();
    }
}
