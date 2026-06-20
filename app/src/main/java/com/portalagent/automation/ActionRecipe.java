package com.portalagent.automation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ActionRecipe {

    public final String id;
    public final String name;
    public final boolean enabled;
    public final boolean autoBoostEnabled;
    public final AutomationRiskLevel riskLevel;
    public final List<String> intentPatterns;
    public final String targetPackage;
    public final String targetActivity;
    public final ScreenFingerprint startConditions;
    public final ScreenFingerprint endConditions;
    public final List<ActionStep> steps;
    public final String source;
    public final List<String> sourceTaskIds;
    public final RecipeStats stats;
    public final String currentVersionId;
    public final JSONArray versions;

    public ActionRecipe(String id, String name, boolean enabled, boolean autoBoostEnabled,
                        AutomationRiskLevel riskLevel, List<String> intentPatterns,
                        String targetPackage, String targetActivity,
                        ScreenFingerprint startConditions, ScreenFingerprint endConditions,
                        List<ActionStep> steps, String source, List<String> sourceTaskIds,
                        RecipeStats stats, String currentVersionId, JSONArray versions) {
        this.id = defaultString(id);
        this.name = defaultString(name);
        this.enabled = enabled;
        this.autoBoostEnabled = autoBoostEnabled;
        this.riskLevel = riskLevel == null ? AutomationRiskLevel.MEDIUM : riskLevel;
        this.intentPatterns = Collections.unmodifiableList(copyStrings(intentPatterns));
        this.targetPackage = defaultString(targetPackage);
        this.targetActivity = defaultString(targetActivity);
        this.startConditions = startConditions == null ? ScreenFingerprint.empty() : startConditions;
        this.endConditions = endConditions == null ? ScreenFingerprint.empty() : endConditions;
        this.steps = Collections.unmodifiableList(copySteps(steps));
        this.source = defaultString(source);
        this.sourceTaskIds = Collections.unmodifiableList(copyStrings(sourceTaskIds));
        this.stats = stats == null ? RecipeStats.empty() : stats;
        this.currentVersionId = defaultString(currentVersionId);
        this.versions = AutomationJson.copyArray(versions);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        AutomationJson.put(json, "id", id);
        AutomationJson.put(json, "name", name);
        AutomationJson.put(json, "enabled", enabled);
        AutomationJson.put(json, "auto_boost_enabled", autoBoostEnabled);
        AutomationJson.put(json, "risk_level", riskLevel.name());
        AutomationJson.put(json, "intent_patterns", new JSONArray(intentPatterns));
        AutomationJson.put(json, "target_package", targetPackage);
        AutomationJson.put(json, "target_activity", targetActivity);
        AutomationJson.put(json, "start_conditions", startConditions.toJson());
        AutomationJson.put(json, "end_conditions", endConditions.toJson());
        JSONArray stepArray = new JSONArray();
        for (ActionStep step : steps) {
            stepArray.put(step.toJson());
        }
        AutomationJson.put(json, "steps", stepArray);
        AutomationJson.put(json, "source", source);
        AutomationJson.put(json, "source_task_ids", new JSONArray(sourceTaskIds));
        AutomationJson.put(json, "stats", stats.toJson());
        AutomationJson.put(json, "current_version_id", currentVersionId);
        AutomationJson.put(json, "versions", AutomationJson.copyArray(versions));
        return json;
    }

    public static ActionRecipe fromJson(JSONObject json) {
        if (json == null) json = new JSONObject();
        List<ActionStep> steps = new ArrayList<>();
        JSONArray stepArray = json.optJSONArray("steps");
        if (stepArray != null) {
            for (int i = 0; i < stepArray.length(); i++) {
                JSONObject step = stepArray.optJSONObject(i);
                if (step != null) {
                    steps.add(ActionStep.fromJson(step));
                }
            }
        }
        return new ActionRecipe(
            json.optString("id", ""),
            json.optString("name", ""),
            json.optBoolean("enabled", false),
            json.optBoolean("auto_boost_enabled", false),
            AutomationRiskLevel.fromString(json.optString("risk_level", null)),
            ScreenFingerprint.readStrings(json.optJSONArray("intent_patterns")),
            json.optString("target_package", ""),
            json.optString("target_activity", ""),
            ScreenFingerprint.fromJson(json.optJSONObject("start_conditions")),
            ScreenFingerprint.fromJson(json.optJSONObject("end_conditions")),
            steps,
            json.optString("source", ""),
            ScreenFingerprint.readStrings(json.optJSONArray("source_task_ids")),
            RecipeStats.fromJson(json.optJSONObject("stats")),
            json.optString("current_version_id", ""),
            AutomationJson.copyArray(json.optJSONArray("versions")));
    }

    public JSONArray versionsCopy() {
        return AutomationJson.copyArray(versions);
    }

    private static List<ActionStep> copySteps(List<ActionStep> steps) {
        List<ActionStep> copy = new ArrayList<>();
        if (steps == null) return copy;
        for (ActionStep step : steps) {
            if (step != null) {
                copy.add(ActionStep.fromJson(step.toJson()));
            }
        }
        return copy;
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
