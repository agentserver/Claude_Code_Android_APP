package com.termux.app.mcp.tools;

import android.content.Context;
import android.util.Base64;

import com.termux.app.mcp.McpTool;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Three file tools: file.check_exists, file.list, file.read
 *
 * Registered as three separate McpTool instances via FileTool.checkExists(),
 * FileTool.list(), FileTool.read().
 */
public class FileTool implements McpTool {

    private static final int MAX_LIST_ENTRIES  = 200;
    private static final int DEFAULT_MAX_MB     = 5;
    private static final int HARD_MAX_MB        = 20;

    private final Kind mKind;
    private final SimpleDateFormat mDateFmt =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

    public enum Kind { CHECK_EXISTS, LIST, READ }

    public FileTool(Kind kind) {
        mKind = kind;
    }

    @Override public String getName() {
        switch (mKind) {
            case CHECK_EXISTS: return "file.check_exists";
            case LIST:         return "file.list";
            default:           return "file.read";
        }
    }

    @Override public String getDescription() {
        switch (mKind) {
            case CHECK_EXISTS: return "Check if a file or directory exists at the given path.";
            case LIST:         return "List directory contents. Returns up to 200 entries.";
            default:           return "Read a file and return its contents as base64. For images returns an image content block. Max 5 MB by default.";
        }
    }

    @Override public String getInputSchema() {
        switch (mKind) {
            case CHECK_EXISTS:
                return "{\"type\":\"object\",\"properties\":{" +
                    "\"path\":{\"type\":\"string\"}," +
                    "\"task_id\":{\"type\":\"string\"}" +
                    "},\"required\":[\"path\"]}";
            case LIST:
                return "{\"type\":\"object\",\"properties\":{" +
                    "\"path\":{\"type\":\"string\"}," +
                    "\"show_hidden\":{\"type\":\"boolean\",\"default\":false}," +
                    "\"task_id\":{\"type\":\"string\"}" +
                    "},\"required\":[\"path\"]}";
            default:
                return "{\"type\":\"object\",\"properties\":{" +
                    "\"path\":{\"type\":\"string\"}," +
                    "\"max_size_mb\":{\"type\":\"integer\",\"default\":5}," +
                    "\"task_id\":{\"type\":\"string\"}" +
                    "},\"required\":[\"path\"]}";
        }
    }

    @Override
    public String call(JSONObject args, Context context) throws Exception {
        switch (mKind) {
            case CHECK_EXISTS: return callCheckExists(args);
            case LIST:         return callList(args);
            default:           return callRead(args);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String callCheckExists(JSONObject args) throws Exception {
        String path = args.getString("path");
        File f = new File(path);

        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("path", path);
        result.put("exists", f.exists());
        if (f.exists()) {
            result.put("is_dir", f.isDirectory());
            result.put("size_bytes", f.isFile() ? f.length() : 0);
        }
        return textContent(result.toString(2));
    }

    private String callList(JSONObject args) throws Exception {
        String path = args.getString("path");
        boolean showHidden = args.optBoolean("show_hidden", false);
        File dir = new File(path);

        if (!dir.exists())     return textContent("{\"ok\":false,\"error\":\"Path does not exist: " + path + "\"}");
        if (!dir.isDirectory())return textContent("{\"ok\":false,\"error\":\"Not a directory: " + path + "\"}");

        File[] files = dir.listFiles();
        if (files == null)     return textContent("{\"ok\":false,\"error\":\"Cannot list directory (permission denied?)\"}");

        JSONArray entries = new JSONArray();
        int count = 0;
        for (File f : files) {
            if (!showHidden && f.getName().startsWith(".")) continue;
            if (count >= MAX_LIST_ENTRIES) break;
            JSONObject entry = new JSONObject();
            entry.put("name", f.getName());
            entry.put("type", f.isDirectory() ? "dir" : "file");
            entry.put("size_bytes", f.isFile() ? f.length() : 0);
            entry.put("last_modified", mDateFmt.format(new Date(f.lastModified())));
            entries.put(entry);
            count++;
        }

        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("path", path);
        result.put("count", count);
        result.put("truncated", files.length > MAX_LIST_ENTRIES);
        result.put("entries", entries);
        return textContent(result.toString(2));
    }

    private String callRead(JSONObject args) throws Exception {
        String path = args.getString("path");
        int maxMb = Math.min(args.optInt("max_size_mb", DEFAULT_MAX_MB), HARD_MAX_MB);
        long maxBytes = (long) maxMb * 1024 * 1024;

        File f = new File(path);
        if (!f.exists())   return textContent("{\"ok\":false,\"error\":\"File not found: " + path + "\"}");
        if (f.isDirectory()) return textContent("{\"ok\":false,\"error\":\"Path is a directory: " + path + "\"}");
        if (f.length() > maxBytes)
            return textContent("{\"ok\":false,\"error\":\"File too large: " + f.length() + " bytes (limit " + maxBytes + " bytes). Increase max_size_mb or copy the file first.\"}");

        byte[] bytes = readFile(f);
        String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
        String mime = guessMime(path);

        JSONArray content = new JSONArray();
        if (mime.startsWith("image/")) {
            JSONObject img = new JSONObject();
            img.put("type", "image");
            img.put("data", b64);
            img.put("mimeType", mime);
            content.put(img);
        } else {
            // Return metadata + base64; Claude can decode or treat as data URI
            JSONObject meta = new JSONObject();
            meta.put("type", "text");
            meta.put("text", "{\"ok\":true,\"path\":\"" + path + "\",\"size_bytes\":" + bytes.length
                + ",\"mime_type\":\"" + mime + "\",\"content_base64\":\"" + b64 + "\"}");
            content.put(meta);
        }
        return content.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static byte[] readFile(File f) throws IOException {
        byte[] buf = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int offset = 0, n;
            while (offset < buf.length && (n = fis.read(buf, offset, buf.length - offset)) >= 0)
                offset += n;
        }
        return buf;
    }

    private static String guessMime(String path) {
        String lower = path.toLowerCase(Locale.US);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".log"))
            return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private static String textContent(String text) throws Exception {
        JSONObject item = new JSONObject();
        item.put("type", "text");
        item.put("text", text);
        return new JSONArray().put(item).toString();
    }
}
