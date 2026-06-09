package com.termux.app.automation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScreenFingerprint {

    public final String packageName;
    public final String activityName;
    public final List<String> anchors;
    public final int clickableCount;
    public final int editableCount;
    public final String rootSummary;

    public ScreenFingerprint(String packageName, String activityName, List<String> anchors,
                             int clickableCount, int editableCount, String rootSummary) {
        this.packageName = defaultString(packageName);
        this.activityName = defaultString(activityName);
        this.anchors = Collections.unmodifiableList(copyStrings(anchors));
        this.clickableCount = clickableCount;
        this.editableCount = editableCount;
        this.rootSummary = defaultString(rootSummary);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        AutomationJson.put(json, "package", packageName);
        AutomationJson.put(json, "activity", activityName);
        AutomationJson.put(json, "anchors", new JSONArray(anchors));
        AutomationJson.put(json, "clickable_count", clickableCount);
        AutomationJson.put(json, "editable_count", editableCount);
        AutomationJson.put(json, "root_summary", rootSummary);
        return json;
    }

    public static ScreenFingerprint fromJson(JSONObject json) {
        if (json == null) json = new JSONObject();
        return new ScreenFingerprint(
            json.optString("package", ""),
            json.optString("activity", ""),
            readStrings(json.optJSONArray("anchors")),
            json.optInt("clickable_count", 0),
            json.optInt("editable_count", 0),
            json.optString("root_summary", ""));
    }

    public static ScreenFingerprint empty() {
        return new ScreenFingerprint("", "", Collections.<String>emptyList(), 0, 0, "");
    }

    public boolean containsAnchor(String anchor) {
        return anchor != null && anchors.contains(anchor);
    }

    static List<String> readStrings(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            values.add(array.optString(i, ""));
        }
        return values;
    }

    private static List<String> copyStrings(List<String> values) {
        List<String> copy = new ArrayList<>();
        if (values == null) return copy;
        for (String value : values) {
            copy.add(defaultString(value));
        }
        return copy;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
