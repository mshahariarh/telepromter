package com.gdstudio.teleprompter;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
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
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Remote connect: in-app BLE remotes (Feelworld etc.), system remotes, button test & learning. */
public class RemoteActivity extends Activity
        implements InputManager.InputDeviceListener, BleRemote.RawListener {

    private LinearLayout devList;
    private TextView testTv, bleStatus;
    private final TextView[] keyLabels = new TextView[Keys.LAST + 1];
    private int learning = 0;
    private InputManager im;
    private TextView wifiTv, wifiBtn;
    private ImageView qrIv;
    private BleRemote ble;

    private final List<String> foundAddr = new ArrayList<>();
    private final List<String> foundName = new ArrayList<>();
    private ArrayAdapter<String> foundAdapter;
    private AlertDialog scanDialog;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ble = BleRemote.get(this);

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

        // 1. In-app BLE remote (Feelworld etc.)
        LinearLayout c0 = card(box, "Feelworld / teleprompter remote");
        bleStatus = new TextView(this);
        bleStatus.setTextSize(15);
        bleStatus.setTypeface(Typeface.DEFAULT_BOLD);
        c0.addView(bleStatus);
        TextView scan = button("🔍  Find & connect remote", Color.rgb(255, 196, 0), Color.BLACK);
        scan.setOnClickListener(v -> startScanFlow());
        c0.addView(scan);
        TextView forget = button("Forget remote", Color.rgb(230, 230, 230), Color.BLACK);
        forget.setOnClickListener(v -> ble.forget());
        c0.addView(forget);
        c0.addView(note("1. Turn the remote on (LED lights up)\n"
                + "2. Tap the yellow button above → choose \"Feelworld-…\" from the list\n"
                + "3. Set each button once under \"Button set\" below\n\n"
                + "No need to pair in phone Bluetooth settings. After the first time, "
                + "the remote connects automatically when the app opens and reconnects if it drops."));

        // 2. Normal Bluetooth remotes / clickers
        LinearLayout c1 = card(box, "Other remotes / clickers (keyboard type)");
        devList = new LinearLayout(this);
        devList.setOrientation(LinearLayout.VERTICAL);
        c1.addView(devList);
        TextView pair = button("🔗  Phone Bluetooth settings", Color.rgb(230, 230, 230), Color.BLACK);
        pair.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (Exception ignored) {
            }
        });
        c1.addView(pair);
        c1.addView(note("Selfie remotes, presentation clickers and keyboards are paired in phone settings."));

        // Phone as remote (WiFi)
        LinearLayout cw = card(box, "📱 Use another phone as remote (WiFi)");
        wifiTv = new TextView(this);
        wifiTv.setTextSize(15);
        wifiTv.setTextColor(Color.BLACK);
        cw.addView(wifiTv);
        qrIv = new ImageView(this);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(dp(200), dp(200));
        qlp.gravity = Gravity.CENTER_HORIZONTAL;
        qlp.topMargin = dp(8);
        cw.addView(qrIv, qlp);
        wifiBtn = button("", Color.rgb(255, 196, 0), Color.BLACK);
        wifiBtn.setOnClickListener(v -> {
            if (RemoteServer.running()) RemoteServer.stop();
            else RemoteServer.start(this);
            wifiTv.postDelayed(this::refreshWifi, 500);
        });
        cw.addView(wifiBtn);
        cw.addView(note("Keep both phones on the same WiFi (or connect one to the other's hotspot). "
                + "Scan the QR code or open the link in the other phone's browser to control play, speed "
                + "and font. Keep the script open on the teleprompter phone."));

        LinearLayout cd = card(box, "Shortcut");
        android.widget.CheckBox dbl = new android.widget.CheckBox(this);
        dbl.setText("Double-press Play → go back to start");
        dbl.setTextColor(Color.BLACK);
        dbl.setChecked(Store.prefs(this).getBoolean("dbl_top", false));
        dbl.setOnCheckedChangeListener((b, c) -> Store.prefs(this).edit().putBoolean("dbl_top", c).apply());
        cd.addView(dbl);

        // 3. Test
        LinearLayout c2 = card(box, "Remote test");
        testTv = new TextView(this);
        testTv.setText("Press any button on the remote…");
        testTv.setTextSize(18);
        testTv.setTextColor(Color.BLACK);
        testTv.setGravity(Gravity.CENTER);
        testTv.setPadding(dp(8), dp(22), dp(8), dp(22));
        GradientDrawable tb = new GradientDrawable();
        tb.setColor(Color.rgb(245, 245, 245));
        tb.setCornerRadius(dp(10));
        testTv.setBackground(tb);
        c2.addView(testTv);

        // 4. Learn buttons
        LinearLayout c3 = card(box, "Button set");
        c3.addView(note("Tap \"Set\", then press the remote button (OK, Select, Back, arrow…) you want for that action. It is remembered."));
        for (int a = Keys.PLAY; a <= Keys.LAST; a++) {
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
                testTv.setText("Now press the remote button for \"" + Keys.NAMES[action] + "\"…");
                refreshLabels();
            });
            r.addView(set, new LinearLayout.LayoutParams(dp(64), dp(38)));
            c3.addView(r);
        }
        TextView reset = button("Reset to default", Color.rgb(230, 230, 230), Color.BLACK);
        reset.setOnClickListener(v -> {
            Keys.reset(this);
            ble.resetMap();
            learning = 0;
            testTv.setText("Buttons reset to default");
            refreshLabels();
        });
        c3.addView(reset);

        setContentView(root);
        refreshLabels();
    }

    // ---------- BLE scan flow ----------

    private void startScanFlow() {
        if (!BleRemote.hasPerms(this)) {
            requestPermissions(BleRemote.perms(), 7);
            return;
        }
        BluetoothAdapter a = ble.adapter();
        if (a == null) {
            testTv.setText("This phone has no Bluetooth");
            return;
        }
        if (!a.isEnabled()) {
            try {
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } catch (Exception e) {
                testTv.setText("Turn on phone Bluetooth and tap again");
            }
            return;
        }
        foundAddr.clear();
        foundName.clear();
        foundAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        scanDialog = new AlertDialog.Builder(this)
                .setTitle("Searching… (keep the remote on)")
                .setAdapter(foundAdapter, (d, w) -> {
                    ble.stopScan();
                    String addr = foundAddr.get(w);
                    String name = foundName.get(w);
                    ble.connect(addr, name, false);
                    testTv.setText("Connecting: " + name + "\nThen set the buttons below");
                })
                .setNegativeButton("Close", (d, w) -> ble.stopScan())
                .show();
        ble.startScan((addr, name) -> {
            if (foundAddr.contains(addr) || scanDialog == null) return;
            boolean fw = name.toLowerCase().contains("feelworld");
            int idx = fw ? 0 : foundAddr.size();
            foundAddr.add(idx, addr);
            foundName.add(idx, name);
            foundAdapter.insert((fw ? "⭐  " : "") + name, idx);
        });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == 7) {
            if (BleRemote.hasPerms(this)) startScanFlow();
            else testTv.setText("Bluetooth permission needed — tap again and choose \"Allow\"");
        }
    }

    @Override
    public void onBleState(boolean connected, String text) {
        bleStatus.setText(text);
        bleStatus.setTextColor(connected ? Color.rgb(0, 140, 70) : Color.rgb(200, 120, 0));
    }

    @Override
    public void onBleRaw(String sig, int action) {
        if (learning > 0) {
            ble.assign(learning, sig);
            testTv.setText("✓  Remote button  →  " + Keys.NAMES[learning]);
            learning = 0;
            refreshLabels();
        } else {
            testTv.setText("Remote signal: " + sig + "\n→  "
                    + (action == Keys.NONE ? "(not set — use Set below)" : Keys.NAMES[action]));
        }
    }

    // ---------- UI helpers ----------

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
        for (int a = Keys.PLAY; a <= Keys.LAST; a++) {
            int k = Keys.custom(this, a);
            boolean b = ble.mapped(a) != null;
            if (learning == a) {
                keyLabels[a].setText("press…");
                keyLabels[a].setTextColor(Color.rgb(230, 120, 0));
            } else if (b || k != -1) {
                String s = b ? "Remote ✓" : "";
                if (k != -1) s += (s.isEmpty() ? "" : " + ") + Keys.keyName(k);
                keyLabels[a].setText(s);
                keyLabels[a].setTextColor(Color.rgb(0, 140, 70));
            } else {
                keyLabels[a].setText("Default");
                keyLabels[a].setTextColor(Color.GRAY);
            }
        }
    }

    private void refreshWifi() {
        boolean on = RemoteServer.running();
        String url = RemoteServer.url();
        wifiBtn.setText(on ? "⏹  Stop phone remote" : "▶  Start phone remote");
        if (on && url != null) {
            wifiTv.setText("✅ Running — open on the other phone:\n" + url);
            qrIv.setImageBitmap(RemoteServer.qr(url, 400));
            qrIv.setVisibility(android.view.View.VISIBLE);
        } else {
            wifiTv.setText(url == null ? "Phone is not on WiFi / hotspot" : "Stopped");
            qrIv.setVisibility(android.view.View.GONE);
        }
    }

    private void refreshDevices() {
        devList.removeAllViews();
        List<String> names = Keys.remotes();
        TextView t = new TextView(this);
        t.setTextSize(15);
        if (names.isEmpty()) {
            t.setText("No keyboard-type remote connected");
            t.setTextColor(Color.GRAY);
        } else {
            t.setText("✅  " + android.text.TextUtils.join("\n✅  ", names));
            t.setTextColor(Color.rgb(0, 140, 70));
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        devList.addView(t);
    }

    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent ev) {
        if (RemoteNav.isJoystick(ev) && ev.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
            int d = RemoteNav.joyDir(ev);
            if (d != 0) {
                String[] n = {"", "LEFT", "RIGHT", "UP", "DOWN"};
                testTv.setText("Joystick: " + n[d] + "\n→  moves the menu highlight (press Select / OK to choose)");
            }
            return true;
        }
        return super.dispatchGenericMotionEvent(ev);
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
                        + (a == Keys.NONE ? "(no action — use Set)" : Keys.NAMES[a]));
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
        ble.setRawListener(this);
        refreshDevices();
        refreshWifi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (im != null) im.unregisterInputDeviceListener(this);
        ble.setRawListener(null);
        ble.stopScan();
    }

    @Override public void onInputDeviceAdded(int id) { refreshDevices(); }
    @Override public void onInputDeviceRemoved(int id) { refreshDevices(); }
    @Override public void onInputDeviceChanged(int id) { refreshDevices(); }
}
