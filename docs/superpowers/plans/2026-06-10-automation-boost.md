# Automation Boost Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Automation Boost so repeated low-risk Android UI operations can be saved as validated action recipes, managed from a settings page, and executed quickly before falling back to Claude/Codex Agent exploration.

**Architecture:** Put reusable automation logic in `com.termux.app.automation`, below Home/Claude/Codex and above the Android MCP action layer. Record MCP tool traces, generate candidate recipes from successful turns, dedupe candidates into recipe versions, expose management in Settings, then let Home attempt whitelisted Boost execution before sending the prompt to the provider.

**Tech Stack:** Android Java, AndroidX Fragment/RecyclerView/Preference, Material Components, SharedPreferences, app-private JSON files, Robolectric/JUnit4, existing MCP tools and `FloatingStatusService`.

---

## File Structure

Create:

- `app/src/main/java/com/termux/app/automation/AutomationRiskLevel.java`
  - Enum for `LOW`, `MEDIUM`, `HIGH`.
- `app/src/main/java/com/termux/app/automation/AutomationStepStatus.java`
  - Enum for `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`.
- `app/src/main/java/com/termux/app/automation/UiSelector.java`
  - Serializable UI selector with text, content-desc, class, bounds, parent summary, and confidence.
- `app/src/main/java/com/termux/app/automation/ScreenFingerprint.java`
  - Serializable page summary used for start/end validation.
- `app/src/main/java/com/termux/app/automation/ActionStep.java`
  - Serializable tool action with arguments, selectors, preconditions, postconditions, and timeout.
- `app/src/main/java/com/termux/app/automation/RecipeStats.java`
  - Serializable success/failure counters and timestamps.
- `app/src/main/java/com/termux/app/automation/ActionRecipe.java`
  - Serializable recipe, including current version, versions, source task IDs, and stats.
- `app/src/main/java/com/termux/app/automation/ToolTraceEvent.java`
  - Serializable MCP call trace row.
- `app/src/main/java/com/termux/app/automation/AutomationStore.java`
  - Reads/writes `files/automation/recipes.json`, `candidates.json`, and `failures.jsonl`.
- `app/src/main/java/com/termux/app/automation/AutomationSettingsStore.java`
  - SharedPreferences wrapper for global enablement, app whitelist, and recipe whitelist.
- `app/src/main/java/com/termux/app/automation/ToolTraceStore.java`
  - Appends JSONL MCP traces and loads traces by time window or task ID.
- `app/src/main/java/com/termux/app/automation/AutomationPolicy.java`
  - Risk classification, sensitive input redaction, and recipe admission rules.
- `app/src/main/java/com/termux/app/automation/RecipeDedupe.java`
  - Candidate similarity scoring and version merge logic.
- `app/src/main/java/com/termux/app/automation/AutomationCandidateGenerator.java`
  - Turns successful tool traces into candidate action recipes.
- `app/src/main/java/com/termux/app/automation/AutomationRuntime.java`
  - Home-facing coordinator: start turn tracking, attempt Boost, generate candidates on completion.
- `app/src/main/java/com/termux/app/automation/AndroidActionRunner.java`
  - Interface used by `BoostExecutor` to run app/ui actions and read current screen state.
- `app/src/main/java/com/termux/app/automation/AndroidMcpActionRunner.java`
  - Android implementation backed by existing MCP services/tools.
- `app/src/main/java/com/termux/app/automation/BoostExecutor.java`
  - Executes recipes step-by-step, updates status, stops on failure, records failure.
- `app/src/main/java/com/termux/app/automation/BoostResult.java`
  - Immutable result for success/failure/fallback.
- `app/src/main/java/com/termux/app/AutomationSettingsFragment.java`
  - Settings UI for Boost global switch, whitelist, candidates, recipes, failures.
- `app/src/main/java/com/termux/app/AutomationRecipeAdapter.java`
  - RecyclerView adapter for candidate and enabled recipe rows.
- `app/src/main/res/layout/fragment_automation_settings.xml`
  - Automation settings page.
- `app/src/main/res/layout/item_automation_recipe.xml`
  - Recipe/candidate row.
- `app/src/main/res/layout/item_automation_failure.xml`
  - Failure row.
- `app/src/test/java/com/termux/app/automation/AutomationModelJsonTest.java`
- `app/src/test/java/com/termux/app/automation/AutomationPolicyTest.java`
- `app/src/test/java/com/termux/app/automation/RecipeDedupeTest.java`
- `app/src/test/java/com/termux/app/automation/AutomationStoreTest.java`
- `app/src/test/java/com/termux/app/automation/ToolTraceStoreTest.java`
- `app/src/test/java/com/termux/app/automation/AutomationCandidateGeneratorTest.java`
- `app/src/test/java/com/termux/app/automation/BoostExecutorTest.java`
- `app/src/test/java/com/termux/app/automation/AutomationRuntimeTest.java`

Modify:

- `app/src/main/java/com/termux/app/mcp/McpHttpServer.java`
  - Record every `tools/call` into `ToolTraceStore`.
- `app/src/main/java/com/termux/app/mcp/tools/UiTreeTool.java`
  - Reuse fingerprint helper when needed; do not change public MCP schema.
- `app/src/main/java/com/termux/app/mcp/McpAccessibilityService.java`
  - Add safe helpers to read current screen fingerprint and selector presence.
- `app/src/main/java/com/termux/app/FloatingStatusService.java`
  - Add Boost title/status updates and force-visible overlay mode while boosting.
- `app/src/main/java/com/termux/app/HomeFragment.java`
  - Add settings icon, center the status indicator, attempt Boost before provider send, generate candidates after successful provider turns.
- `app/src/main/java/com/termux/app/activities/SettingsActivity.java`
  - Add click handler for Automation Boost settings entry.
- `app/src/main/res/xml/root_preferences.xml`
  - Add Automation Boost preference entry.
- `app/src/main/res/layout/fragment_home.xml`
  - Move status indicator to the top bar center and add right settings icon.
- `app/src/main/res/layout/layout_floating_status.xml`
  - Allow dynamic title text for Boost.
- `app/src/main/res/values/strings.xml`
  - Add Automation Boost labels.

---

### Task 1: Automation Core Model

**Files:**
- Create: `app/src/main/java/com/termux/app/automation/AutomationRiskLevel.java`
- Create: `app/src/main/java/com/termux/app/automation/AutomationStepStatus.java`
- Create: `app/src/main/java/com/termux/app/automation/UiSelector.java`
- Create: `app/src/main/java/com/termux/app/automation/ScreenFingerprint.java`
- Create: `app/src/main/java/com/termux/app/automation/ActionStep.java`
- Create: `app/src/main/java/com/termux/app/automation/RecipeStats.java`
- Create: `app/src/main/java/com/termux/app/automation/ActionRecipe.java`
- Create: `app/src/main/java/com/termux/app/automation/ToolTraceEvent.java`
- Test: `app/src/test/java/com/termux/app/automation/AutomationModelJsonTest.java`

- [ ] **Step 1: Write failing JSON round-trip tests**

Create `app/src/test/java/com/termux/app/automation/AutomationModelJsonTest.java`:

```java
package com.termux.app.automation;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

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
}
```

- [ ] **Step 2: Run the model test and verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.AutomationModelJsonTest
```

Expected: compile failure mentioning missing `com.termux.app.automation` model classes.

- [ ] **Step 3: Add enums**

Create `app/src/main/java/com/termux/app/automation/AutomationRiskLevel.java`:

```java
package com.termux.app.automation;

