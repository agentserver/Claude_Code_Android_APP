package com.termux.app.automation;

import org.json.JSONObject;

public final class ToolTraceEvent {

    public final String id;
    public final long timestampMs;
    public final String taskId;
    public final String toolName;
    public final JSONObject arguments;
    public final boolean success;
    public final String resultSummary;
    public final String packageName;
    public final String activityName;

    public ToolTraceEvent(String id, long timestampMs, String taskId, String toolName,
                          JSONObject arguments, boolean success, String resultSummary,
                          String packageName, String activityName) {
        this.id = defaultString(id);
        this.timestampMs = timestampMs;
        this.taskId = defaultString(taskId);
        this.toolName = defaultString(toolName);
        this.arguments = AutomationJson.copyObject(arguments);
        this.success = success;
        this.resultSummary = defaultString(resultSummary);
        this.packageName = defaultString(packageName);
        this.activityName = defaultString(activityName);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        AutomationJson.put(json, "id", id);
        AutomationJson.put(json, "timestamp_ms", timestampMs);
        AutomationJson.put(json, "task_id", taskId);
        AutomationJson.put(json, "tool_name", toolName);
        AutomationJson.put(json, "arguments", AutomationJson.copyObject(arguments));
        AutomationJson.put(json, "success", success);
        AutomationJson.put(json, "result_summary", resultSummary);
        AutomationJson.put(json, "package", packageName);
        AutomationJson.put(json, "activity", activityName);
        return json;
    }

    public static ToolTraceEvent fromJson(JSONObject json) {
        if (json == null) json = new JSONObject();
        return new ToolTraceEvent(
            json.optString("id", ""),
            json.optLong("timestamp_ms", 0),
            json.optString("task_id", ""),
            json.optString("tool_name", ""),
            AutomationJson.copyObject(json.optJSONObject("arguments")),
            json.optBoolean("success", false),
            json.optString("result_summary", ""),
            json.optString("package", ""),
            json.optString("activity", ""));
    }

    public JSONObject argumentsCopy() {
        return AutomationJson.copyObject(arguments);
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
