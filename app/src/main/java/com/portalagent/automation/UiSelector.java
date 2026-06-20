package com.portalagent.automation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

public final class UiSelector {

    public final String text;
    public final String contentDesc;
    public final String className;
    public final int[] bounds;
    public final String parentSummary;
    public final String screenFingerprint;
    public final int confidence;

    public UiSelector(String text, String contentDesc, String className, int[] bounds,
                      String parentSummary, String screenFingerprint, int confidence) {
        this.text = defaultString(text);
        this.contentDesc = defaultString(contentDesc);
        this.className = defaultString(className);
        this.bounds = copyBounds(bounds);
        this.parentSummary = defaultString(parentSummary);
        this.screenFingerprint = defaultString(screenFingerprint);
        this.confidence = confidence;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        AutomationJson.put(json, "text", text);
        AutomationJson.put(json, "content_desc", contentDesc);
        AutomationJson.put(json, "class", className);
        AutomationJson.put(json, "bounds", new JSONArray(Arrays.asList(bounds[0], bounds[1], bounds[2], bounds[3])));
        AutomationJson.put(json, "parent_summary", parentSummary);
        AutomationJson.put(json, "screen_fingerprint", screenFingerprint);
        AutomationJson.put(json, "confidence", confidence);
        return json;
    }

    public static UiSelector fromJson(JSONObject json) {
        if (json == null) json = new JSONObject();
        return new UiSelector(
            json.optString("text", ""),
            json.optString("content_desc", ""),
            json.optString("class", ""),
            readBounds(json.optJSONArray("bounds")),
            json.optString("parent_summary", ""),
            json.optString("screen_fingerprint", ""),
            json.optInt("confidence", 0));
    }

    public boolean hasStableAnchor() {
        return !text.isEmpty() || !contentDesc.isEmpty() || !parentSummary.isEmpty()
            || !screenFingerprint.isEmpty();
    }

    public int[] boundsCopy() {
        return copyBounds(bounds);
    }

    private static int[] readBounds(JSONArray array) {
        if (array == null || array.length() != 4) return new int[]{0, 0, 0, 0};
        for (int i = 0; i < 4; i++) {
            if (!(array.opt(i) instanceof Number)) return new int[]{0, 0, 0, 0};
        }
        return new int[]{
            array.optInt(0, 0),
            array.optInt(1, 0),
            array.optInt(2, 0),
            array.optInt(3, 0)
        };
    }

    private static int[] copyBounds(int[] bounds) {
        if (bounds == null || bounds.length != 4) return new int[]{0, 0, 0, 0};
        return Arrays.copyOf(bounds, 4);
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