public enum AutomationRiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static AutomationRiskLevel fromString(String value) {
        if (value == null) return MEDIUM;
        try {
            return AutomationRiskLevel.valueOf(value);
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
```

Create `app/src/main/java/com/termux/app/automation/AutomationStepStatus.java`:

```java
package com.termux.app.automation;

public enum AutomationStepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}
```

- [ ] **Step 4: Add serializable selector and fingerprint models**

Create `app/src/main/java/com/termux/app/automation/UiSelector.java`:

```java
package com.termux.app.automation;

import org.json.JSONArray;
import org.json.JSONObject;

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
        this.text = text != null ? text : "";
        this.contentDesc = contentDesc != null ? contentDesc : "";
        this.className = className != null ? className : "";
        this.bounds = bounds != null && bounds.length == 4 ? bounds : new int[]{0, 0, 0, 0};
        this.parentSummary = parentSummary != null ? parentSummary : "";
        this.screenFingerprint = screenFingerprint != null ? screenFingerprint : "";
        this.confidence = confidence;
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("text", text);
        o.put("content_desc", contentDesc);
        o.put("class", className);
        JSONArray b = new JSONArray();
        for (int value : bounds) b.put(value);
        o.put("bounds", b);
        o.put("parent_summary", parentSummary);
        o.put("screen_fingerprint", screenFingerprint);
        o.put("confidence", confidence);
        return o;
    }

    public static UiSelector fromJson(JSONObject o) {
        if (o == null) return new UiSelector("", "", "", null, "", "", 0);
        JSONArray b = o.optJSONArray("bounds");
        int[] bounds = new int[]{0, 0, 0, 0};
        if (b != null && b.length() == 4) {
            for (int i = 0; i < 4; i++) bounds[i] = b.optInt(i, 0);
        }
        return new UiSelector(
            o.optString("text", ""),
            o.optString("content_desc", ""),
            o.optString("class", ""),
            bounds,
            o.optString("parent_summary", ""),
            o.optString("screen_fingerprint", ""),
            o.optInt("confidence", 0));
    }

    public boolean hasStableAnchor() {
        return !text.isEmpty() || !contentDesc.isEmpty() || !className.isEmpty();
    }
}
```

Create `app/src/main/java/com/termux/app/automation/ScreenFingerprint.java`:

```java
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
        this.packageName = packageName != null ? packageName : "";
        this.activityName = activityName != null ? activityName : "";
        this.anchors = anchors != null ? new ArrayList<>(anchors) : new ArrayList<>();
        this.clickableCount = clickableCount;
        this.editableCount = editableCount;
        this.rootSummary = rootSummary != null ? rootSummary : "";
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("package", packageName);
        o.put("activity", activityName);
        JSONArray a = new JSONArray();
        for (String anchor : anchors) a.put(anchor);
        o.put("anchors", a);
        o.put("clickable_count", clickableCount);
        o.put("editable_count", editableCount);
        o.put("root_summary", rootSummary);
        return o;
    }

    public static ScreenFingerprint fromJson(JSONObject o) {
        if (o == null) return empty();
        List<String> anchors = new ArrayList<>();
        JSONArray a = o.optJSONArray("anchors");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                String value = a.optString(i, "");
                if (!value.isEmpty()) anchors.add(value);
            }
        }
        return new ScreenFingerprint(
            o.optString("package", ""),
            o.optString("activity", ""),
            anchors,
            o.optInt("clickable_count", 0),
            o.optInt("editable_count", 0),
            o.optString("root_summary", ""));
    }

    public static ScreenFingerprint empty() {
        return new ScreenFingerprint("", "", Collections.emptyList(), 0, 0, "");
    }

    public boolean containsAnchor(String value) {
        if (value == null || value.isEmpty()) return false;
        for (String anchor : anchors) {
            if (anchor.contains(value) || value.contains(anchor)) return true;
        }
        return false;
    }
}
```

- [ ] **Step 5: Add serializable step, stats, recipe, and trace models**

Create `ActionStep.java`, `RecipeStats.java`, `ActionRecipe.java`, and `ToolTraceEvent.java` with the fields tested in Step 1, `toJson()` methods that write every public field, and `fromJson(JSONObject)` methods that default missing strings to `""`, booleans to `false`, and missing lists to empty lists. Use the same JSON keys shown in the tests: `tool_name`, `arguments`, `selectors`, `preconditions`, `postconditions`, `timeout_ms`, `fallback_policy`, `intent_patterns`, `target_package`, `target_activity`, `source_task_ids`, `current_version_id`, `versions`, `timestamp_ms`, `task_id`, `success`, `result_summary`, `package`, and `activity`.

The `ActionRecipe` constructor must copy incoming `List` values into new `ArrayList` instances and must ensure `stats` is never `null` by replacing `null` with `new RecipeStats(0, 0, 0, 0, 0, "")`.

- [ ] **Step 6: Run the model test and verify it passes**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.AutomationModelJsonTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit the core model**

Run:

```powershell
git add app/src/main/java/com/termux/app/automation app/src/test/java/com/termux/app/automation/AutomationModelJsonTest.java
git commit -m "feat: add automation recipe model"
```

---

### Task 2: Policy, Selector Matching, and Candidate Dedupe

**Files:**
- Create: `app/src/main/java/com/termux/app/automation/AutomationPolicy.java`
- Create: `app/src/main/java/com/termux/app/automation/RecipeDedupe.java`
- Test: `app/src/test/java/com/termux/app/automation/AutomationPolicyTest.java`
- Test: `app/src/test/java/com/termux/app/automation/RecipeDedupeTest.java`

- [ ] **Step 1: Write policy tests**

Create `app/src/test/java/com/termux/app/automation/AutomationPolicyTest.java`:

```java
package com.termux.app.automation;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class AutomationPolicyTest {

    @Test
    public void inputTextIsHighRiskAndRedacted() throws Exception {
        ActionStep step = new ActionStep(
            "s", "ui.input_text", new JSONObject().put("text", "secret-token"),
            Arrays.asList(new UiSelector("", "", "EditText", new int[]{0, 0, 1, 1}, "", "", 80)),
            ScreenFingerprint.empty(), ScreenFingerprint.empty(), 3000, "fallback_agent");

        Assert.assertEquals(AutomationRiskLevel.HIGH, AutomationPolicy.classifyStep(step));
        JSONObject redacted = AutomationPolicy.redactArguments("ui.input_text", step.arguments);
        Assert.assertEquals("[redacted]", redacted.optString("text"));
    }

    @Test
    public void clickTextWithStableSelectorIsLowRisk() throws Exception {
        ActionStep step = new ActionStep(
            "s", "ui.click_text", new JSONObject().put("text", "无障碍"),
            Arrays.asList(new UiSelector("无障碍", "", "TextView", new int[]{0, 0, 1, 1}, "", "", 80)),
            ScreenFingerprint.empty(), ScreenFingerprint.empty(), 3000, "fallback_agent");

        Assert.assertEquals(AutomationRiskLevel.LOW, AutomationPolicy.classifyStep(step));
        Assert.assertTrue(AutomationPolicy.hasStableSelector(step));
    }

    @Test
    public void recipeCannotAutoBoostWithoutEndCondition() throws Exception {
        ActionRecipe recipe = new ActionRecipe(
            "r", "打开页面", true, true, AutomationRiskLevel.LOW,
            Arrays.asList("打开页面"), "pkg", "Activity",
            ScreenFingerprint.empty(), ScreenFingerprint.empty(),
            Arrays.asList(), "agent_success", Arrays.asList(), new RecipeStats(0, 0, 0, 0, 0, ""),
            "v1", null);

        Assert.assertFalse(AutomationPolicy.canAutoBoost(recipe));
    }
}
```

- [ ] **Step 2: Write dedupe tests**

Create `app/src/test/java/com/termux/app/automation/RecipeDedupeTest.java`:

```java
package com.termux.app.automation;

import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class RecipeDedupeTest {

    @Test
    public void similarSettingsRecipesMergeAsVersions() throws Exception {
        ScreenFingerprint end = new ScreenFingerprint(
            "com.android.settings", "AccessibilitySettings",
            Arrays.asList("已下载的应用", "Claude Code Test"), 7, 0, "root");
        ActionRecipe existing = recipe("r1", "打开无障碍设置", AutomationRiskLevel.LOW, end, "v1");
        ActionRecipe candidate = recipe("r2", "进入辅助功能权限页", AutomationRiskLevel.LOW, end, "v2");

        Assert.assertTrue(RecipeDedupe.shouldMerge(existing, candidate));
        ActionRecipe merged = RecipeDedupe.mergeAsVersion(existing, candidate);

        Assert.assertEquals("r1", merged.id);
        Assert.assertEquals(2, merged.versions.length());
        Assert.assertEquals("v1", merged.currentVersionId);
        Assert.assertEquals(2, merged.sourceTaskIds.size());
    }

    @Test
    public void differentRiskRecipesDoNotMerge() throws Exception {
        ScreenFingerprint end = new ScreenFingerprint("pkg", "Activity", Arrays.asList("目标"), 1, 0, "");
        ActionRecipe low = recipe("r1", "打开页面", AutomationRiskLevel.LOW, end, "v1");
        ActionRecipe high = recipe("r2", "打开页面并发送", AutomationRiskLevel.HIGH, end, "v2");

        Assert.assertFalse(RecipeDedupe.shouldMerge(low, high));
    }

    private static ActionRecipe recipe(String id, String name, AutomationRiskLevel risk,
                                       ScreenFingerprint end, String version) throws Exception {
        JSONArray versions = new JSONArray().put(RecipeDedupe.versionJson(version, Arrays.asList()));
        return new ActionRecipe(
            id, name, true, false, risk, Arrays.asList(name),
            end.packageName, end.activityName, ScreenFingerprint.empty(), end,
            Arrays.asList(), "agent_success", Arrays.asList(id + "-task"),
            new RecipeStats(0, 0, 0, 0, 0, ""), version, versions);
    }
}
```

- [ ] **Step 3: Run tests and verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.AutomationPolicyTest --tests com.termux.app.automation.RecipeDedupeTest
```

Expected: compile failure for missing `AutomationPolicy` and `RecipeDedupe`.

- [ ] **Step 4: Implement `AutomationPolicy`**

Create `app/src/main/java/com/termux/app/automation/AutomationPolicy.java`:

