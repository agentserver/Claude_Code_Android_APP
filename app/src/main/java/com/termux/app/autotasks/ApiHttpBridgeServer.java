package com.termux.app.autotasks;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.termux.api.apis.BatteryStatusAPI;
import com.termux.api.apis.CameraInfoAPI;
import com.termux.api.apis.SensorAPI;
import com.termux.api.apis.WifiAPI;
import com.termux.app.TermuxActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight HTTP API bridge server, listening on 127.0.0.1:17681.
 *
 * Problem: Termux binaries are compiled for Android bionic libc and cannot execute inside Ubuntu
 * proot's glibc environment. Wrapper scripts that exec termux-* binaries silently fail.
 *
 * Solution: This server runs inside the Android app process and exposes Android APIs over plain
 * HTTP. Claude Code inside Ubuntu proot calls standard curl/wget to get real-time data without
 * any bionic/glibc mismatch.
 *
 * Endpoints (GET only, all on 127.0.0.1 loopback):
 *   /battery   → battery status JSON     (matches termux-battery-status output)
 *   /camera    → camera info JSON array  (matches termux-camera-info output)
 *   /sensors   → sensor list JSON        (matches termux-sensor -l output)
 *   /wifi      → wifi connection JSON    (matches termux-wifi-connectioninfo output)
 *   /clipboard → clipboard plain text    (matches termux-clipboard-get output)
 */
public class ApiHttpBridgeServer {

    /** Port the HTTP bridge listens on (loopback only). */
    static final int PORT = 17681;

    private final TermuxActivity mActivity;
    private volatile boolean mRunning = false;
    private volatile ServerSocket mServerSocket;

    public ApiHttpBridgeServer(@NonNull TermuxActivity activity) {
        mActivity = activity;
    }

    public void start() {
        if (mRunning) return;
        mRunning = true;
        Thread t = new Thread(this::runServer, "api-http-bridge");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        mRunning = false;
        ServerSocket ss = mServerSocket;
        if (ss != null) {
            try { ss.close(); } catch (IOException ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Server loop
    // -------------------------------------------------------------------------

    private void runServer() {
        while (mRunning) {
            try (ServerSocket server = new ServerSocket(PORT, 10,
                    InetAddress.getByName("127.0.0.1"))) {
                server.setReuseAddress(true);
                mServerSocket = server;
                while (mRunning) {
                    Socket client = server.accept();
                    Thread ct = new Thread(() -> handleClient(client), "api-bridge-req");
                    ct.setDaemon(true);
                    ct.start();
                }
            } catch (IOException e) {
                if (!mRunning) break;
                // Retry after delay (e.g., if port is temporarily in use)
                try { Thread.sleep(5000); } catch (InterruptedException ignored) { break; }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Request handling
    // -------------------------------------------------------------------------

    private void handleClient(Socket client) {
        try (Socket sock = client) {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), "UTF-8"));
            String requestLine = reader.readLine();
            if (requestLine == null) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2 || !"GET".equals(parts[0])) {
                sendResponse(sock.getOutputStream(), 405, "application/json",
                    "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            // Strip query string from path
            String path = parts[1];
            if (path.contains("?")) path = path.substring(0, path.indexOf('?'));

            dispatch(path, sock.getOutputStream());
        } catch (IOException ignored) {}
    }

    private void dispatch(String path, OutputStream out) throws IOException {
        try {
            switch (path) {
                case "/battery":
                    sendResponse(out, 200, "application/json",
                        BatteryStatusAPI.getBatteryStatusJson(mActivity));
                    break;
                case "/camera":
                    sendResponse(out, 200, "application/json",
                        CameraInfoAPI.getCameraInfoJson(mActivity));
                    break;
                case "/sensors":
                    sendResponse(out, 200, "application/json",
                        SensorAPI.getSensorListJson(mActivity));
                    break;
                case "/wifi":
                    sendResponse(out, 200, "application/json",
                        WifiAPI.getWifiConnectionInfoJson(mActivity));
                    break;
                case "/clipboard":
                    // Return plain text to match termux-clipboard-get output format
                    sendResponse(out, 200, "text/plain; charset=utf-8", getClipboardText());
                    break;
                default:
                    sendResponse(out, 404, "application/json",
                        "{\"error\":\"Unknown endpoint\"," +
                        "\"available\":[\"/battery\",\"/camera\",\"/sensors\",\"/wifi\",\"/clipboard\"]}");
            }
        } catch (Throwable t) {
            sendResponse(out, 500, "application/json",
                "{\"error\":\"" + escapeJson(t.getMessage()) + "\"}");
        }
    }

    /**
     * Clipboard access on Android 10+ requires main thread. Marshal via Handler + CountDownLatch.
     */
    private String getClipboardText() {
        AtomicReference<String> result = new AtomicReference<>("");
        CountDownLatch latch = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                ClipboardManager cm = (ClipboardManager)
                    mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData data = cm != null ? cm.getPrimaryClip() : null;
                if (data != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < data.getItemCount(); i++) {
                        CharSequence text = data.getItemAt(i).coerceToText(mActivity);
                        if (text != null) sb.append(text);
                    }
                    result.set(sb.toString());
                }
            } finally {
                latch.countDown();
            }
        });
        try { latch.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return result.get();
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private void sendResponse(OutputStream out, int code, String contentType, String body)
            throws IOException {
        byte[] bodyBytes = body.getBytes("UTF-8");
        String status;
        switch (code) {
            case 200: status = "200 OK"; break;
            case 404: status = "404 Not Found"; break;
            case 405: status = "405 Method Not Allowed"; break;
            default:  status = code + " Error"; break;
        }
        String header = "HTTP/1.1 " + status + "\r\n"
            + "Content-Type: " + contentType + "\r\n"
            + "Content-Length: " + bodyBytes.length + "\r\n"
            + "Connection: close\r\n\r\n";
        out.write(header.getBytes("UTF-8"));
        out.write(bodyBytes);
        out.flush();
    }

    private static String escapeJson(String s) {
        if (s == null) return "null";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
