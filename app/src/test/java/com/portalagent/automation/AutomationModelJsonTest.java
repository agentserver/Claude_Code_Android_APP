package com.portalagent.automation;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class AutomationModelJsonTest {

    @Test
    public void selectorRoundTripsAllStableFields() throws Exception {
        UiSelector selector = new UiSelector(
            "设置", "Settings", "TextView",
            new int[]{10, 20, 110, 80},
            "LinearLayout/TextView", "settings:main", 91);

        UiSelector parsed = UiSelector.fromJson(selector.toJson());

        Assert.assertEquals("设置", parsed.text);
        Assert.assertEquals("Settings", parsed.contentDesc);
        Assert.assertEquals("TextView", parsed.className);
        Assert.assertArrayEquals(new int[]{10, 20, 110, 80}, parsed.bounds);
        Assert.assertEquals("LinearLayout/TextView", parsed.parentSummary);
        Assert.assertEquals("settings:main", parsed.screenFingerprint);
        Assert.assertEquals(91, parsed.confidence);
    }

    @Test
    public void selectorInvalidBoundsElementDefaultsWholeBoundsArray() throws Exception {
        JSONObject json = new JSONObject()
            .put("bounds", new JSONArray().put("bad").put(2).put(3).put(4));

        UiSelector parsed = UiSelector.fromJson(json);

        Assert.assertArrayEquals(new int[]{0, 0, 0, 0}, parsed.bounds);
    }

    @Test
    public void selectorCopiesConstructorBoundsAndAccessorReturnsCopy() throws Exception {
        int[] bounds = new int[]{1, 2, 3, 4};
        UiSelector selector = new UiSelector("", "", "", bounds, "", "", 0);

        bounds[0] = 99;
        int[] copy = selector.boundsCopy();
        copy[1] = 88;

        Assert.assertArrayEquals(new int[]{1, 2, 3, 4}, selector.bounds);
    }

    @Test
    public void actionStepSkipsMalformedSelectorEntries() throws Exception {
        ActionStep parsed = ActionStep.fromJson(new JSONObject()
            .put("selectors", new JSONArray().put(123)));

        Assert.assertEquals(0, parsed.selectors.size());
    }

    @Test
    public void actionStepCopiesConstructorArgumentsAndAccessorReturnsCopy() throws Exception {
        JSONObject arguments = new JSONObject().put("text", "original");
        ActionStep step = new ActionStep("", "", arguments, null, null, null, 0, "");

        arguments.put("text", "mutated");
        JSONObject copy = step.argumentsCopy();
        copy.put("text", "copy-mutated");

        Assert.assertEquals("original", step.arguments.optString("text"));
    }

    @Test
    public void recipeRoundTripsStepsVersionsSourcesAndStats() throws Exception {
        ScreenFingerprint start = new ScreenFingerprint(
            "com.android.settings", "Settings", Arrays.asList("网络", "无障碍"),
            6, 0, "FrameLayout/ListView");
        ScreenFingerprint end = new ScreenFingerprint(
            "com.android.settings", "AccessibilitySettings", Arrays.asList("已下载的应用"),
            8, 0, "FrameLayout/RecyclerView");
        ActionStep step = new ActionStep(
            "step-1", "ui.click_text",
            new JSONObject().put("text", "无障碍").put("match_mode", "contains"),
            Arrays.asList(new UiSelector("无障碍", "", "TextView", new int[]{0, 0, 200, 80}, "", "", 85)),
            start, end, 3000, "fallback_agent");
        RecipeStats stats = new RecipeStats(2, 1, 1000L, 2000L, 450L, "Text not found");

        ActionRecipe recipe = new ActionRecipe(
            "recipe-1", "打开无障碍设置", true, false, AutomationRiskLevel.LOW,
            Arrays.asList("打开无障碍", "进入辅助功能"),
            "com.android.settings", "AccessibilitySettings",
            start, end, Arrays.asList(step), "agent_success",
            Arrays.asList("task-a"), stats, "v1", new JSONArray().put(new JSONObject().put("id", "v1")));

        ActionRecipe parsed = ActionRecipe.fromJson(recipe.toJson());

        Assert.assertEquals("recipe-1", parsed.id);
        Assert.assertEquals("打开无障碍设置", parsed.name);
        Assert.assertTrue(parsed.enabled);
        Assert.assertFalse(parsed.autoBoostEnabled);
        Assert.assertEquals(AutomationRiskLevel.LOW, parsed.riskLevel);
        Assert.assertEquals("com.android.settings", parsed.targetPackage);
        Assert.assertEquals("AccessibilitySettings", parsed.targetActivity);
        Assert.assertEquals(1, parsed.steps.size());
        Assert.assertEquals("ui.click_text", parsed.steps.get(0).toolName);
        Assert.assertEquals("task-a", parsed.sourceTaskIds.get(0));
        Assert.assertEquals(2, parsed.stats.successCount);
        Assert.assertEquals("v1", parsed.currentVersionId);
        Assert.assertEquals(1, parsed.versions.length());
    }

    @Test
    public void recipeSkipsMalformedStepEntries() throws Exception {
        ActionRecipe parsed = ActionRecipe.fromJson(new JSONObject()
            .put("steps", new JSONArray().put(123)));

        Assert.assertEquals(0, parsed.steps.size());
    }

    @Test
    public void recipeCopiesConstructorVersionsAndAccessorReturnsCopy() throws Exception {
        JSONArray versions = new JSONArray().put(new JSONObject().put("id", "v1"));
        ActionRecipe recipe = new ActionRecipe(
            "", "", false, false, AutomationRiskLevel.MEDIUM, null,
            "", "", null, null, null, "", null, null, "", versions);

        versions.getJSONObject(0).put("id", "mutated");
        JSONArray copy = recipe.versionsCopy();
        copy.getJSONObject(0).put("id", "copy-mutated");

        Assert.assertEquals("v1", recipe.versions.getJSONObject(0).optString("id"));
    }

    @Test
    public void traceEventRoundTripsResultAndTaskId() throws Exception {
        ToolTraceEvent event = new ToolTraceEvent(
            "trace-1", 1234L, "task-1", "ui.click_text",
            new JSONObject().put("text", "确定"),
            true, "Clicked element with text: \"确定\"",
            "com.android.settings", "Settings");

        ToolTraceEvent parsed = ToolTraceEvent.fromJson(event.toJson());

        Assert.assertEquals("trace-1", parsed.id);
        Assert.assertEquals(1234L, parsed.timestampMs);
        Assert.assertEquals("task-1", parsed.taskId);
        Assert.assertEquals("ui.click_text", parsed.toolName);
        Assert.assertTrue(parsed.success);
        Assert.assertEquals("确定", parsed.arguments.optString("text"));
        Assert.assertEquals("com.android.settings", parsed.packageName);
    }

    @Test
    public void traceEventCopiesConstructorArgumentsAndAccessorReturnsCopy() throws Exception {
        JSONObject arguments = new JSONObject().put("text", "original");
        ToolTraceEvent event = new ToolTraceEvent("", 0, "", "", arguments, false, "", "", "");

        arguments.put("text", "mutated");
        JSONObject copy = event.argumentsCopy();
        copy.put("text", "copy-mutated");

        Assert.assertEquals("original", event.arguments.optString("text"));
    }
}
