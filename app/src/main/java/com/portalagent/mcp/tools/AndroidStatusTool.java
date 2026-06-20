package com.portalagent.mcp.tools;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.portalagent.mcp.AndroidMcpPermissionState;
import com.portalagent.mcp.AdbCompanionClient;
import com.portalagent.mcp.McpTool;

import org.json.JSONArray;
import org.json.JSONObject;

/** android.get_status — returns permission states and list of enabled MCP tools. */
public class AndroidStatusTool implements McpTool {

    private static final String VERSION = "0.1.0";
    private final AdbCompanionClient adbClient;

    public AndroidStatusTool() {
        this(new AdbCompanionClient(AdbCompanionClient.DEFAULT_BASE_URL, 250));
    }

    AndroidStatusTool(AdbCompanionClient adbClient) {
        this.adbClient = adbClient;
    }

    @Override public String getName() { return "android.get_status"; }

    @Override public String getDescription() {
        return "Get Android MCP server status: enabled tools, permission states, and server version.";
    }

    @Override public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String call(JSONObject args, Context context) throws Exception {
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("server_version", VERSION);

        JSONObject perms = new JSONObject();
        perms.put("camera",           hasPermission(context, Manifest.permission.CAMERA));
        perms.put("storage_read",     hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE));
        perms.put("storage_manage",   hasPermission(context, Manifest.permission.MANAGE_EXTERNAL_STORAGE));
        boolean accessibility = AndroidMcpPermissionState.isAccessibilityReady(context);
        boolean mediaProjection = AndroidMcpPermissionState.isScreenCaptureRunning();
        perms.put("accessibility",    accessibility);
        perms.put("media_projection", mediaProjection);
        result.put("permissions", perms);
        JSONObject adbStatus = adbStatus();
        boolean adbOnline = adbStatus.optBoolean("ok", false);
        result.put("adb_companion", adbStatus);

        JSONArray tools = new JSONArray();
        tools.put("android.get_status");
        tools.put("loom.list_local_slaves");
        tools.put("adb.get_status");
        tools.put("camera.take_photo");
        tools.put("file.check_exists");
        tools.put("file.list");
        tools.put("file.read");
        if (mediaProjection) {
            tools.put("screen.capture");
        }
        if (accessibility) {
            tools.put("ui.tap");
            tools.put("ui.swipe");
            tools.put("ui.click_text");
            tools.put("ui.input_text");
            tools.put("ui.get_accessibility_tree");
            tools.put("app.open");
            tools.put("app.get_current_activity");
        }
        if (adbOnline) {
            tools.put("adb.screenshot");
            tools.put("adb.tap");
            tools.put("adb.swipe");
            tools.put("adb.input_text");
            tools.put("adb.keyevent");
            tools.put("adb.current_activity");
        }
        result.put("enabled_tools", tools);

        result.put("current_task_id", JSONObject.NULL);

        JSONArray content = new JSONArray();
        JSONObject text = new JSONObject();
        text.put("type", "text");
        text.put("text", result.toString(2));
        content.put(text);
        return content.toString();
    }

    private static boolean hasPermission(Context ctx, String permission) {
        return ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private JSONObject adbStatus() {
        try {
            JSONObject status = adbClient.call("status", new JSONObject());
            if (!status.has("ok")) status.put("ok", true);
            return status;
        } catch (Exception e) {
            JSONObject status = new JSONObject();
            try {
                status.put("ok", false);
                status.put("message", "ADB Companion 未连接: " + safeMessage(e));
                status.put("port", AdbCompanionClient.DEFAULT_PORT);
            } catch (Exception ignored) {
            }
            return status;
        }
    }

    private static String safeMessage(Exception e) {
        String message = e == null ? "" : e.getMessage();
        return message == null || message.isEmpty() ? String.valueOf(e) : message;
    }
}