```java
package com.termux.app.automation;

import org.json.JSONObject;

public final class AutomationPolicy {
    private AutomationPolicy() {}

    public static AutomationRiskLevel classifyStep(ActionStep step) {
        if (step == null) return AutomationRiskLevel.HIGH;
        String tool = step.toolName;
        if ("ui.input_text".equals(tool)) return AutomationRiskLevel.HIGH;
        String text = step.arguments.optString("text", "").toLowerCase();
        if (containsHighRiskWord(text)) return AutomationRiskLevel.HIGH;
        if ("app.open".equals(tool) || "ui.click_text".equals(tool) || "ui.tap".equals(tool) || "ui.swipe".equals(tool)) {
            return AutomationRiskLevel.LOW;
        }
        return AutomationRiskLevel.MEDIUM;
    }

    public static AutomationRiskLevel classifyRecipe(ActionRecipe recipe) {
        if (recipe == null) return AutomationRiskLevel.HIGH;
        AutomationRiskLevel result = AutomationRiskLevel.LOW;
        for (ActionStep step : recipe.steps) {
            AutomationRiskLevel risk = classifyStep(step);
            if (risk == AutomationRiskLevel.HIGH) return AutomationRiskLevel.HIGH;
            if (risk == AutomationRiskLevel.MEDIUM) result = AutomationRiskLevel.MEDIUM;
        }
        return result;
    }

    public static JSONObject redactArguments(String toolName, JSONObject args) throws Exception {
        JSONObject copy = args == null ? new JSONObject() : new JSONObject(args.toString());
        if ("ui.input_text".equals(toolName) && copy.has("text")) {
            copy.put("text", "[redacted]");
        }
        return copy;
    }

    public static boolean hasStableSelector(ActionStep step) {
        if (step == null) return false;
        for (UiSelector selector : step.selectors) {
            if (selector.hasStableAnchor()) return true;
        }
        String text = step.arguments.optString("text", "");
        return !text.isEmpty() && !"ui.tap".equals(step.toolName);
    }

    public static boolean canAutoBoost(ActionRecipe recipe) {
        if (recipe == null || !recipe.enabled || !recipe.autoBoostEnabled) return false;
        if (recipe.riskLevel != AutomationRiskLevel.LOW) return false;
        if (recipe.endConditions == null || recipe.endConditions.anchors.isEmpty()) return false;
        for (ActionStep step : recipe.steps) {
            if (classifyStep(step) != AutomationRiskLevel.LOW) return false;
            if (!hasStableSelector(step) && "ui.tap".equals(step.toolName)) return false;
        }
        return true;
    }

    private static boolean containsHighRiskWord(String text) {
        return text.contains("支付") || text.contains("删除") || text.contains("发送")
            || text.contains("转账") || text.contains("确认下单") || text.contains("授权")
            || text.contains("password") || text.contains("token") || text.contains("验证码");
    }
}
```

- [ ] **Step 5: Implement `RecipeDedupe`**

Create `app/src/main/java/com/termux/app/automation/RecipeDedupe.java`:

```java
package com.termux.app.automation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RecipeDedupe {
    private RecipeDedupe() {}

    public static boolean shouldMerge(ActionRecipe existing, ActionRecipe candidate) {
        if (existing == null || candidate == null) return false;
        if (existing.riskLevel != candidate.riskLevel) return false;
        if (!safeEquals(existing.targetPackage, candidate.targetPackage)) return false;
        if (!safeEquals(existing.endConditions.packageName, candidate.endConditions.packageName)) return false;
        int score = 0;
        if (safeEquals(existing.endConditions.activityName, candidate.endConditions.activityName)) score += 2;
        if (anchorOverlap(existing.endConditions.anchors, candidate.endConditions.anchors) > 0) score += 3;
        if (toolSequence(existing).equals(toolSequence(candidate))) score += 2;
        if (intentOverlap(existing.intentPatterns, candidate.intentPatterns)) score += 1;
        return score >= 4;
    }

    public static ActionRecipe mergeAsVersion(ActionRecipe existing, ActionRecipe candidate) throws Exception {
        JSONArray versions = new JSONArray(existing.versions != null ? existing.versions.toString() : "[]");
        String versionId = candidate.currentVersionId == null || candidate.currentVersionId.isEmpty()
            ? "v" + (versions.length() + 1)
            : candidate.currentVersionId;
        versions.put(versionJson(versionId, candidate.steps));

        List<String> sourceTaskIds = new ArrayList<>(existing.sourceTaskIds);
        for (String id : candidate.sourceTaskIds) {
            if (!sourceTaskIds.contains(id)) sourceTaskIds.add(id);
        }
        return new ActionRecipe(
            existing.id, existing.name, existing.enabled, existing.autoBoostEnabled,
            existing.riskLevel, existing.intentPatterns, existing.targetPackage,
            existing.targetActivity, existing.startConditions, existing.endConditions,
            existing.steps, existing.source, sourceTaskIds, existing.stats,
            existing.currentVersionId, versions);
    }

    public static JSONObject versionJson(String versionId, List<ActionStep> steps) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", versionId);
        JSONArray arr = new JSONArray();
        if (steps != null) {
            for (ActionStep step : steps) arr.put(step.toJson());
        }
        o.put("steps", arr);
        o.put("created_at", System.currentTimeMillis());
        return o;
    }

    private static List<String> toolSequence(ActionRecipe recipe) {
        List<String> out = new ArrayList<>();
        for (ActionStep step : recipe.steps) out.add(step.toolName);
        return out;
    }

    private static int anchorOverlap(List<String> a, List<String> b) {
        Set<String> normalized = new HashSet<>();
        for (String value : a) normalized.add(normalize(value));
        int count = 0;
        for (String value : b) {
            if (normalized.contains(normalize(value))) count++;
        }
        return count;
    }

    private static boolean intentOverlap(List<String> a, List<String> b) {
        for (String left : a) {
            String nl = normalize(left);
            for (String right : b) {
                String nr = normalize(right);
                if (!nl.isEmpty() && !nr.isEmpty() && (nl.contains(nr) || nr.contains(nl))) return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace(" ", "").replace("设置", "").trim().toLowerCase();
    }

    private static boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
```

- [ ] **Step 6: Run policy and dedupe tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.AutomationPolicyTest --tests com.termux.app.automation.RecipeDedupeTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit policy and dedupe**

Run:

```powershell
git add app/src/main/java/com/termux/app/automation app/src/test/java/com/termux/app/automation
git commit -m "feat: add automation recipe policy"
```

---

### Task 3: Automation Storage

**Files:**
- Create: `app/src/main/java/com/termux/app/automation/AutomationStore.java`
- Create: `app/src/main/java/com/termux/app/automation/AutomationSettingsStore.java`
- Test: `app/src/test/java/com/termux/app/automation/AutomationStoreTest.java`

- [ ] **Step 1: Write store tests**

Create `app/src/test/java/com/termux/app/automation/AutomationStoreTest.java`:

```java
package com.termux.app.automation;

import android.content.Context;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class AutomationStoreTest {

    @Test
    public void recipesCandidatesAndFailuresPersistInAppFiles() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        deleteDir(new File(context.getFilesDir(), "automation"));
        AutomationStore store = new AutomationStore(context);
        ActionRecipe recipe = recipe("recipe-1", "打开设置");

        store.saveRecipes(Arrays.asList(recipe));
        store.saveCandidates(Arrays.asList(recipe));
        store.appendFailure("recipe-1", "step-1", "Text not found", ScreenFingerprint.empty());

        Assert.assertEquals(1, store.loadRecipes().size());
        Assert.assertEquals("打开设置", store.loadRecipes().get(0).name);
        Assert.assertEquals(1, store.loadCandidates().size());
        Assert.assertEquals(1, store.loadFailures().size());
        Assert.assertTrue(store.loadFailures().get(0).optString("reason").contains("Text not found"));
    }

    @Test
    public void settingsStorePersistsGlobalAndWhitelistFlags() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(AutomationSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit();
        AutomationSettingsStore store = new AutomationSettingsStore(context);

        store.setBoostEnabled(true);
        store.setAppWhitelisted("com.android.settings", true);
        store.setRecipeWhitelisted("recipe-1", true);

        AutomationSettingsStore next = new AutomationSettingsStore(context);
        Assert.assertTrue(next.isBoostEnabled());
        Assert.assertTrue(next.isAppWhitelisted("com.android.settings"));
        Assert.assertTrue(next.isRecipeWhitelisted("recipe-1"));
        Assert.assertFalse(next.isAppWhitelisted("com.example.other"));
    }

    private static ActionRecipe recipe(String id, String name) throws Exception {
        ScreenFingerprint end = new ScreenFingerprint("pkg", "Activity", Arrays.asList("完成"), 1, 0, "");
        return new ActionRecipe(id, name, true, false, AutomationRiskLevel.LOW,
            Arrays.asList(name), "pkg", "Activity", ScreenFingerprint.empty(), end,
            Arrays.asList(), "agent_success", Arrays.asList(), new RecipeStats(0, 0, 0, 0, 0, ""),
            "v1", null);
    }

    private static void deleteDir(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteDir(child);
        }
        file.delete();
    }
}
```

