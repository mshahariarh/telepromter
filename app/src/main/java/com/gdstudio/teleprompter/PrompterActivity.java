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
        implements Choreographer.FrameCallback, InputManager.InputDeviceListener, VoiceScroller.Target {

    // ---- settings (saved, all live) ----
    private int fontSize, lineSp, letterSp, speed, margin, textColor, bgColor, countdown;
    private boolean mirror, bold, center, loop, landscape;
    private boolean focus, dblTop;
    private String fontFace;

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
    private TextView remainTv, fontBtn, focusBtn, langBtn;
    private ImageView micIv;
    private View focusTop, focusBottom;
    private VoiceScroller voice;
    private final java.util.ArrayList<Integer> pauseOffsets = new java.util.ArrayList<>();
    private long lastPlayPress = 0;
    private float prevPos = 0f;
    private int readColor, readEnd = -1, lastVoiceY = -1;
    private android.text.style.ForegroundColorSpan readSpan;
    private android.animation.ValueAnimator scrollAnim;
    private LinearLayout readRow;
    private RemoteNav nav;
    private static final int[] READ_COLORS = {0, Color.rgb(120, 120, 130), Color.rgb(255, 214, 0),
            Color.rgb(0, 230, 118), Color.rgb(0, 229, 255), Color.rgb(255, 145, 0), Color.rgb(229, 57, 53)};

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

        focusTop = new View(this);
        focusBottom = new View(this);
        content.addView(focusTop, new FrameLayout.LayoutParams(-1, 0, Gravity.TOP));
        content.addView(focusBottom, new FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM));
        voice = new VoiceScroller(this, this);

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
                    layoutFocus(h);
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
            int am = e.getActionMasked();
            if (am == MotionEvent.ACTION_DOWN && scrollAnim != null) scrollAnim.cancel();
            if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL)
                scroll.postDelayed(this::resyncVoice, 700);
            return false;
        });
        scroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (voice == null || !voice.isActive()) markReadAtGuide();
        });

        setContentView(root);
        nav = new RemoteNav(this);
        nav.setDefault(playIv);
        loadText();
        applyStyle(false);
        updateToggles();
        updateTimer();
        showStatus("Ready — press ▶ or use the remote");
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
        countdown = prefs.getInt("countdown2", 0); // off by default
        focus = prefs.getBoolean("focus", false);
        dblTop = prefs.getBoolean("dbl_top", false);
        fontFace = prefs.getString("font_face", Fonts.SYSTEM);
        readColor = prefs.getInt("readColor", 0);
    }

    private void saveSettings() {
        prefs.edit()
                .putInt(MainActivity.K_FONT, fontSize).putInt(MainActivity.K_SPEED, speed)
                .putBoolean(MainActivity.K_MIRROR, mirror)
                .putInt("lineSp", lineSp).putInt("letterSp", letterSp).putInt("margin", margin)
                .putInt("textColor", textColor).putInt("bgColor", bgColor)
                .putBoolean("bold", bold).putBoolean("center", center)
                .putBoolean("loop", loop).putBoolean("landscape", landscape)
                .putInt("countdown2", countdown)
                .putBoolean("focus", focus).putString("font_face", fontFace)
                .putInt("readColor", readColor)
                .apply();
    }

    private void loadText() {
        String body = script == null ? "" : script.text;
        if (body == null || body.trim().isEmpty()) body = "(Script is empty — tap ✎ above to write)";
        readEnd = -1;
        lastVoiceY = -1;
        readSpan = null;
        text.setText(markup(body), TextView.BufferType.SPANNABLE);
        voice.setText(body);
        titleTv.setText(script == null ? "Teleprompter" : script.title);
        scroll.post(this::updateRemain);
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
        remainTv = new TextView(this);
        remainTv.setTextColor(Color.rgb(170, 175, 190));
        remainTv.setTextSize(12);
        remainTv.setPadding(dp(6), 0, dp(2), 0);
        bar.addView(remainTv);
        micIv = barIcon(R.drawable.ic_mic, v -> toggleVoice());
        bar.addView(micIv);
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
            showStatus(loop ? "Loop ON — restarts at the end" : "Loop OFF");
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
        LinearLayout fontRow = new LinearLayout(this);
        fontRow.setPadding(0, dp(8), 0, 0);
        fontBtn = chip(fontRow, "Font: " + Fonts.label(fontFace), v -> pickFont());
        box.addView(fontRow);
        box.addView(label("Quick theme"));
        HorizontalScrollView ths = new HorizontalScrollView(this);
        ths.setHorizontalScrollBarEnabled(false);
        LinearLayout themes = new LinearLayout(this);
        int[][] presets = {{Color.WHITE, Color.BLACK}, {Color.rgb(229, 57, 53), Color.WHITE},
                {Color.rgb(255, 214, 0), Color.BLACK}, {Color.WHITE, Color.rgb(13, 27, 62)},
                {Color.rgb(25, 25, 25), Color.WHITE}, {Color.rgb(0, 230, 118), Color.BLACK}};
        for (int[] pr : presets) {
            TextView t = new TextView(this);
            t.setText("Aa");
            t.setTextSize(18);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(pr[0]);
            GradientDrawable d = new GradientDrawable();
            d.setColor(pr[1]);
            d.setCornerRadius(dp(10));
            d.setStroke(dp(1), Color.rgb(90, 95, 110));
            t.setBackground(d);
            t.setOnClickListener(v -> { textColor = pr[0]; bgColor = pr[1]; applyStyle(false); });
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(56), dp(44));
            tlp.rightMargin = dp(10);
            themes.addView(t, tlp);
        }
        ths.addView(themes);
        box.addView(ths);
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
        box.addView(label("Read text colour"));
        HorizontalScrollView rhs = new HorizontalScrollView(this);
        rhs.setHorizontalScrollBarEnabled(false);
        readRow = new LinearLayout(this);
        readRow.setPadding(0, dp(4), 0, 0);
        for (int c : READ_COLORS) {
            TextView sw = new TextView(this);
            sw.setTag(c);
            sw.setGravity(Gravity.CENTER);
            sw.setTextSize(11);
            sw.setTextColor(Color.WHITE);
            if (c == 0) sw.setText("Auto");
            sw.setOnClickListener(v -> { readColor = c; applyStyle(false); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(c == 0 ? dp(52) : dp(38), dp(38));
            lp.rightMargin = dp(10);
            readRow.addView(sw, lp);
        }
        rhs.addView(readRow);
        box.addView(rhs);

        LinearLayout toggles = new LinearLayout(this);
        toggles.setPadding(0, dp(14), 0, 0);
        boldBtn = chip(toggles, "B  Bold", v -> { bold = !bold; applyStyle(true); });
        alignBtn = chip(toggles, "≡  Center", v -> { center = !center; applyStyle(false); });
        focusBtn = chip(toggles, "◐  Focus", v -> { focus = !focus; applyStyle(false); });
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
                    updateRemain();
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        row.addView(speedBar, new LinearLayout.LayoutParams(0, -2, 4f));
        TextView plus = chip(row, "+", v -> changeSpeed(1));
        plus.setTextSize(22);
        speedPanel.addView(row);

        speedPanel.addView(label("🎤 Voice scroll — script follows your voice"));
        LinearLayout vrow = new LinearLayout(this);
        chip(vrow, "🎤  On / Off", v -> toggleVoice());
        langBtn = chip(vrow, langLabel(), v -> {
            String l = prefs.getString("voice_lang", "bn-BD").equals("bn-BD") ? "en-US" : "bn-BD";
            prefs.edit().putString("voice_lang", l).apply();
            langBtn.setText(langLabel());
            if (voice.isActive()) voice.start(l);
        });
        speedPanel.addView(vrow);

        slider(speedPanel, "Countdown (before play)", 0, 10, countdown,
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
        text.setTypeface(Fonts.get(this, fontFace), bold ? Typeface.BOLD : Typeface.NORMAL);
        text.setGravity(center ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        int h = content.getHeight() > 0 ? content.getHeight()
                : getResources().getDisplayMetrics().heightPixels;
        text.setPadding(dp(margin), guideY > 0 ? guideY : h / 3, dp(margin), h);
        scroll.setScaleX(mirror ? -1f : 1f);
        layoutFocus(h);

        markSwatches(textSwatches, textColor);
        markSwatches(bgSwatches, bgColor);
        markReadRow();
        int re = readEnd;
        readEnd = -1;
        if (re >= 0) markRead(re);
        chipState(boldBtn, bold);
        chipState(alignBtn, center);
        chipState(focusBtn, focus);
        if (fontBtn != null) fontBtn.setText("Font: " + Fonts.label(fontFace));
        saveSettings();
        scroll.post(this::updateRemain);

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
        if (voice != null && voice.isActive()) voice.stop();
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
        resyncVoice();
    }

    private void toEnd() {
        int max = Math.max(0, text.getHeight() - scroll.getHeight());
        scroll.scrollTo(0, max);
        pos = max;
        resyncVoice();
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
        updateRemain();
        showStatus("Speed " + speed + (playing ? "" : "  (Paused)"));
    }

    private void jump(int direction) {
        int step = (int) (scroll.getHeight() * 0.35f) * direction;
        if (scrollAnim != null) scrollAnim.cancel();
        scroll.scrollBy(0, step);
        pos = scroll.getScrollY();
        resyncVoice();
        showStatus(direction > 0 ? "▼  Forward" : "▲  Back");
    }

    private void updateTimer() {
        long s = elapsedMs / 1000;
        if (s == shownSec) return;
        shownSec = s;
        timerTv.setText(String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        updateRemain();
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
            prevPos = pos;
            pos += speed * 10f * getResources().getDisplayMetrics().density * dt;
            int max = Math.max(0, text.getHeight() - scroll.getHeight());
            int stopAt = pauseLine(prevPos, pos);
            if (stopAt >= 0) {
                pos = stopAt;
                scroll.scrollTo(0, stopAt);
                setPlaying(false);
                showStatus("⏸  PAUSE — press play");
                return;
            }
            if (pos >= max) {
                if (loop) {
                    pos = 0;
                    scroll.scrollTo(0, 0);
                    showStatus("↻  Restarting");
                } else {
                    scroll.scrollTo(0, max);
                    setPlaying(false);
                    showStatus("End ✓");
                    return;
                }
            } else {
                scroll.scrollTo(0, (int) pos);
            }
        }
        markReadAtGuide();
        lastFrame = frameTimeNanos;
        Choreographer.getInstance().postFrameCallback(this);
    }

    // ===================== v5: markers, reading time, focus, fonts, voice =====================

    /** [PAUSE] / [STOP] / *** lines stop the scroll; other [notes] are shown in orange. */
    private CharSequence markup(String s) {
        pauseOffsets.clear();
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(s);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[[^\\]\\n]{1,40}\\]").matcher(s);
        while (m.find()) {
            String tag = m.group().toUpperCase(Locale.ROOT);
            boolean pause = tag.equals("[PAUSE]") || tag.equals("[STOP]")
                    || tag.equals("[থামুন]") || tag.equals("[বিরতি]");
            if (pause) {
                sb.setSpan(new android.text.style.BackgroundColorSpan(Color.rgb(229, 57, 53)), m.start(), m.end(), 33);
                sb.setSpan(new android.text.style.ForegroundColorSpan(Color.WHITE), m.start(), m.end(), 33);
                pauseOffsets.add(m.start());
            } else {
                sb.setSpan(new android.text.style.ForegroundColorSpan(ACCENT), m.start(), m.end(), 33);
                sb.setSpan(new android.text.style.StyleSpan(Typeface.ITALIC), m.start(), m.end(), 33);
            }
        }
        m = java.util.regex.Pattern.compile("(?m)^[ \\t]*\\*{3,}[ \\t]*$").matcher(s);
        while (m.find()) {
            sb.setSpan(new android.text.style.BackgroundColorSpan(Color.rgb(229, 57, 53)), m.start(), m.end(), 33);
            pauseOffsets.add(m.start());
        }
        java.util.Collections.sort(pauseOffsets);
        return sb;
    }

    /** Scroll position of a pause marker crossed between two positions, or -1. */
    private int pauseLine(float prev, float now) {
        android.text.Layout l = text.getLayout();
        if (l == null || pauseOffsets.isEmpty()) return -1;
        for (int off : pauseOffsets) {
            int y = l.getLineTop(l.getLineForOffset(Math.min(off, text.length())));
            if (prev < y && now >= y) return y;
        }
        return -1;
    }

    private void updateRemain() {
        if (remainTv == null || text == null) return;
        int end = Math.max(0, text.getHeight() - scroll.getHeight() - guideY);
        float pxs = speed * 10f * getResources().getDisplayMetrics().density;
        long sec = (long) (Math.max(0, end - scroll.getScrollY()) / pxs);
        remainTv.setText(String.format(Locale.US, "⏱ %d:%02d", sec / 60, sec % 60));
    }

    private void layoutFocus(int h) {
        if (focusTop == null) return;
        focusTop.setVisibility(focus ? View.VISIBLE : View.GONE);
        focusBottom.setVisibility(focus ? View.VISIBLE : View.GONE);
        if (!focus || h <= 0) return;
        int lh = text.getLineHeight();
        int topH = Math.max(0, guideY - lh / 2);
        int botH = Math.max(0, h - (guideY + lh * 5 / 2));
        int strong = (bgColor & 0x00FFFFFF) | 0xE6000000, soft = (bgColor & 0x00FFFFFF) | 0x80000000;
        focusTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{strong, soft}));
        focusBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{soft, strong}));
        FrameLayout.LayoutParams a = (FrameLayout.LayoutParams) focusTop.getLayoutParams();
        a.height = topH;
        focusTop.setLayoutParams(a);
        FrameLayout.LayoutParams b = (FrameLayout.LayoutParams) focusBottom.getLayoutParams();
        b.height = botH;
        focusBottom.setLayoutParams(b);
    }

    private void pickFont() {
        String[] fonts = Fonts.list(this);
        String[] labels = new String[fonts.length];
        for (int i = 0; i < fonts.length; i++) labels[i] = Fonts.label(fonts[i]);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Font")
                .setItems(labels, (d, w) -> {
                    fontFace = fonts[w];
                    applyStyle(true);
                })
                .show();
    }

    private String langLabel() {
        return prefs.getString("voice_lang", "bn-BD").equals("bn-BD") ? "Language: Bangla" : "Language: English";
    }

    private void toggleVoice() {
        if (voice.isActive()) {
            voice.stop();
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 11);
            return;
        }
        if (playing) setPlaying(false);
        android.text.Layout l = text.getLayout();
        if (l != null)
            voice.setPositionFromOffset(l.getLineStart(l.getLineForVertical(scroll.getScrollY())));
        voice.start(prefs.getString("voice_lang", "bn-BD"));
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code != 11) return;
        if (r.length > 0 && r[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) toggleVoice();
        else showStatus("Voice scroll needs microphone permission");
    }

    @Override
    public void voiceGoTo(int off) {
        android.text.Layout l = text.getLayout();
        if (l == null) return;
        int n = text.length();
        off = Math.min(off, n);
        // colour everything already read, up to the end of the spoken word
        int end = off;
        while (end < n && VoiceScroller.isWordChar(text.getText().charAt(end))) end++;
        markRead(end);
        int y = l.getLineTop(l.getLineForOffset(off));
        if (y != lastVoiceY) {
            lastVoiceY = y;
            smoothTo(y);
        }
    }

    /** Gentle, eased scroll so the eyes can follow (no jumps). */
    private void smoothTo(int y) {
        if (scrollAnim != null) scrollAnim.cancel();
        int from = scroll.getScrollY();
        int dist = Math.abs(y - from);
        if (dist == 0) return;
        scrollAnim = android.animation.ValueAnimator.ofInt(from, y);
        scrollAnim.setDuration(Math.max(450, Math.min(1100, dist * 3)));
        scrollAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        scrollAnim.addUpdateListener(a -> {
            int v = (Integer) a.getAnimatedValue();
            scroll.scrollTo(0, v);
            pos = v;
        });
        scrollAnim.start();
        updateRemain();
    }

    /** Colour text from the start up to 'end' with the "already read" colour. */
    private void markRead(int end) {
        if (end == readEnd || !(text.getText() instanceof android.text.Spannable)) return;
        android.text.Spannable sp = (android.text.Spannable) text.getText();
        if (readSpan != null) sp.removeSpan(readSpan);
        readSpan = null;
        readEnd = end;
        end = Math.min(end, sp.length());
        if (end <= 0) return;
        int c = readColor != 0 ? readColor : mix(textColor, bgColor, 0.6f);
        readSpan = new android.text.style.ForegroundColorSpan(c);
        sp.setSpan(readSpan, 0, end, 33);
    }

    /** During normal scrolling: lines above the guide line count as read. */
    private void markReadAtGuide() {
        android.text.Layout l = text.getLayout();
        if (l == null) return;
        markRead(l.getLineStart(l.getLineForVertical(Math.max(0, scroll.getScrollY()))));
    }

    /** After manual scrolling / jumps, voice matching restarts from the line at the guide. */
    private void resyncVoice() {
        android.text.Layout l = text.getLayout();
        if (l == null) return;
        int start = l.getLineStart(l.getLineForVertical(Math.max(0, scroll.getScrollY())));
        markRead(start);
        lastVoiceY = -1;
        if (voice != null && voice.isActive()) voice.setPositionFromOffset(start);
    }

    private static int mix(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private void markReadRow() {
        if (readRow == null) return;
        for (int i = 0; i < readRow.getChildCount(); i++) {
            View v = readRow.getChildAt(i);
            int c = (Integer) v.getTag();
            GradientDrawable d = new GradientDrawable();
            if (c == 0) {
                d.setCornerRadius(dp(19));
                d.setColor(mix(textColor, bgColor, 0.6f));
            } else {
                d.setShape(GradientDrawable.OVAL);
                d.setColor(c);
            }
            d.setStroke(c == readColor ? dp(4) : dp(1), c == readColor ? ACCENT : Color.rgb(90, 95, 110));
            v.setBackground(d);
        }
    }

    @Override
    public void voiceState(String s, boolean on) {
        showStatus(s);
        activeBg(micIv, on);
    }

    // ===================== remote =====================

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (nav != null && nav.onKey(event)) return true;
        int action = Keys.actionFor(this, event.getKeyCode());
        if (action == Keys.NONE) {
            // show unknown remote buttons so the user can see the signal arrives
            InputDevice dev = event.getDevice();
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                    && dev != null && !dev.isVirtual()
                    && event.getKeyCode() != KeyEvent.KEYCODE_BACK
                    && event.getKeyCode() != KeyEvent.KEYCODE_HOME)
                showStatus("Remote button: " + Keys.keyName(event.getKeyCode())
                        + " — set it in Bluetooth icon → Button set");
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) doAction(action, event.getRepeatCount() == 0);
        return true;
    }

    /** One place for every remote/keyboard action. */
    void doAction(int action, boolean first) {
        switch (action) {
            case Keys.PLAY:
                if (!first) break;
                long now = System.currentTimeMillis();
                if (dblTop && now - lastPlayPress < 450) {
                    lastPlayPress = 0;
                    if (countLeft > 0) togglePlay();
                    if (playing) setPlaying(false);
                    toTop();
                    showStatus("⏮  Double press — back to start");
                } else {
                    lastPlayPress = now;
                    togglePlay();
                }
                break;
            case Keys.BACK: jump(-1); break;
            case Keys.FWD: jump(1); break;
            case Keys.FASTER: changeSpeed(1); break;
            case Keys.SLOWER: changeSpeed(-1); break;
            case Keys.EXIT: if (first) finish(); break;
            case Keys.TOP:
                if (first) { toTop(); showStatus("⏮  Start"); }
                break;
            case Keys.FONT_UP:
                fontSize = Math.min(140, fontSize + 4);
                applyStyle(true);
                showStatus("Font " + fontSize);
                break;
            case Keys.FONT_DN:
                fontSize = Math.max(14, fontSize - 4);
                applyStyle(true);
                showStatus("Font " + fontSize);
                break;
            case Keys.MIRROR:
                if (first) {
                    mirror = !mirror;
                    applyStyle(false);
                    updateToggles();
                    showStatus(mirror ? "Mirror ON" : "Mirror OFF");
                }
                break;
            case Keys.LOOP:
                if (first) {
                    loop = !loop;
                    saveSettings();
                    updateToggles();
                    showStatus(loop ? "Loop ON" : "Loop OFF");
                }
                break;
            case Keys.SELECT:
                if (first) {
                    if (nav.isActive()) nav.select();
                    else nav.start();
                }
                break;
        }
    }

    /** Joystick on the remote: move the menu highlight. */
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (nav != null && nav.onMotion(ev)) return true;
        return super.dispatchGenericMotionEvent(ev);
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
        if (voice != null) voice.stop();
        if (playing) setPlaying(false);
        handler.removeCallbacks(tick);
        countLeft = 0;
        countTv.setVisibility(View.GONE);
        saveSettings();
    }
}
