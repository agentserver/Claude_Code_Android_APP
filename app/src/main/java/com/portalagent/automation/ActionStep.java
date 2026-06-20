package com.portalagent.automation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ActionStep {

    public final String id;
    public final String toolName;
    public final JSONObject arguments;
    public final List<UiSelector> selectors;
    public final ScreenFingerprint preconditions;
    public final ScreenFingerprint postconditions;
    public final int timeoutMs;
    public final String fallbackPolicy;

    public ActionStep(String id, String toolName, JSONObject arguments, List<UiSelector> selectors,
                      ScreenFingerprint preconditions, ScreenFingerprint postconditions,
                      int timeoutMs, String fallbackPolicy) {
        this.id = defaultString(id);
        this.toolName = defaultString(toolName);
        this.arguments = AutomationJson.copyObject(arguments);
        this.selectors = Collections.unmodifiableList(copySelectors(selectors));
        this.preconditions = preconditions == null ? ScreenFingerprint.empty() : preconditions;
        this.postconditions = postconditions == null ? ScreenFingerprint.empty() : postconditions;
        this.timeoutMs = timeoutMs;
        this.fallbackPolicy = defaultString(fallbackPolicy);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        AutomationJson.put(json, "id", id);
        AutomationJson.put(json, "tool_name", toolName);
        AutomationJson.put(json, "arguments", AutomationJson.copyObject(arguments));
        JSONArray selectorArray = new JSONArray();
        for (UiSelector selector : selectors) {
            selectorArray.put(selector.toJson());
        }
        AutomationJson.put(json, "selectors", selectorArray);
        AutomationJson.put(json, "preconditions", preconditions.toJson());
        AutomationJson.put(json, "postconditions", postconditions.toJson());
        AutomationJson.put(json, "timeout_ms", timeoutMs);
        AutomationJson.put(json, "fallback_policy", fallbackPolicy);
        return json;
    }

    public static ActionStep fromJson(JSONObject json) {
        if (json == null) json = new JSONObject();
        List<UiSelector> selectors = new ArrayList<>();
        JSONArray selectorArray = json.optJSONArray("selectors");
        if (selectorArray != null) {
            for (int i = 0; i < selectorArray.length(); i++) {
                JSONObject selector = selectorArray.optJSONObject(i);
                if (selector != null) {
                    selectors.add(UiSelector.fromJson(selector));
                }
            }
        }
        return new ActionStep(
            json.optString("id", ""),
            json.optString("tool_name", ""),
            AutomationJson.copyObject(json.optJSONObject("arguments")),
            selectors,
            ScreenFingerprint.fromJson(json.optJSONObject("preconditions")),
            ScreenFingerprint.fromJson(json.optJSONObject("postconditions")),
            json.optInt("timeout_ms", 0),
            json.optString("fallback_policy", ""));
    }

    public JSONObject argumentsCopy() {
        return AutomationJson.copyObject(arguments);
    }

    private static List<UiSelector> copySelectors(List<UiSelector> selectors) {
        List<UiSelector> copy = new ArrayList<>();
        if (selectors == null) return copy;
        for (UiSelector selector : selectors) {
            if (selector != null) {
                copy.add(UiSelector.fromJson(selector.toJson()));
            }
        }
        return copy;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