- [ ] **Step 2: Run the store test and verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.AutomationStoreTest
```

Expected: compile failure for missing `AutomationStore` and `AutomationSettingsStore`.

- [ ] **Step 3: Implement `AutomationStore`**

Create `app/src/main/java/com/termux/app/automation/AutomationStore.java` with these methods:

```java
public synchronized List<ActionRecipe> loadRecipes()
public synchronized void saveRecipes(List<ActionRecipe> recipes)
public synchronized List<ActionRecipe> loadCandidates()
public synchronized void saveCandidates(List<ActionRecipe> candidates)
public synchronized void appendFailure(String recipeId, String stepId, String reason, ScreenFingerprint fingerprint)
public synchronized List<JSONObject> loadFailures()
public File automationDir()
```

Implementation details:

- Base directory: `new File(context.getApplicationContext().getFilesDir(), "automation")`.
- Files: `recipes.json`, `candidates.json`, `failures.jsonl`.
- JSON array files use `ActionRecipe.toJson()` and `ActionRecipe.fromJson()`.
- Failure rows include `timestamp_ms`, `recipe_id`, `step_id`, `reason`, and `fingerprint`.
- Use `java.nio.file.Files.readAllBytes` and `Files.write` because the project already targets Java 8 with desugaring.
- On corrupt JSON, return an empty list and do not throw to callers.

- [ ] **Step 4: Implement `AutomationSettingsStore`**

Create `app/src/main/java/com/termux/app/automation/AutomationSettingsStore.java`:

```java
package com.termux.app.automation;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class AutomationSettingsStore {
    public static final String PREFS_NAME = "automation_boost";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_APP_WHITELIST = "app_whitelist";
    private static final String KEY_RECIPE_WHITELIST = "recipe_whitelist";

    private final SharedPreferences prefs;

    public AutomationSettingsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isBoostEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setBoostEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public boolean isAppWhitelisted(String packageName) {
        return prefs.getStringSet(KEY_APP_WHITELIST, new HashSet<>()).contains(packageName);
    }

    public void setAppWhitelisted(String packageName, boolean enabled) {
        updateSet(KEY_APP_WHITELIST, packageName, enabled);
    }

    public boolean isRecipeWhitelisted(String recipeId) {
        return prefs.getStringSet(KEY_RECIPE_WHITELIST, new HashSet<>()).contains(recipeId);
    }

    public void setRecipeWhitelisted(String recipeId, boolean enabled) {
        updateSet(KEY_RECIPE_WHITELIST, recipeId, enabled);
    }

    public Set<String> appWhitelist() {
        return new HashSet<>(prefs.getStringSet(KEY_APP_WHITELIST, new HashSet<>()));
    }

    private void updateSet(String key, String value, boolean enabled) {
        if (value == null || value.isEmpty()) return;
        Set<String> values = new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
        if (enabled) values.add(value); else values.remove(value);
        prefs.edit().putStringSet(key, values).apply();
    }
}
```

- [ ] **Step 5: Run the store test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.AutomationStoreTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit storage**

Run:

```powershell
git add app/src/main/java/com/termux/app/automation app/src/test/java/com/termux/app/automation/AutomationStoreTest.java
git commit -m "feat: add automation storage"
```

---

### Task 4: MCP Tool Trace Recording

**Files:**
- Create: `app/src/main/java/com/termux/app/automation/ToolTraceStore.java`
- Modify: `app/src/main/java/com/termux/app/mcp/McpHttpServer.java`
- Test: `app/src/test/java/com/termux/app/automation/ToolTraceStoreTest.java`

- [ ] **Step 1: Write trace store tests**

Create `app/src/test/java/com/termux/app/automation/ToolTraceStoreTest.java`:

```java
package com.termux.app.automation;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ToolTraceStoreTest {

    @Test
    public void appendsAndLoadsRecentTraces() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        deleteDir(new File(context.getFilesDir(), "automation"));
        ToolTraceStore store = new ToolTraceStore(context);

        store.append(new ToolTraceEvent("old", 100L, "", "ui.click_text",
            new JSONObject().put("text", "旧"), true, "ok", "pkg", "Activity"));
        store.append(new ToolTraceEvent("new", 300L, "task", "ui.click_text",
            new JSONObject().put("text", "新"), true, "ok", "pkg", "Activity"));

        List<ToolTraceEvent> recent = store.loadBetween(200L, 400L);

        Assert.assertEquals(1, recent.size());
        Assert.assertEquals("new", recent.get(0).id);
        Assert.assertEquals("新", recent.get(0).arguments.optString("text"));
    }

    @Test
    public void redactsInputTextBeforePersisting() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        deleteDir(new File(context.getFilesDir(), "automation"));
        ToolTraceStore store = new ToolTraceStore(context);

        store.append(new ToolTraceEvent("input", 1L, "", "ui.input_text",
            new JSONObject().put("text", "secret"), true, "ok", "pkg", "Activity"));

        List<ToolTraceEvent> traces = store.loadBetween(0L, 10L);
        Assert.assertEquals("[redacted]", traces.get(0).arguments.optString("text"));
    }

    private static void deleteDir(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteDir(child);
        }
        file.delete();
    }
}
```

- [ ] **Step 2: Run trace tests and verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.ToolTraceStoreTest
```

Expected: compile failure for missing `ToolTraceStore`.

- [ ] **Step 3: Implement `ToolTraceStore`**

Create `app/src/main/java/com/termux/app/automation/ToolTraceStore.java` with:

```java
public synchronized void append(ToolTraceEvent event)
public synchronized List<ToolTraceEvent> loadBetween(long startMs, long endMs)
public synchronized List<ToolTraceEvent> loadByTaskId(String taskId)
```

Implementation details:

- File path: `files/automation/traces.jsonl`.
- Before writing, call `AutomationPolicy.redactArguments(event.toolName, event.arguments)`.
- Keep the same event ID, timestamp, task ID, success flag, result summary, package, and activity.
- Parse JSONL line by line; skip corrupt lines.
- `loadBetween` includes both boundaries: `timestampMs >= startMs && timestampMs <= endMs`.

- [ ] **Step 4: Integrate trace recording into `McpHttpServer`**

Modify `app/src/main/java/com/termux/app/mcp/McpHttpServer.java`:

Add import:

```java
import com.termux.app.automation.ToolTraceEvent;
import com.termux.app.automation.ToolTraceStore;
```

Add field:

```java
private final ToolTraceStore mTraceStore;
```

Initialize in constructor:

```java
mTraceStore = new ToolTraceStore(mContext);
```

After `mAudit.log(toolName, taskId, success, success ? null : contentStr);`, append:

```java
try {
    String pkg = "";
    String activity = "";
    if (com.termux.app.mcp.McpAccessibilityService.isRunning()) {
        com.termux.app.mcp.McpAccessibilityService svc =
            com.termux.app.mcp.McpAccessibilityService.getInstance();
        pkg = svc.getCurrentPackage();
        activity = svc.getCurrentActivity();
    }
    mTraceStore.append(new ToolTraceEvent(
        java.util.UUID.randomUUID().toString(),
        System.currentTimeMillis(),
        taskId,
        toolName,
        args,
        success,
        summarizeTraceResult(contentStr),
        pkg,
        activity));
} catch (Exception e) {
    Log.w(TAG, "Trace write failed: " + e.getMessage());
}
```

Add helper near the bottom:

```java
private static String summarizeTraceResult(String contentStr) {
    if (contentStr == null) return "";
    String value = contentStr.replace('\n', ' ').trim();
    if (value.length() > 240) value = value.substring(0, 237) + "...";
    return value;
}
```

- [ ] **Step 5: Run trace tests and full automation model tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.ToolTraceStoreTest --tests com.termux.app.automation.AutomationModelJsonTest --tests com.termux.app.automation.AutomationPolicyTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit trace recording**

Run:

```powershell
git add app/src/main/java/com/termux/app/automation/ToolTraceStore.java app/src/main/java/com/termux/app/mcp/McpHttpServer.java app/src/test/java/com/termux/app/automation/ToolTraceStoreTest.java
git commit -m "feat: record automation tool traces"
```

---

### Task 5: Candidate Generation From Successful Traces

**Files:**
- Create: `app/src/main/java/com/termux/app/automation/AutomationCandidateGenerator.java`
- Test: `app/src/test/java/com/termux/app/automation/AutomationCandidateGeneratorTest.java`

- [ ] **Step 1: Write candidate generator tests**

Create `app/src/test/java/com/termux/app/automation/AutomationCandidateGeneratorTest.java`:

```java
package com.termux.app.automation;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AutomationCandidateGeneratorTest {

    @Test
    public void createsLowRiskCandidateFromOpenAndClickTrace() throws Exception {
        List<ToolTraceEvent> traces = Arrays.asList(
            new ToolTraceEvent("1", 100L, "", "app.open",
                new JSONObject().put("package_name", "com.android.settings"),
                true, "Launched app", "com.android.settings", "Settings"),
            new ToolTraceEvent("2", 200L, "", "ui.click_text",
                new JSONObject().put("text", "无障碍").put("match_mode", "contains"),
                true, "Clicked element", "com.android.settings", "Settings")
        );

        ActionRecipe recipe = AutomationCandidateGenerator.fromSuccessfulTurn(
            "打开无障碍设置", "task-1", traces);

        Assert.assertNotNull(recipe);
        Assert.assertFalse(recipe.id.isEmpty());
        Assert.assertEquals(AutomationRiskLevel.LOW, recipe.riskLevel);
        Assert.assertFalse(recipe.autoBoostEnabled);
        Assert.assertEquals("com.android.settings", recipe.targetPackage);
        Assert.assertEquals(2, recipe.steps.size());
        Assert.assertEquals("打开无障碍设置", recipe.intentPatterns.get(0));
        Assert.assertEquals("task-1", recipe.sourceTaskIds.get(0));
        Assert.assertFalse(recipe.endConditions.anchors.isEmpty());
    }

    @Test
    public void rejectsCandidateWithOnlyCoordinates() throws Exception {
        List<ToolTraceEvent> traces = Arrays.asList(
            new ToolTraceEvent("1", 100L, "", "ui.tap",
                new JSONObject().put("x", 500).put("y", 600),
                true, "Tapped", "pkg", "Activity")
        );

        Assert.assertNull(AutomationCandidateGenerator.fromSuccessfulTurn("点那里", "", traces));
    }

    @Test
    public void rejectsHighRiskInputTextCandidate() throws Exception {
        List<ToolTraceEvent> traces = Arrays.asList(
            new ToolTraceEvent("1", 100L, "", "ui.input_text",
                new JSONObject().put("text", "secret"),
                true, "Input text", "pkg", "Activity")
        );

        Assert.assertNull(AutomationCandidateGenerator.fromSuccessfulTurn("输入密码", "", traces));
    }
}
```

