package com.gdstudio.teleprompter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Remote connect status (live), pairing shortcut, button test and button learning. */
public class RemoteActivity extends Activity implements InputManager.InputDeviceListener {

    private LinearLayout devList;
    private TextView testTv;
    private final TextView[] keyLabels = new TextView[6];
    private int learning = 0;
    private InputManager im;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(242, 242, 242));

        LinearLayout header = new LinearLayout(this);
        header.setBackgroundColor(Color.BLACK);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), 0, dp(4), 0);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView h = new TextView(this);
        h.setText("Remote connect");
        h.setTextColor(Color.WHITE);
        h.setTextSize(18);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(h);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(24));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 1. Status
        LinearLayout c1 = card(box, "Connected remote");
        devList = new LinearLayout(this);
        devList.setOrientation(LinearLayout.VERTICAL);
        c1.addView(devList);
        TextView pair = button("🔗  Notun remote pair korun", Color.rgb(255, 196, 0), Color.BLACK);
        pair.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (Exception ignored) {
            }
        });
        c1.addView(pair);
        c1.addView(note("1. Remote on kore pairing mode e din (sadharonoto button long-press)\n"
                + "2. Upore button chepe Bluetooth list theke remote er nam tap korun\n"
                + "3. Ekhane fire ashun — upore ✅ dekhabe\n\n"
                + "Ekbar pair korle porer bar remote on korlei nije nije connect hobe."));

        // 2. Test
        LinearLayout c2 = card(box, "Remote test");
        testTv = new TextView(this);
        testTv.setText("Remote er je kono button chapun…");
        testTv.setTextSize(18);
        testTv.setTextColor(Color.BLACK);
        testTv.setGravity(Gravity.CENTER);
        testTv.setPadding(dp(8), dp(22), dp(8), dp(22));
        GradientDrawable tb = new GradientDrawable();
        tb.setColor(Color.rgb(245, 245, 245));
        tb.setCornerRadius(dp(10));
        testTv.setBackground(tb);
        c2.addView(testTv);

        // 3. Learn buttons
        LinearLayout c3 = card(box, "Button set (optional)");
        c3.addView(note("Remote er kono button onno kaj korle: \"Set\" chepe remote er sei button ta chapun."));
        for (int a = Keys.PLAY; a <= Keys.SLOWER; a++) {
            final int action = a;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(6), 0, dp(6));
            TextView n = new TextView(this);
            n.setText(Keys.NAMES[a]);
            n.setTextColor(Color.BLACK);
            n.setTextSize(15);
            r.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
            keyLabels[a] = new TextView(this);
            keyLabels[a].setTextSize(12);
            keyLabels[a].setPadding(0, 0, dp(10), 0);
            r.addView(keyLabels[a]);
            TextView set = button("Set", Color.BLACK, Color.WHITE);
            set.setOnClickListener(v -> {
                learning = action;
                testTv.setText("Ekhon remote e \"" + Keys.NAMES[action] + "\" er button chapun…");
                refreshLabels();
            });
            r.addView(set, new LinearLayout.LayoutParams(dp(64), dp(38)));
            c3.addView(r);
        }
        TextView reset = button("Default e firun", Color.rgb(230, 230, 230), Color.BLACK);
        reset.setOnClickListener(v -> {
            Keys.reset(this);
            learning = 0;
            testTv.setText("Default button e fire geche");
            refreshLabels();
        });
        c3.addView(reset);

        setContentView(root);
        refreshLabels();
    }

    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.BLACK);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(10));
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        parent.addView(c, lp);
        return c;
    }

    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.rgb(100, 100, 100));
        t.setTextSize(13);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private TextView button(String label, int bg, int fg) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(fg);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(10));
        b.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private void refreshLabels() {
        for (int a = Keys.PLAY; a <= Keys.SLOWER; a++) {
            int k = Keys.custom(this, a);
            if (learning == a) {
                keyLabels[a].setText("chapun…");
                keyLabels[a].setTextColor(Color.rgb(230, 120, 0));
            } else {
                keyLabels[a].setText(k == -1 ? "Default" : Keys.keyName(k));
                keyLabels[a].setTextColor(k == -1 ? Color.GRAY : Color.rgb(0, 140, 70));
            }
        }
    }

    private void refreshDevices() {
        devList.removeAllViews();
        List<String> names = Keys.remotes();
        if (names.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("❌  Kono remote connect nei");
            t.setTextColor(Color.rgb(200, 40, 40));
            t.setTextSize(15);
            devList.addView(t);
        } else {
            for (String n : names) {
                TextView t = new TextView(this);
                t.setText("✅  " + n);
                t.setTextColor(Color.rgb(0, 140, 70));
                t.setTextSize(15);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setPadding(0, dp(2), 0, dp(2));
                devList.addView(t);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            if (learning > 0) {
                Keys.assign(this, learning, code);
                testTv.setText("✓  " + Keys.keyName(code) + "  →  " + Keys.NAMES[learning]);
                learning = 0;
                refreshLabels();
            } else {
                int a = Keys.actionFor(this, code);
                testTv.setText("Button: " + Keys.keyName(code) + "\n→  "
                        + (a == Keys.NONE ? "(kono kaj nei — Set diye din)" : Keys.NAMES[a]));
            }
            refreshDevices();
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        im = (InputManager) getSystemService(INPUT_SERVICE);
        im.registerInputDeviceListener(this, null);
        refreshDevices();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (im != null) im.unregisterInputDeviceListener(this);
    }

    @Override public void onInputDeviceAdded(int id) { refreshDevices(); }
    @Override public void onInputDeviceRemoved(int id) { refreshDevices(); }
    @Override public void onInputDeviceChanged(int id) { refreshDevices(); }
}
