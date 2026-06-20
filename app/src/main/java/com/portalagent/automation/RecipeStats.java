package com.portalagent.automation;

import org.json.JSONObject;

public final class RecipeStats {

    public final int successCount;
    public final int failureCount;
    public final long lastSuccessAt;
    public final long lastFailureAt;
    public final long averageDurationMs;
    public final String lastFailureReason;

    public RecipeStats(int successCount, int failureCount, long lastSuccessAt, long lastFailureAt,
                       long averageDurationMs, String lastFailureReason) {
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.lastSuccessAt = lastSuccessAt;
        this.lastFailureAt = lastFailureAt;
        this.averageDurationMs = averageDurationMs;
        this.lastFailureReason = lastFailureReason == null ? "" : lastFailureReason;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        AutomationJson.put(json, "success_count", successCount);
        AutomationJson.put(json, "failure_count", failureCount);
        AutomationJson.put(json, "last_success_at", lastSuccessAt);
        AutomationJson.put(json, "last_failure_at", lastFailureAt);
        AutomationJson.put(json, "average_duration_ms", averageDurationMs);
        AutomationJson.put(json, "last_failure_reason", lastFailureReason);
        return json;
    }

    public static RecipeStats fromJson(JSONObject json) {
        if (json == null) return empty();
        return new RecipeStats(
            json.optInt("success_count", 0),
            json.optInt("failure_count", 0),
            json.optLong("last_success_at", 0),
            json.optLong("last_failure_at", 0),
            json.optLong("average_duration_ms", 0),
            json.optString("last_failure_reason", ""));
    }

    public static RecipeStats empty() {
        return new RecipeStats(0, 0, 0, 0, 0, "");
    }
}
