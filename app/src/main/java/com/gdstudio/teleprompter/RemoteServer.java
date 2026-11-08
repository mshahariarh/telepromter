package com.gdstudio.teleprompter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;

/** "Phone ke remote": a tiny web server; another phone on the same WiFi opens the page and taps buttons. */
public class RemoteServer {

    static final int PORT = 8080;
    private static ServerSocket ss;
    private static final Handler main = new Handler(Looper.getMainLooper());

    static boolean running() {
        return ss != null && !ss.isClosed();
    }

    static synchronized void start(Context c) {
        if (running()) return;
        BleRemote ble = BleRemote.get(c);
        Thread t = new Thread(() -> {
            ServerSocket server;
            try {
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(PORT));
                ss = server;
            } catch (IOException e) {
                return;
            }
            while (!server.isClosed()) {
                try {
                    handle(server.accept(), ble);
                } catch (Exception ignored) {
                }
            }
        }, "phone-remote");
        t.setDaemon(true);
        t.start();
    }

    static synchronized void stop() {
        try {
            if (ss != null) ss.close();
        } catch (Exception ignored) {
        }
        ss = null;
    }

    static String url() {
        String ip = ip();
        return ip == null ? null : "http://" + ip + ":" + PORT;
    }

    static String ip() {
        try {
            String best = null;
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements(); ) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                    InetAddress ad = a.nextElement();
                    if (ad instanceof Inet4Address && ad.isSiteLocalAddress()) {
                        String n = ni.getName();
                        if (n.startsWith("wlan") || n.startsWith("ap") || n.startsWith("swlan"))
                            return ad.getHostAddress();
                        if (best == null) best = ad.getHostAddress();
                    }
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    static Bitmap qr(String text, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    b.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static void handle(Socket s, BleRemote ble) {
        try (Socket sock = s) {
            sock.setSoTimeout(3000);
            BufferedReader r = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
            String line = r.readLine();
            if (line == null) return;
            String[] parts = line.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";
            while ((line = r.readLine()) != null && !line.isEmpty()) {
                // skip headers
            }
            OutputStream o = sock.getOutputStream();
            if (path.startsWith("/a")) {
                int x = 0;
                int i = path.indexOf("x=");
                if (i >= 0) {
                    try {
                        x = Integer.parseInt(path.substring(i + 2).replaceAll("[^0-9].*$", ""));
                    } catch (Exception ignored) {
                    }
                }
                final int act = x;
                main.post(() -> {
                    Activity cur = ble.currentActivity();
                    if (cur instanceof PrompterActivity) ((PrompterActivity) cur).doAction(act, true);
                });
                send(o, "text/plain", "ok");
            } else {
                send(o, "text/html; charset=utf-8", PAGE);
            }
        } catch (Exception ignored) {
        }
    }

    private static void send(OutputStream o, String type, String body) throws IOException {
        byte[] b = body.getBytes("UTF-8");
        o.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + b.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        o.write(b);
        o.flush();
    }

    private static final String PAGE = "<!doctype html><html><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>"
            + "<title>GD Teleprompter Remote</title><style>"
            + "body{margin:0;background:#111;color:#fff;font-family:sans-serif;text-align:center}"
            + "h1{font-size:18px;margin:14px 0 4px}p{color:#999;font-size:12px;margin:0 0 6px}"
            + ".g{display:grid;grid-template-columns:1fr 1fr;gap:10px;padding:10px}"
            + "button{font-size:20px;padding:22px 6px;border:0;border-radius:14px;background:#2a2d3a;color:#fff}"
            + "button:active{background:#ff9800;color:#000}"
            + ".big{grid-column:span 2;background:#ff9800;color:#000;font-size:28px;padding:34px}"
            + "#s{height:18px;color:#ff9800;font-size:13px}</style></head><body>"
            + "<h1>GD Teleprompter Remote</h1><p>Keep the script open on the teleprompter phone</p><div id='s'></div>"
            + "<div class='g'><button class='big' onclick='a(1)'>&#9654; / &#10074;&#10074; Play</button>"
            + "<button onclick='a(2)'>&#9650; Back</button><button onclick='a(3)'>&#9660; Forward</button>"
            + "<button onclick='a(5)'>&minus; Speed</button><button onclick='a(4)'>+ Speed</button>"
            + "<button onclick='a(9)'>A&minus; Font</button><button onclick='a(8)'>A+ Font</button>"
            + "<button onclick='a(7)'>&#9198; Start</button><button onclick='a(10)'>&#8651; Mirror</button></div>"
            + "<p>Developer: M. Shahariar</p>"
            + "<script>function a(x){var s=document.getElementById('s');"
            + "fetch('/a?x='+x+'&t='+Date.now()).then(function(){s.textContent='✓'})"
            + ".catch(function(){s.textContent='Not connected - are both phones on the same WiFi?'});"
            + "setTimeout(function(){s.textContent=''},900)}</script></body></html>";
}
