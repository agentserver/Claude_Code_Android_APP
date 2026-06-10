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
public class AutomationRuntimeTest {

    @Test
    public void completedTurnReadsTracesAndCreatesCandidate() throws Exception {
        Context context = cleanContext();
        AutomationStore store = new AutomationStore(context);
        ToolTraceStore traceStore = new ToolTraceStore(context);
        AutomationRuntime runtime = new AutomationRuntime(context, store, traceStore, null);

        traceStore.append(trace("before", 99, "task-1", "ui.click_text",
            new JSONObject().put("text", "Ignored"), true, "clicked Ignored",
            "com.android.settings", ".IgnoredActivity"));
        traceStore.append(trace("open", 100, "task-1", "app.open",
            new JSONObject().put("package", "com.android.settings"), true, "opened settings",
            "com.android.settings", ".Settings"));
        traceStore.append(trace("click", 150, "task-1", "ui.click_text",
            new JSONObject().put("text", "Network").put("match_mode", "exact"), true, "clicked Network",
            "com.android.settings", ".NetworkDashboardActivity"));
        traceStore.append(trace("after", 201, "task-1", "ui.click_text",
            new JSONObject().put("text", "After"), true, "clicked After",
            "com.android.settings", ".AfterActivity"));

        runtime.generateCandidateForCompletedTurn("打开网络设置", "task-1", 100, 200);

        List<ActionRecipe> candidates = store.loadCandidates();
        Assert.assertEquals(1, candidates.size());
        ActionRecipe candidate = candidates.get(0);
        Assert.assertEquals("打开网络设置", candidate.name);
        Assert.assertEquals("com.android.settings", candidate.targetPackage);
        Assert.assertEquals(".NetworkDashboardActivity", candidate.targetActivity);
        Assert.assertEquals(2, candidate.steps.size());
        Assert.assertTrue(candidate.endConditions.containsAnchor("Network"));
        Assert.assertEquals(1, candidate.versions.length());
    }

    @Test
    public void similarCompletedTurnsMergeIntoOneCandidateVersion() throws Exception {
        Context context = cleanContext();
        AutomationStore store = new AutomationStore(context);
        ToolTraceStore traceStore = new ToolTraceStore(context);
        AutomationRuntime runtime = new AutomationRuntime(context, store, traceStore, null);

        traceStore.append(trace("open-1", 100, "task-1", "app.open",
            new JSONObject().put("package", "com.android.settings"), true, "opened settings",
            "com.android.settings", ".Settings"));
        traceStore.append(trace("click-1", 110, "task-1", "ui.click_text",
            new JSONObject().put("text", "Network"), true, "clicked Network",
            "com.android.settings", ".NetworkDashboardActivity"));
        traceStore.append(trace("open-2", 200, "task-2", "app.open",
            new JSONObject().put("package", "com.android.settings"), true, "opened settings",
            "com.android.settings", ".Settings"));
        traceStore.append(trace("click-2", 210, "task-2", "ui.click_text",
            new JSONObject().put("text", "Network"), true, "clicked Network",
            "com.android.settings", ".NetworkDashboardActivity"));

        runtime.generateCandidateForCompletedTurn("打开网络设置", "task-1", 100, 150);
        runtime.generateCandidateForCompletedTurn("打开网络设置", "task-2", 200, 250);

        List<ActionRecipe> candidates = store.loadCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(2, candidates.get(0).versions.length());
        Assert.assertEquals(2, candidates.get(0).sourceTaskIds.size());
    }

    @Test
    public void nullCandidateDoesNotSaveForInputTextOrPureTap() throws Exception {
        Context context = cleanContext();
        AutomationStore store = new AutomationStore(context);
        ToolTraceStore traceStore = new ToolTraceStore(context);
        AutomationRuntime runtime = new AutomationRuntime(context, store, traceStore, null);

        traceStore.append(trace("open", 100, "task-1", "app.open",
            new JSONObject().put("package", "com.example.app"), true, "opened",
            "com.example.app", ".MainActivity"));
        traceStore.append(trace("input", 110, "task-1", "ui.input_text",
            new JSONObject().put("text", "secret"), true, "typed",
            "com.example.app", ".MainActivity"));
        traceStore.append(trace("tap", 200, "task-2", "ui.tap",
            new JSONObject().put("x", 10).put("y", 20), true, "tapped",
            "com.example.app", ".MainActivity"));

        runtime.generateCandidateForCompletedTurn("输入内容", "task-1", 100, 150);
        runtime.generateCandidateForCompletedTurn("点一下", "task-2", 200, 250);

        Assert.assertTrue(store.loadCandidates().isEmpty());
    }

    @Test
    public void markTurnStartedReturnsCurrentPositiveTime() {
        Context context = cleanContext();
        AutomationRuntime runtime = new AutomationRuntime(
            context, new AutomationStore(context), new ToolTraceStore(context), null);

        long before = System.currentTimeMillis();
        long started = runtime.markTurnStarted();
        long after = System.currentTimeMillis();

        Assert.assertTrue(started > 0);
        Assert.assertTrue(started >= before);
        Assert.assertTrue(started <= after);
    }

    private static ToolTraceEvent trace(String id, long timestampMs, String taskId, String toolName,
                                        JSONObject arguments, boolean success, String resultSummary,
                                        String packageName, String activityName) {
        return new ToolTraceEvent(id, timestampMs, taskId, toolName, arguments, success,
            resultSummary, packageName, activityName);
    }

    private static Context cleanContext() {
        Context context = RuntimeEnvironment.getApplication();
        deleteDir(new File(context.getFilesDir(), "automation"));
        return context;
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