- [ ] **Step 2: Run candidate tests and verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.AutomationCandidateGeneratorTest
```

Expected: compile failure for missing `AutomationCandidateGenerator`.

- [ ] **Step 3: Implement generator**

Create `app/src/main/java/com/termux/app/automation/AutomationCandidateGenerator.java`.

Required behavior:

- Ignore unsuccessful trace events.
- Keep only `app.open`, `ui.click_text`, `ui.swipe`, and `ui.tap` when the sequence has at least one stable non-coordinate anchor.
- Reject any turn containing `ui.input_text` in first version.
- Convert `app.open` to an `ActionStep` with tool `app.open` and selector list empty.
- Convert `ui.click_text` to an `ActionStep` with one `UiSelector` using the clicked text.
- Convert `ui.tap` only if another stable selector exists in the same recipe; coordinate-only recipe returns `null`.
- Set `riskLevel` from `AutomationPolicy.classifyRecipe`.
- Set `enabled=false` and `autoBoostEnabled=false` for candidates.
- Set `source="agent_success"`.
- Set end condition anchors from the last stable clicked text or the last event result summary.

Use this public method:

```java
public static ActionRecipe fromSuccessfulTurn(String prompt, String sourceTaskId,
                                              List<ToolTraceEvent> traces)
```

Use `UUID.randomUUID().toString()` for IDs and `"v1"` for the first version.

- [ ] **Step 4: Run candidate tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.AutomationCandidateGeneratorTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit candidate generation**

Run:

```powershell
git add app/src/main/java/com/termux/app/automation/AutomationCandidateGenerator.java app/src/test/java/com/termux/app/automation/AutomationCandidateGeneratorTest.java
git commit -m "feat: generate automation candidates"
```

---

### Task 6: Automation Runtime and Candidate Dedupe Storage

**Files:**
- Create: `app/src/main/java/com/termux/app/automation/AutomationRuntime.java`
- Test: `app/src/test/java/com/termux/app/automation/AutomationRuntimeTest.java`

- [ ] **Step 1: Write runtime tests for candidate merge**

Create `app/src/test/java/com/termux/app/automation/AutomationRuntimeTest.java`:

```java
package com.termux.app.automation;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
public class AutomationRuntimeTest {

    @Test
    public void completedTurnCreatesCandidateAndMergesDuplicate() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        deleteDir(new File(context.getFilesDir(), "automation"));
        ToolTraceStore traceStore = new ToolTraceStore(context);
        AutomationStore automationStore = new AutomationStore(context);
        AutomationRuntime runtime = new AutomationRuntime(context, automationStore, traceStore, null);

        long start = 1000L;
        traceStore.append(new ToolTraceEvent("1", start + 1, "", "app.open",
            new JSONObject().put("package_name", "com.android.settings"),
            true, "Launched app", "com.android.settings", "Settings"));
        traceStore.append(new ToolTraceEvent("2", start + 2, "", "ui.click_text",
            new JSONObject().put("text", "无障碍"),
            true, "Clicked", "com.android.settings", "Settings"));

        runtime.generateCandidateForCompletedTurn("打开无障碍设置", "task-a", start, start + 10);
        runtime.generateCandidateForCompletedTurn("进入辅助功能权限页", "task-b", start, start + 10);

        Assert.assertEquals(1, automationStore.loadCandidates().size());
        Assert.assertEquals(2, automationStore.loadCandidates().get(0).versions.length());
    }

    private static void deleteDir(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteDir(child);
        }
        file.delete();
    }
}
```

- [ ] **Step 2: Run runtime test and verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.AutomationRuntimeTest
```

Expected: compile failure for missing `AutomationRuntime`.

- [ ] **Step 3: Implement `AutomationRuntime` candidate path**

Create `AutomationRuntime` with constructor:

```java
public AutomationRuntime(Context context, AutomationStore store, ToolTraceStore traceStore,
                         BoostExecutor executor)
```

Add methods:

```java
public long markTurnStarted()
public void generateCandidateForCompletedTurn(String prompt, String sourceTaskId, long startMs, long endMs)
public boolean tryStartBoost(String prompt, BoostExecutor.Callback callback)
```

For this task, implement `tryStartBoost` as `return false;` and fully implement candidate generation:

- Load traces from `traceStore.loadBetween(startMs, endMs)`.
- Call `AutomationCandidateGenerator.fromSuccessfulTurn`.
- If candidate is `null`, return.
- Load existing candidates.
- If `RecipeDedupe.shouldMerge(existing, candidate)`, replace existing with `RecipeDedupe.mergeAsVersion(existing, candidate)`.
- Otherwise append candidate.
- Save candidates.

- [ ] **Step 4: Run runtime test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.AutomationRuntimeTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit runtime candidate path**

Run:

```powershell
git add app/src/main/java/com/termux/app/automation/AutomationRuntime.java app/src/test/java/com/termux/app/automation/AutomationRuntimeTest.java
git commit -m "feat: merge automation candidates"
```

---

### Task 7: Boost Executor Core

**Files:**
- Create: `app/src/main/java/com/termux/app/automation/AndroidActionRunner.java`
- Create: `app/src/main/java/com/termux/app/automation/BoostExecutor.java`
- Create: `app/src/main/java/com/termux/app/automation/BoostResult.java`
- Test: `app/src/test/java/com/termux/app/automation/BoostExecutorTest.java`

- [ ] **Step 1: Write executor tests**

Create `app/src/test/java/com/termux/app/automation/BoostExecutorTest.java`:

```java
package com.termux.app.automation;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BoostExecutorTest {

    @Test
    public void executesStepsAndReportsSuccess() throws Exception {
        FakeRunner runner = new FakeRunner();
        BoostExecutor executor = new BoostExecutor(runner, null);
        List<String> statuses = new ArrayList<>();

        BoostResult result = executor.execute(recipe("r1"), new BoostExecutor.Callback() {
            @Override public void onStep(String recipeName, int index, int total, String toolName) {
                statuses.add(index + "/" + total + ":" + toolName);
            }
            @Override public void onCompleted(String recipeName) {}
            @Override public void onFailed(String recipeName, String reason) {}
        });

        Assert.assertTrue(result.success);
        Assert.assertEquals(2, runner.calls.size());
        Assert.assertEquals("1/2:app.open", statuses.get(0));
        Assert.assertEquals("2/2:ui.click_text", statuses.get(1));
    }

    @Test
    public void stopsAtFirstFailure() throws Exception {
        FakeRunner runner = new FakeRunner();
        runner.failOnTool = "ui.click_text";
        BoostExecutor executor = new BoostExecutor(runner, null);

        BoostResult result = executor.execute(recipe("r1"), BoostExecutor.Callback.NOOP);

        Assert.assertFalse(result.success);
        Assert.assertTrue(result.reason.contains("ui.click_text"));
        Assert.assertEquals(2, runner.calls.size());
    }

    private static ActionRecipe recipe(String id) throws Exception {
        ScreenFingerprint end = new ScreenFingerprint("com.android.settings", "Settings", Arrays.asList("无障碍"), 1, 0, "");
        ActionStep open = new ActionStep("1", "app.open",
            new JSONObject().put("package_name", "com.android.settings"),
            Arrays.asList(), ScreenFingerprint.empty(), end, 3000, "fallback_agent");
        ActionStep click = new ActionStep("2", "ui.click_text",
            new JSONObject().put("text", "无障碍"),
            Arrays.asList(new UiSelector("无障碍", "", "TextView", new int[]{0, 0, 1, 1}, "", "", 90)),
            end, end, 3000, "fallback_agent");
        return new ActionRecipe(id, "打开无障碍", true, true, AutomationRiskLevel.LOW,
            Arrays.asList("打开无障碍"), "com.android.settings", "Settings",
            ScreenFingerprint.empty(), end, Arrays.asList(open, click),
            "agent_success", Arrays.asList(), new RecipeStats(0, 0, 0, 0, 0, ""),
            "v1", null);
    }

    private static final class FakeRunner implements AndroidActionRunner {
        final List<String> calls = new ArrayList<>();
        String failOnTool = "";

        @Override public void runStep(ActionStep step) throws Exception {
            calls.add(step.toolName);
            if (step.toolName.equals(failOnTool)) throw new Exception("failed " + step.toolName);
        }

        @Override public ScreenFingerprint currentFingerprint() {
            return new ScreenFingerprint("com.android.settings", "Settings", Arrays.asList("无障碍"), 1, 0, "");
        }

        @Override public boolean matches(ScreenFingerprint expected) {
            return true;
        }
    }
}
```

