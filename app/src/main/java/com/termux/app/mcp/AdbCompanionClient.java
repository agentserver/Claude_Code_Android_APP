package com.termux.app.mcp;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AdbCompanionClient {

    public static final int DEFAULT_PORT = 18765;
    public static final String DEFAULT_BASE_URL = "http://127.0.0.1:" + DEFAULT_PORT;
    private static final int DEFAULT_TIMEOUT_MS = 1500;

    private final String baseUrl;
    private final int timeoutMs;

    public AdbCompanionClient() {
        this(DEFAULT_BASE_URL, DEFAULT_TIMEOUT_MS);
    }

    public AdbCompanionClient(String baseUrl, int timeoutMs) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeoutMs = Math.max(100, timeoutMs);
    }

    public JSONObject call(String action, JSONObject arguments) throws Exception {
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing ADB companion action");
        }
        JSONObject body = arguments == null ? new JSONObject() : new JSONObject(arguments.toString());
        URL url = new URL(baseUrl + "/adb/" + action.trim());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(payload.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(payload);
        }

        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String raw = in == null ? "" : readFully(in);
        if (code < 200 || code >= 300) {
            throw new java.io.IOException("ADB Companion HTTP " + code + ": " + raw);
        }
        if (raw.trim().isEmpty()) {
            return new JSONObject().put("ok", true);
        }
        return new JSONObject(raw);
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null || value.trim().isEmpty() ? DEFAULT_BASE_URL : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String readFully(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
