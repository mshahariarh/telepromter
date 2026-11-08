package com.gdstudio.teleprompter;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.UUID;

/**
 * Connects to app-paired BLE remotes (e.g. Feelworld) directly inside the app,
 * listens to every notify characteristic, and turns learned button signals into
 * the same key events the prompter already understands. Reconnects automatically.
 */
public class BleRemote implements Application.ActivityLifecycleCallbacks {

    interface RawListener {
        void onBleState(boolean connected, String text);
        void onBleRaw(String sig, int action);
    }

    interface ScanCb { void found(String addr, String name); }

    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static BleRemote inst;

    static synchronized BleRemote get(Context c) {
        if (inst == null) {
            inst = new BleRemote(c.getApplicationContext());
            ((Application) c.getApplicationContext()).registerActivityLifecycleCallbacks(inst);
            inst.autoConnect();
        }
        return inst;
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<BluetoothGattDescriptor> queue = new ArrayDeque<>();
    private BluetoothGatt gatt;
    private boolean connected = false, wanted = false;
    private RawListener rawListener;
    private Activity current;
    private ScanCallback scanCb;
    private String lastSig = "";
    private long lastTime = 0;

    private BleRemote(Context c) {
        ctx = c;
    }

    // ---------- permissions / adapter ----------

    static String[] perms() {
        return Build.VERSION.SDK_INT >= 31
                ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    static boolean hasPerms(Context c) {
        for (String p : perms())
            if (c.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    BluetoothAdapter adapter() {
        BluetoothManager m = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        return m == null ? null : m.getAdapter();
    }

    boolean isConnected() { return connected; }
    String savedName() { return Store.prefs(ctx).getString("ble_name", ""); }
    String savedAddr() { return Store.prefs(ctx).getString("ble_addr", ""); }

    String statusText() {
        if (savedAddr().isEmpty()) return "Kono remote connect kora nei";
        return connected ? "✅  Connected: " + savedName() : "⏳  Khujchi: " + savedName() + " (remote on korun)";
    }

    void setRawListener(RawListener l) {
        rawListener = l;
        if (l != null) l.onBleState(connected, statusText());
    }

    // ---------- connect ----------

    void autoConnect() {
        if (!savedAddr().isEmpty() && !connected && gatt == null && hasPerms(ctx))
            connect(savedAddr(), savedName(), true);
    }

    void connect(String addr, String name, boolean background) {
        Store.prefs(ctx).edit().putString("ble_addr", addr)
                .putString("ble_name", name == null || name.isEmpty() ? addr : name).apply();
        wanted = true;
        closeGatt();
        try {
            BluetoothAdapter a = adapter();
            if (a == null || !a.isEnabled()) {
                state(false, "Phone er Bluetooth bondho — on korun");
                return;
            }
            BluetoothDevice d = a.getRemoteDevice(addr);
            gatt = Build.VERSION.SDK_INT >= 23
                    ? d.connectGatt(ctx, background, cb, BluetoothDevice.TRANSPORT_LE)
                    : d.connectGatt(ctx, background, cb);
            state(false, "⏳  Connecting: " + savedName());
        } catch (Exception e) {
            state(false, "Connect hoyni — abar chesta korun");
        }
    }

    void forget() {
        wanted = false;
        closeGatt();
        connected = false;
        Store.prefs(ctx).edit().remove("ble_addr").remove("ble_name").apply();
        state(false, statusText());
    }

    private void closeGatt() {
        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception ignored) {
            }
            gatt = null;
        }
    }

    private void state(boolean c, String text) {
        main.post(() -> {
            if (rawListener != null) rawListener.onBleState(c, text);
        });
    }

    private void toast(String t) {
        main.post(() -> {
            if (rawListener == null) Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show();
        });
    }

    private final BluetoothGattCallback cb = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                try {
                    g.discoverServices();
                } catch (Exception ignored) {
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                boolean was = connected;
                connected = false;
                try {
                    g.close();
                } catch (Exception ignored) {
                }
                if (g == gatt) gatt = null;
                state(false, "⏳  Remote disconnected — abar connect hobe…");
                if (was) toast("⚠ Remote disconnected");
                // keep trying in the background; the system waits until the remote is back
                if (wanted) main.postDelayed(() -> {
                    if (wanted && !connected && gatt == null) connect(savedAddr(), savedName(), true);
                }, 1500);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            queue.clear();
            for (BluetoothGattService s : g.getServices()) {
                for (BluetoothGattCharacteristic ch : s.getCharacteristics()) {
                    int p = ch.getProperties();
                    boolean notify = (p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
                    boolean indicate = (p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
                    if (!notify && !indicate) continue;
                    try {
                        g.setCharacteristicNotification(ch, true);
                    } catch (Exception ignored) {
                    }
                    BluetoothGattDescriptor d = ch.getDescriptor(CCCD);
                    if (d != null) {
                        d.setValue(notify ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                        queue.add(d);
                    }
                }
            }
            connected = true;
            state(true, "✅  Connected: " + savedName());
            toast("🎮 Remote connected: " + savedName());
            next(g);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            next(g);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            handle(ch.getUuid(), ch.getValue());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch, byte[] value) {
            handle(ch.getUuid(), value);
        }
    };

    private void next(BluetoothGatt g) {
        BluetoothGattDescriptor d = queue.poll();
        if (d == null) return;
        try {
            if (!g.writeDescriptor(d)) next(g);
        } catch (Exception e) {
            next(g);
        }
    }

    // ---------- button signals ----------

    private void handle(UUID u, byte[] v) {
        if (v == null || v.length == 0) return;
        boolean allZero = true;
        for (byte b : v) if (b != 0) { allZero = false; break; }
        if (allZero) return; // button release
        StringBuilder sb = new StringBuilder();
        for (byte b : v) sb.append(String.format("%02X", b));
        String sig = u.toString().substring(4, 8).toUpperCase() + ":" + sb;
        long now = System.currentTimeMillis();
        if (sig.equals(lastSig) && now - lastTime < 120) return;
        lastSig = sig;
        lastTime = now;
        int mappedAction = actionFor(sig);
        // nothing learned yet: any remote button = Play/Pause, so it works right away
        int action = (mappedAction == Keys.NONE && !hasAnyMap()) ? Keys.PLAY : mappedAction;
        main.post(() -> {
            if (rawListener != null) {
                rawListener.onBleRaw(sig, mappedAction);
            } else if (current instanceof PrompterActivity && action != Keys.NONE) {
                ((PrompterActivity) current).doAction(action, true);
            } else if (current != null && action != Keys.NONE) {
                int code = codeFor(action);
                current.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
                current.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
            }
        });
    }

    private static int codeFor(int action) {
        switch (action) {
            case Keys.PLAY: return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            case Keys.BACK: return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            case Keys.FWD: return KeyEvent.KEYCODE_MEDIA_NEXT;
            case Keys.FASTER: return KeyEvent.KEYCODE_NUMPAD_ADD;
            case Keys.SLOWER: return KeyEvent.KEYCODE_NUMPAD_SUBTRACT;
        }
        return KeyEvent.KEYCODE_UNKNOWN;
    }

    int actionFor(String sig) {
        SharedPreferences p = Store.prefs(ctx);
        for (int a = Keys.PLAY; a <= Keys.LAST; a++)
            if (sig.equals(p.getString("bm_" + a, null))) return a;
        return Keys.NONE;
    }

    void assign(int action, String sig) {
        SharedPreferences p = Store.prefs(ctx);
        SharedPreferences.Editor e = p.edit();
        for (int a = Keys.PLAY; a <= Keys.LAST; a++)
            if (sig.equals(p.getString("bm_" + a, null))) e.remove("bm_" + a);
        e.putString("bm_" + action, sig).apply();
    }

    boolean hasAnyMap() {
        for (int a = Keys.PLAY; a <= Keys.LAST; a++) if (mapped(a) != null) return true;
        return false;
    }

    String mapped(int action) {
        return Store.prefs(ctx).getString("bm_" + action, null);
    }

    void resetMap() {
        SharedPreferences.Editor e = Store.prefs(ctx).edit();
        for (int a = Keys.PLAY; a <= Keys.LAST; a++) e.remove("bm_" + a);
        e.apply();
    }

    // ---------- scanning ----------

    void startScan(ScanCb found) {
        stopScan();
        try {
            BluetoothAdapter a = adapter();
            if (a == null) return;
            BluetoothLeScanner s = a.getBluetoothLeScanner();
            if (s == null) return;
            scanCb = new ScanCallback() {
                @Override
                public void onScanResult(int type, ScanResult r) {
                    String n = null;
                    try {
                        n = r.getDevice().getName();
                    } catch (SecurityException ignored) {
                    }
                    if ((n == null || n.isEmpty()) && r.getScanRecord() != null)
                        n = r.getScanRecord().getDeviceName();
                    if (n == null || n.isEmpty()) return;
                    String name = n;
                    String addr = r.getDevice().getAddress();
                    main.post(() -> found.found(addr, name));
                }
            };
            s.startScan(scanCb);
            main.postDelayed(this::stopScan, 15000);
        } catch (Exception ignored) {
        }
    }

    void stopScan() {
        try {
            if (scanCb != null) {
                BluetoothLeScanner s = adapter().getBluetoothLeScanner();
                if (s != null) s.stopScan(scanCb);
            }
        } catch (Exception ignored) {
        }
        scanCb = null;
    }

    // ---------- lifecycle: send buttons to the visible screen, reconnect on open ----------

    @Override public void onActivityResumed(Activity a) { current = a; autoConnect(); }
    @Override public void onActivityPaused(Activity a) { if (current == a) current = null; }
    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityStarted(Activity a) {}
    @Override public void onActivityStopped(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}
}
