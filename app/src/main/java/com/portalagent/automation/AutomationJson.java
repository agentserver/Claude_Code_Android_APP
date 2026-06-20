package com.portalagent.automation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class AutomationJson {

    private AutomationJson() {}

    static JSONObject copyObject(JSONObject object) {
        if (object == null) return new JSONObject();
        try {
            return new JSONObject(object.toString());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static JSONArray copyArray(JSONArray array) {
        if (array == null) return new JSONArray();
        try {
            return new JSONArray(array.toString());
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to write JSON key: " + key, e);
        }
    }
}
