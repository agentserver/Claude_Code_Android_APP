package com.termux.app.mcp.tools;

import android.content.Context;

import com.termux.app.mcp.AdbCompanionClient;
import com.termux.app.mcp.McpTool;

import org.json.JSONArray;
import org.json.JSONObject;

public class AdbTool implements McpTool {

    public enum Kind {
        GET_STATUS,
        SCREENSHOT,
        TAP,
        SWIPE,
        INPUT_TEXT,
        KEYEVENT,
        CURRENT_ACTIVITY
    }

    private final Kind kind;
    private final AdbCompanionClient client;

    public AdbTool(Kind kind) {
        this(kind, new AdbCompanionClient());
    }

    public AdbTool(Kind kind, AdbCompanionClient client) {
        this.kind = kind;
        this.client = client == null ? new AdbCompanionClient() : client;
    }

    @Override
    public String getName() {
        switch (kind) {
            case GET_STATUS: return "adb.get_status";
            case SCREENSHOT: return "adb.screenshot";
            case TAP: return "adb.tap";
            case SWIPE: return "adb.swipe";
            case INPUT_TEXT: return "adb.input_text";
            case KEYEVENT: return "adb.keyevent";
            case CURRENT_ACTIVITY: return "adb.current_activity";
            default: return "adb.unknown";
        }
    }

    @Override
    public String getDescription() {
        switch (kind) {
            case GET_STATUS:
                return "检查宿主机 ADB Companion 是否在线，并返回设备序列号和前台应用。";
            case SCREENSHOT:
                return "通过宿主机 ADB Companion 截取当前屏幕，返回 PNG 图片。";
            case TAP:
                return "通过宿主机 ADB Companion 执行 adb shell input tap。";
            case SWIPE:
                return "通过宿主机 ADB Companion 执行 adb shell input swipe。";
            case INPUT_TEXT:
                return "通过宿主机 ADB Companion 输入文本。敏感内容不会进入自动化 Boost。";
            case KEYEVENT:
                return "通过宿主机 ADB Companion 发送 Android keyevent。";
            case CURRENT_ACTIVITY:
                return "通过宿主机 ADB Companion 读取当前前台包名和 Activity。";
            default:
                return "";
        }
    }

    @Override
    public String getInputSchema() {
        switch (kind) {
            case TAP:
                return "{\"type\":\"object\",\"required\":[\"x\",\"y\"],\"properties\":{" +
                    "\"task_id\":{\"type\":\"string\"}," +
                    "\"x\":{\"type\":\"number\"}," +
                    "\"y\":{\"type\":\"number\"}" +
                    "}}";
            case SWIPE:
                return "{\"type\":\"object\",\"required\":[\"x1\",\"y1\",\"x2\",\"y2\"],\"properties\":{" +
                    "\"task_id\":{\"type\":\"string\"}," +
                    "\"x1\":{\"type\":\"number\"},\"y1\":{\"type\":\"number\"}," +
                    "\"x2\":{\"type\":\"number\"},\"y2\":{\"type\":\"number\"}," +
                    "\"duration_ms\":{\"type\":\"integer\",\"default\":300}" +
                    "}}";
            case INPUT_TEXT:
                return "{\"type\":\"object\",\"required\":[\"text\"],\"properties\":{" +
                    "\"task_id\":{\"type\":\"string\"}," +
                    "\"text\":{\"type\":\"string\"}" +
                    "}}";
            case KEYEVENT:
                return "{\"type\":\"object\",\"required\":[\"keycode\"],\"properties\":{" +
                    "\"task_id\":{\"type\":\"string\"}," +
                    "\"keycode\":{\"type\":\"string\",\"description\":\"Android keycode, e.g. BACK, HOME, ENTER, 4, 66\"}" +
                    "}}";
            case SCREENSHOT:
                return "{\"type\":\"object\",\"properties\":{\"task_id\":{\"type\":\"string\"}}}";
            case CURRENT_ACTIVITY:
            case GET_STATUS:
            default:
                return "{\"type\":\"object\",\"properties\":{\"task_id\":{\"type\":\"string\"}}}";
        }
    }

    @Override
    public String call(JSONObject args, Context context) throws Exception {
        if (kind == Kind.GET_STATUS) {
            return statusContent(args);
        }

        JSONObject response = client.call(actionName(), args);
        if (!response.optBoolean("ok", true)) {
            throw new Exception(response.optString("message", "ADB Companion action failed"));
        }
        if (kind == Kind.SCREENSHOT) {
            return imageContent(response);
        }
        return textContent(response.optString("message", response.toString(2)));
    }

    private String statusContent(JSONObject args) throws Exception {
        try {
            JSONObject response = client.call(actionName(), args);
            if (!response.has("ok")) {
                response.put("ok", true);
            }
            return textContent(response.toString(2));
        } catch (Exception e) {
            JSONObject disconnected = new JSONObject();
            disconnected.put("ok", false);
            disconnected.put("message", "ADB Companion 未连接: " + safeMessage(e));
            disconnected.put("setup", "在电脑运行 tools/adb_companion_server.py，并执行 adb reverse tcp:"
                + AdbCompanionClient.DEFAULT_PORT + " tcp:" + AdbCompanionClient.DEFAULT_PORT);
            return textContent(disconnected.toString(2));
        }
    }

    private String imageContent(JSONObject response) throws Exception {
        String b64 = response.optString("image_base64", "");
        if (b64.isEmpty()) {
            return textContent(response.optString("message", "ADB screenshot returned no image"));
        }
        JSONObject image = new JSONObject();
        image.put("type", "image");
        image.put("data", b64);
        image.put("mimeType", response.optString("mime_type", "image/png"));
        return new JSONArray().put(image).toString();
    }

    private String actionName() {
        switch (kind) {
            case GET_STATUS: return "status";
            case SCREENSHOT: return "screenshot";
            case TAP: return "tap";
            case SWIPE: return "swipe";
            case INPUT_TEXT: return "input_text";
            case KEYEVENT: return "keyevent";
            case CURRENT_ACTIVITY: return "current_activity";
            default: return "";
        }
    }

    private static String safeMessage(Exception e) {
        String message = e == null ? "" : e.getMessage();
        return message == null || message.isEmpty() ? String.valueOf(e) : message;
    }

    private static String textContent(String message) throws Exception {
        JSONObject item = new JSONObject();
        item.put("type", "text");
        item.put("text", message);
        return new JSONArray().put(item).toString();
    }
}