- [ ] **Step 2: Run executor tests and verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.BoostExecutorTest
```

Expected: compile failure for missing executor classes.

- [ ] **Step 3: Implement runner interface and result**

Create `AndroidActionRunner.java`:

```java
package com.termux.app.automation;

public interface AndroidActionRunner {
    void runStep(ActionStep step) throws Exception;
    ScreenFingerprint currentFingerprint();
    boolean matches(ScreenFingerprint expected);
}
```

Create `BoostResult.java`:

```java
package com.termux.app.automation;

public final class BoostResult {
    public final boolean success;
    public final String recipeId;
    public final String reason;

    private BoostResult(boolean success, String recipeId, String reason) {
        this.success = success;
        this.recipeId = recipeId != null ? recipeId : "";
        this.reason = reason != null ? reason : "";
    }

    public static BoostResult success(String recipeId) {
        return new BoostResult(true, recipeId, "");
    }

    public static BoostResult failure(String recipeId, String reason) {
        return new BoostResult(false, recipeId, reason);
    }
}
```

- [ ] **Step 4: Implement `BoostExecutor`**

Create `BoostExecutor.java`:

```java
package com.termux.app.automation;

public final class BoostExecutor {
    public interface Callback {
        Callback NOOP = new Callback() {
            @Override public void onStep(String recipeName, int index, int total, String toolName) {}
            @Override public void onCompleted(String recipeName) {}
            @Override public void onFailed(String recipeName, String reason) {}
        };
        void onStep(String recipeName, int index, int total, String toolName);
        void onCompleted(String recipeName);
        void onFailed(String recipeName, String reason);
    }

    private final AndroidActionRunner runner;
    private final AutomationStore store;

    public BoostExecutor(AndroidActionRunner runner, AutomationStore store) {
        this.runner = runner;
        this.store = store;
    }

    public BoostResult execute(ActionRecipe recipe, Callback callback) {
        Callback cb = callback != null ? callback : Callback.NOOP;
        if (recipe == null) return BoostResult.failure("", "Missing recipe");
        try {
            int total = recipe.steps.size();
            for (int i = 0; i < total; i++) {
                ActionStep step = recipe.steps.get(i);
                cb.onStep(recipe.name, i + 1, total, step.toolName);
                runner.runStep(step);
                if (step.postconditions != null && !step.postconditions.anchors.isEmpty()
                        && !runner.matches(step.postconditions)) {
                    throw new Exception("postcondition failed for " + step.toolName);
                }
            }
            cb.onCompleted(recipe.name);
            return BoostResult.success(recipe.id);
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.toString();
            cb.onFailed(recipe.name, reason);
            if (store != null) store.appendFailure(recipe.id, "", reason, runner.currentFingerprint());
            return BoostResult.failure(recipe.id, reason);
        }
    }
}
```

- [ ] **Step 5: Run executor tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.BoostExecutorTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit executor core**

Run:

```powershell
git add app/src/main/java/com/termux/app/automation app/src/test/java/com/termux/app/automation/BoostExecutorTest.java
git commit -m "feat: add automation boost executor"
```

---

### Task 8: Android MCP Action Runner

**Files:**
- Create: `app/src/main/java/com/termux/app/automation/AndroidMcpActionRunner.java`
- Modify: `app/src/main/java/com/termux/app/mcp/McpAccessibilityService.java`
- Modify: `app/src/main/java/com/termux/app/mcp/tools/UiTreeTool.java`

- [ ] **Step 1: Add accessibility fingerprint helpers**

Modify `McpAccessibilityService` with:

```java
public com.termux.app.automation.ScreenFingerprint currentScreenFingerprint() {
    AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root == null) {
        return new com.termux.app.automation.ScreenFingerprint(
            getCurrentPackage(), getCurrentActivity(),
            java.util.Collections.emptyList(), 0, 0, "");
    }
    try {
        java.util.ArrayList<String> anchors = new java.util.ArrayList<>();
        int[] counts = new int[]{0, 0};
        collectFingerprint(root, anchors, counts, 0, 4);
        return new com.termux.app.automation.ScreenFingerprint(
            getCurrentPackage(), getCurrentActivity(), anchors, counts[0], counts[1],
            shortClass(root.getClassName()));
    } finally {
        root.recycle();
    }
}

public boolean screenMatches(com.termux.app.automation.ScreenFingerprint expected) {
    if (expected == null) return true;
    com.termux.app.automation.ScreenFingerprint current = currentScreenFingerprint();
    if (!expected.packageName.isEmpty() && !expected.packageName.equals(current.packageName)) return false;
    for (String anchor : expected.anchors) {
        if (!current.containsAnchor(anchor)) return false;
    }
    return true;
}

private void collectFingerprint(AccessibilityNodeInfo node, java.util.List<String> anchors,
                                int[] counts, int depth, int maxDepth) {
    if (node == null || depth > maxDepth) return;
    if (node.isClickable()) counts[0]++;
    if (node.isEditable()) counts[1]++;
    CharSequence text = node.getText();
    CharSequence desc = node.getContentDescription();
    if (text != null && text.length() > 0 && anchors.size() < 24) anchors.add(text.toString());
    if (desc != null && desc.length() > 0 && anchors.size() < 24) anchors.add(desc.toString());
    for (int i = 0; i < node.getChildCount(); i++) {
        AccessibilityNodeInfo child = node.getChild(i);
        collectFingerprint(child, anchors, counts, depth + 1, maxDepth);
        if (child != null) child.recycle();
    }
}

private static String shortClass(CharSequence cls) {
    if (cls == null) return "";
    String s = cls.toString();
    int dot = s.lastIndexOf('.');
    return dot >= 0 ? s.substring(dot + 1) : s;
}
```

If `UiTreeTool` already has a private `shortClass`, keep both methods private in their files to avoid changing public contracts.

- [ ] **Step 2: Add `AndroidMcpActionRunner`**

Create `app/src/main/java/com/termux/app/automation/AndroidMcpActionRunner.java`:

```java
package com.termux.app.automation;

import android.content.Context;

import com.termux.app.mcp.McpAccessibilityService;
import com.termux.app.mcp.tools.AppTool;
import com.termux.app.mcp.tools.UiTool;

public final class AndroidMcpActionRunner implements AndroidActionRunner {
    private final Context context;

    public AndroidMcpActionRunner(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void runStep(ActionStep step) throws Exception {
        if ("app.open".equals(step.toolName)) {
            new AppTool(AppTool.Kind.OPEN).call(step.arguments, context);
            return;
        }
        if ("ui.click_text".equals(step.toolName)) {
            new UiTool(UiTool.Kind.CLICK_TEXT).call(step.arguments, context);
            return;
        }
        if ("ui.tap".equals(step.toolName)) {
            new UiTool(UiTool.Kind.TAP).call(step.arguments, context);
            return;
        }
        if ("ui.swipe".equals(step.toolName)) {
            new UiTool(UiTool.Kind.SWIPE).call(step.arguments, context);
            return;
        }
        throw new Exception("Unsupported Boost tool: " + step.toolName);
    }

    @Override
    public ScreenFingerprint currentFingerprint() {
        if (!McpAccessibilityService.isRunning()) return ScreenFingerprint.empty();
        return McpAccessibilityService.getInstance().currentScreenFingerprint();
    }

    @Override
    public boolean matches(ScreenFingerprint expected) {
        if (expected == null || expected.anchors.isEmpty()) return true;
        if (!McpAccessibilityService.isRunning()) return false;
        return McpAccessibilityService.getInstance().screenMatches(expected);
    }
}
```

- [ ] **Step 3: Run automation unit tests and compile**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.BoostExecutorTest --tests com.termux.app.automation.AutomationModelJsonTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit Android action runner**

Run:

```powershell
git add app/src/main/java/com/termux/app/automation/AndroidMcpActionRunner.java app/src/main/java/com/termux/app/mcp/McpAccessibilityService.java
git commit -m "feat: run automation steps via android mcp"
```

---

### Task 9: Settings Page and Automation Management UI

**Files:**
- Create: `app/src/main/java/com/termux/app/AutomationSettingsFragment.java`
- Create: `app/src/main/java/com/termux/app/AutomationRecipeAdapter.java`
- Create: `app/src/main/res/layout/fragment_automation_settings.xml`
- Create: `app/src/main/res/layout/item_automation_recipe.xml`
- Create: `app/src/main/res/layout/item_automation_failure.xml`
- Modify: `app/src/main/java/com/termux/app/activities/SettingsActivity.java`
- Modify: `app/src/main/res/xml/root_preferences.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add strings**

Add these strings to `app/src/main/res/values/strings.xml` near the settings strings:

```xml
<string name="automation_boost_preferences_title">Automation Boost</string>
<string name="automation_boost_preferences_summary">Manage fast paths, app whitelist, and learned action recipes</string>
<string name="automation_boost_global_enabled">Enable Automation Boost</string>
<string name="automation_boost_candidates">Candidate recipes</string>
<string name="automation_boost_recipes">Enabled recipes</string>
<string name="automation_boost_failures">Recent failures</string>
<string name="automation_boost_empty_candidates">No candidate recipes yet</string>
<string name="automation_boost_empty_recipes">No enabled recipes yet</string>
<string name="automation_boost_empty_failures">No Boost failures recorded</string>
```

- [ ] **Step 2: Add root settings entry**

Add this preference to `app/src/main/res/xml/root_preferences.xml` above the existing Termux preference:

```xml
<Preference
    app:key="automation_boost"
    app:title="@string/automation_boost_preferences_title"
    app:summary="@string/automation_boost_preferences_summary"
    app:persistent="false"/>
```

- [ ] **Step 3: Wire root preference click**

Modify `SettingsActivity.RootPreferencesFragment.onCreatePreferences` after `setPreferencesFromResource(...)`:

```java
Preference automationPreference = findPreference("automation_boost");
if (automationPreference != null) {
    automationPreference.setOnPreferenceClickListener(preference -> {
        requireActivity().getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.settings, new com.termux.app.AutomationSettingsFragment())
            .addToBackStack("automation_boost")
            .commit();
        return true;
    });
}
```

- [ ] **Step 4: Add settings layout**

Create `app/src/main/res/layout/fragment_automation_settings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#FFFFFF">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/automation_boost_enabled"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/automation_boost_global_enabled"
            android:textSize="16sp" />

        <TextView
            android:id="@+id/automation_whitelist_summary"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:textColor="#666666"
            android:textSize="13sp" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="@string/automation_boost_candidates"
            android:textColor="#212121"
            android:textSize="15sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/automation_candidates_empty"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingTop="8dp"
            android:text="@string/automation_boost_empty_candidates"
            android:textColor="#888888"
            android:textSize="13sp" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/automation_candidates_recycler"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:nestedScrollingEnabled="false" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="@string/automation_boost_recipes"
            android:textColor="#212121"
            android:textSize="15sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/automation_recipes_empty"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingTop="8dp"
            android:text="@string/automation_boost_empty_recipes"
            android:textColor="#888888"
            android:textSize="13sp" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/automation_recipes_recycler"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:nestedScrollingEnabled="false" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="@string/automation_boost_failures"
            android:textColor="#212121"
            android:textSize="15sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/automation_failures_empty"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingTop="8dp"
            android:text="@string/automation_boost_empty_failures"
            android:textColor="#888888"
            android:textSize="13sp" />

        <LinearLayout
            android:id="@+id/automation_failures_container"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical" />

    </LinearLayout>
</ScrollView>
```

- [ ] **Step 5: Add recipe item layout**

Create `app/src/main/res/layout/item_automation_recipe.xml` with a compact vertical row:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingTop="10dp"
    android:paddingBottom="10dp">

    <TextView
        android:id="@+id/automation_recipe_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="#212121"
        android:textSize="14sp"
        android:textStyle="bold"
        android:maxLines="1"
        android:ellipsize="end" />

    <TextView
        android:id="@+id/automation_recipe_summary"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:textColor="#666666"
        android:textSize="12sp"
        android:maxLines="3"
        android:ellipsize="end" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="6dp"
        android:orientation="horizontal">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/automation_recipe_primary"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"
            android:layout_width="wrap_content"
            android:layout_height="32dp"
            android:textSize="12sp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/automation_recipe_secondary"
            style="@style/Widget.MaterialComponents.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="32dp"
            android:layout_marginStart="8dp"
            android:textSize="12sp" />
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 6: Implement adapter and fragment behavior**

Implement `AutomationRecipeAdapter` with:

```java
public interface Listener {
    void onPrimary(ActionRecipe recipe);
    void onSecondary(ActionRecipe recipe);
}
```

Adapter behavior:

- Candidate mode: primary button text `启用`, secondary `删除`.
- Recipe mode: primary button text `白名单/取消白名单` based on `AutomationSettingsStore.isRecipeWhitelisted(id)`, secondary `禁用`.
- Summary text: package, risk level, step count, success/failure count.

Implement `AutomationSettingsFragment`:

- On create view, inflate `fragment_automation_settings`.
- Load `AutomationStore` and `AutomationSettingsStore`.
- Global switch updates `settingsStore.setBoostEnabled`.
- Candidate primary moves recipe from candidates to recipes with `enabled=true`.
- Candidate secondary removes candidate.
- Recipe primary toggles recipe whitelist and app whitelist for `targetPackage`.
- Recipe secondary sets `enabled=false`, removes recipe whitelist, and saves.
- Failures render as simple `TextView` children in `automation_failures_container`.

- [ ] **Step 7: Run resource compile through unit test task**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.AutomationStoreTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit settings UI**

Run:

```powershell
git add app/src/main/java/com/termux/app/AutomationSettingsFragment.java app/src/main/java/com/termux/app/AutomationRecipeAdapter.java app/src/main/java/com/termux/app/activities/SettingsActivity.java app/src/main/res/layout/fragment_automation_settings.xml app/src/main/res/layout/item_automation_recipe.xml app/src/main/res/xml/root_preferences.xml app/src/main/res/values/strings.xml
git commit -m "feat: add automation boost settings"
```

---

### Task 10: Home Top Bar Settings Entry

**Files:**
- Modify: `app/src/main/res/layout/fragment_home.xml`
- Modify: `app/src/main/java/com/termux/app/HomeFragment.java`

- [ ] **Step 1: Update top bar XML**

In `fragment_home.xml`, replace the current top status `LinearLayout` content with this structure:

```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="12dp"
    android:background="#F5F5F5">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_history"
            style="@style/Widget.MaterialComponents.Button.TextButton"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:text="≡"
            android:textSize="20sp"
            android:textColor="#555555"
            android:insetTop="0dp"
            android:insetBottom="0dp"
            android:insetLeft="0dp"
            android:insetRight="0dp"
            android:paddingStart="0dp"
            android:paddingEnd="0dp" />

        <TextView
            android:id="@+id/home_session_title"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:clickable="true"
            android:focusable="true"
            android:foreground="?attr/selectableItemBackground"
            android:text="Claude Code"
            android:textColor="#212121"
            android:textSize="16sp"
            android:textStyle="bold"
            android:paddingTop="6dp"
            android:paddingBottom="6dp"
            android:ellipsize="end"
            android:maxLines="1" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_home_settings"
            style="@style/Widget.MaterialComponents.Button.TextButton"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:text="⚙"
            android:textSize="18sp"
            android:textColor="#555555"
            android:insetTop="0dp"
            android:insetBottom="0dp"
            android:insetLeft="0dp"
            android:insetRight="0dp"
            android:paddingStart="0dp"
            android:paddingEnd="0dp"
            android:contentDescription="@string/action_open_settings" />
    </LinearLayout>

    <TextView
        android:id="@+id/home_status_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="● 等待中"
        android:textColor="#888888"
        android:textSize="12sp"
        android:paddingStart="10dp"
        android:paddingEnd="10dp"
        android:paddingTop="4dp"
        android:paddingBottom="4dp"
        android:background="#E0E0E0" />
</FrameLayout>
```

- [ ] **Step 2: Wire settings button**

In `HomeFragment.onViewCreated`, after `mSessionTitle.setOnClickListener(...)`, add:

```java
View homeSettings = view.findViewById(R.id.btn_home_settings);
if (homeSettings != null) {
    homeSettings.setOnClickListener(v ->
        startActivity(new Intent(requireContext(), com.termux.app.activities.SettingsActivity.class)));
}
```

- [ ] **Step 3: Run compile check**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.ChatAdapterOrderingTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit home settings entry**

Run:

```powershell
git add app/src/main/res/layout/fragment_home.xml app/src/main/java/com/termux/app/HomeFragment.java
git commit -m "feat: add home automation settings entry"
```

---

### Task 11: Floating Boost Status

**Files:**
- Modify: `app/src/main/java/com/termux/app/FloatingStatusService.java`
- Modify: `app/src/main/res/layout/layout_floating_status.xml`

- [ ] **Step 1: Make floating title dynamic**

In `FloatingStatusService`, add field:

```java
private TextView mTitleText;
```

In `onCreate`, assign:

```java
mTitleText = mFloatingView.findViewById(R.id.float_title);
```

Add static state:

```java
private static volatile String sPendingTitle = "Claude Code";
private static volatile boolean sForceVisible = false;
```

Add public method:

```java
public static void updateBoostStatus(String status, int color, String preview, boolean active) {
    sPendingTitle = "Automation Boost";
    sForceVisible = active;
    updateStatus(status, color, preview, active);
    if (!active) {
        sPendingTitle = "Claude Code";
        sForceVisible = false;
    }
}
```

Update `applyPending()`:

```java
if (mTitleText != null) mTitleText.setText(sPendingTitle);
```

Update `updateVisibility()`:

```java
boolean shouldShow = (sForceVisible || inBackground) && sIsBusy && !sUserClosed;
```

- [ ] **Step 2: Run compile check**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.ChatMessageTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit floating Boost status**

Run:

```powershell
git add app/src/main/java/com/termux/app/FloatingStatusService.java app/src/main/res/layout/layout_floating_status.xml
git commit -m "feat: show automation boost status"
```

---

### Task 12: Home Boost Attempt and Fallback

**Files:**
- Modify: `app/src/main/java/com/termux/app/HomeFragment.java`
- Modify: `app/src/main/java/com/termux/app/automation/AutomationRuntime.java`

- [ ] **Step 1: Complete `AutomationRuntime.tryStartBoost`**

Implement:

```java
public boolean tryStartBoost(String prompt, BoostExecutor.Callback callback) {
    AutomationSettingsStore settings = new AutomationSettingsStore(context);
    if (!settings.isBoostEnabled()) return false;
    List<ActionRecipe> recipes = store.loadRecipes();
    for (ActionRecipe recipe : recipes) {
        if (!AutomationPolicy.canAutoBoost(recipe)) continue;
        if (!settings.isRecipeWhitelisted(recipe.id)) continue;
        if (!settings.isAppWhitelisted(recipe.targetPackage)) continue;
        if (!matchesPrompt(prompt, recipe.intentPatterns)) continue;
        new Thread(() -> executor.execute(recipe, callback), "automation-boost").start();
        return true;
    }
    return false;
}
```

Add private matcher:

```java
private static boolean matchesPrompt(String prompt, List<String> patterns) {
    String p = normalize(prompt);
    for (String pattern : patterns) {
        String value = normalize(pattern);
        if (!value.isEmpty() && (p.contains(value) || value.contains(p))) return true;
    }
    return false;
}

private static String normalize(String value) {
    return value == null ? "" : value.replace(" ", "").trim().toLowerCase();
}
```

- [ ] **Step 2: Add Home fields**

In `HomeFragment`, add:

```java
private com.termux.app.automation.AutomationRuntime mAutomationRuntime;
private long mAutomationTurnStartMs = 0L;
private boolean mBoosting = false;
```

Initialize in `onViewCreated` after provider setup:

```java
com.termux.app.automation.AutomationStore automationStore =
    new com.termux.app.automation.AutomationStore(requireContext());
com.termux.app.automation.ToolTraceStore traceStore =
    new com.termux.app.automation.ToolTraceStore(requireContext());
com.termux.app.automation.BoostExecutor boostExecutor =
    new com.termux.app.automation.BoostExecutor(
        new com.termux.app.automation.AndroidMcpActionRunner(requireContext()), automationStore);
mAutomationRuntime = new com.termux.app.automation.AutomationRuntime(
    requireContext(), automationStore, traceStore, boostExecutor);
```

- [ ] **Step 3: Refactor provider send path**

Extract the existing API-key/provider execution portion of `sendOrConfirm()` into:

```java
private void sendToProvider(String text, String displayText) {
    // Move the existing API key lookup, message insertion, status update,
    // appendChatLog, and mClaudeSession/mCodexSession send logic here.
}
```

Keep all existing behavior unchanged inside `sendToProvider`.

- [ ] **Step 4: Attempt Boost before provider send**

In `sendOrConfirm()`, after display text is computed and before API key lookup, add:

```java
if (mAutomationRuntime != null && tryBoost(text, displayText)) {
    mInputEdit.setText("");
    return;
}
sendToProvider(text, displayText);
```

Add helper:

```java
private boolean tryBoost(String text, String displayText) {
    return mAutomationRuntime.tryStartBoost(text, new com.termux.app.automation.BoostExecutor.Callback() {
        @Override public void onStep(String recipeName, int index, int total, String toolName) {
            mBoosting = true;
            mHandler.post(() -> {
                updateStatus("⚡ Boosting " + index + "/" + total, 0xFF7B1FA2);
                FloatingStatusService.updateBoostStatus(
                    "⚡ Boosting " + index + "/" + total, 0xFF7B1FA2, recipeName, true);
            });
        }

        @Override public void onCompleted(String recipeName) {
            mHandler.post(() -> {
                mBoosting = false;
                mAdapter.addMessage(ChatMessage.user(displayText));
                mAdapter.addMessage(ChatMessage.assistant("已通过 Automation Boost 完成：" + recipeName));
                updateStatus("● 就绪", 0xFF2E7D32);
                FloatingStatusService.updateBoostStatus("● Boost completed", 0xFF2E7D32, recipeName, false);
                scrollToCurrentTurnOutputStartOnce();
            });
        }

        @Override public void onFailed(String recipeName, String reason) {
            mHandler.post(() -> {
                mBoosting = false;
                FloatingStatusService.updateBoostStatus(
                    "● Boost failed, falling back to Agent", 0xFFF57C00, reason, false);
                sendToProvider(text, displayText);
            });
        }
    });
}
```

- [ ] **Step 5: Generate candidates after successful provider turns**

In both Claude and Codex result handlers:

- Set `mAutomationTurnStartMs = mAutomationRuntime.markTurnStarted()` immediately before provider send.
- On non-error result, call:

```java
if (mAutomationRuntime != null && mAutomationTurnStartMs > 0) {
    mAutomationRuntime.generateCandidateForCompletedTurn(
        mLastSentText, mCurrentSessionId != null ? mCurrentSessionId : "", mAutomationTurnStartMs, System.currentTimeMillis());
}
```

For Codex, pass an empty source task ID if no session ID exists.

- [ ] **Step 6: Run Home-related unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.ChatAdapterOrderingTest --tests com.termux.app.automation.AutomationRuntimeTest --tests com.termux.app.automation.BoostExecutorTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit Home Boost integration**

Run:

```powershell
git add app/src/main/java/com/termux/app/HomeFragment.java app/src/main/java/com/termux/app/automation/AutomationRuntime.java
git commit -m "feat: attempt automation boost before agent"
```

---

### Task 13: Failure Downgrade and Recipe Stats

**Files:**
- Modify: `app/src/main/java/com/termux/app/automation/BoostExecutor.java`
- Modify: `app/src/main/java/com/termux/app/automation/AutomationStore.java`
- Test: `app/src/test/java/com/termux/app/automation/BoostExecutorTest.java`

- [ ] **Step 1: Add failing test for repeated failures**

Extend `BoostExecutorTest`:

```java
@Test
public void repeatedFailureDisablesAutoBoost() throws Exception {
    FakeRunner runner = new FakeRunner();
    runner.failOnTool = "ui.click_text";
    InMemoryStore store = new InMemoryStore();
    ActionRecipe recipe = recipe("r1");
    store.saveRecipes(java.util.Arrays.asList(recipe));
    BoostExecutor executor = new BoostExecutor(runner, store);

    executor.execute(recipe, BoostExecutor.Callback.NOOP);
    executor.execute(store.loadRecipes().get(0), BoostExecutor.Callback.NOOP);

    ActionRecipe updated = store.loadRecipes().get(0);
    Assert.assertFalse(updated.autoBoostEnabled);
    Assert.assertEquals(2, updated.stats.failureCount);
}
```

Add `InMemoryStore` test subclass or helper that exposes the same methods used by `BoostExecutor`.

- [ ] **Step 2: Implement stats update**

In `AutomationStore`, add:

```java
public synchronized void updateRecipeStats(String recipeId, boolean success, String failureReason)
```

Behavior:

- Load recipes.
- Find matching recipe.
- Increment success or failure count.
- Update last timestamps.
- Recompute average duration only when caller later passes duration; for this task keep old value.
- If failure count reaches `2`, write a new `ActionRecipe` copy with `autoBoostEnabled=false`.
- Save recipes.

In `BoostExecutor.execute`, call `store.updateRecipeStats(recipe.id, true, "")` on success and `store.updateRecipeStats(recipe.id, false, reason)` on failure.

- [ ] **Step 3: Run executor tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.automation.BoostExecutorTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit failure downgrade**

Run:

```powershell
git add app/src/main/java/com/termux/app/automation/BoostExecutor.java app/src/main/java/com/termux/app/automation/AutomationStore.java app/src/test/java/com/termux/app/automation/BoostExecutorTest.java
git commit -m "feat: downgrade failing automation recipes"
```

---

### Task 14: Final Verification and Manual Device Checklist

**Files:**
- Modify only if the verification finds a defect in files touched by earlier tasks.

- [ ] **Step 1: Run all unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Build debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and an APK under `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Manual device test for candidate generation**

On a test device:

1. Enable Accessibility and Screen Capture as usual.
2. Ask the app to open Android Settings and enter a low-risk page such as Accessibility.
3. Let Claude or Codex complete the task normally.
4. Open Home top-right settings -> Automation Boost.
5. Verify a candidate appears with target package `com.android.settings`, low risk, and steps containing `app.open` and `ui.click_text`.

Expected: candidate appears and is not automatically whitelisted.

- [ ] **Step 4: Manual device test for whitelist Boost**

On the same device:

1. In Automation Boost settings, enable global Boost.
2. Enable the candidate as a recipe.
3. Toggle recipe whitelist for that recipe.
4. Return Home and ask the same operation again.

Expected:

- Home status changes to `Boosting`.
- Floating overlay title shows `Automation Boost`.
- Steps execute without sending the prompt to Claude/Codex.
- Final assistant message says Boost completed.

- [ ] **Step 5: Manual device test for failure fallback**

Create a failure by disabling the recipe target package whitelist or changing the recipe target text in storage through the settings UI when an edit option exists. If no edit option exists, use a naturally changed page state where the target text is absent.

Expected:

- Boost stops on the failing step.
- Floating overlay shows fallback.
- `mBoosting` state clears.
- The prompt is sent to Claude/Codex.
- Failure appears in Automation Boost settings.

- [ ] **Step 6: Commit verification fixes only if needed**

If verification finds a defect, commit only the specific file or files changed by that fix. For example, if the fix is in the Boost executor:

```powershell
git add app/src/main/java/com/termux/app/automation/BoostExecutor.java app/src/test/java/com/termux/app/automation/BoostExecutorTest.java
git commit -m "fix: stabilize automation boost verification"
```

If no defect was fixed, do not create an empty commit.
