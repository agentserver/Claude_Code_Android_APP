package com.portalagent.automation;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ToolTraceStoreTest {

    @Test
    public void loadBetweenReturnsWindowAndArguments() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        deleteDir(new File(context.getFilesDir(), "automation"));
        ToolTraceStore store = new ToolTraceStore(context);

        store.append(trace("before", 99, "task-1", "ui.tap", new JSONObject().put("x", 1)));
        store.append(trace("inside-start", 100, "task-1", "ui.tap", new JSONObject().put("x", 2)));
        store.append(trace("inside-end", 200, "task-1", "ui.tap", new JSONObject().put("x", 3)));
        store.append(trace("after", 201, "task-1", "ui.tap", new JSONObject().put("x", 4)));

        List<ToolTraceEvent> events = store.loadBetween(100, 200);

        Assert.assertEquals(2, events.size());
        Assert.assertEquals("inside-start", events.get(0).id);
        Assert.assertEquals(2, events.get(0).argumentsCopy().optInt("x"));
        Assert.assertEquals("inside-end", events.get(1).id);
        Assert.assertEquals(3, events.get(1).argumentsCopy().optInt("x"));
    }

    @Test
    public void inputTextIsRedactedBeforePersisting() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        deleteDir(new File(context.getFilesDir(), "automation"));
        ToolTraceStore store = new ToolTraceStore(context);

        store.append(trace("trace-1", 100, "task-1", "ui.input_text",
            new JSONObject().put("text", "secret").put("mode", "replace")));

        List<ToolTraceEvent> events = store.loadBetween(0, 200);

        Assert.assertEquals(1, events.size());
        Assert.assertEquals("[redacted]", events.get(0).argumentsCopy().optString("text"));
        Assert.assertEquals("replace", events.get(0).argumentsCopy().optString("mode"));
    }

    @Test
    public void loadByTaskIdFiltersMatchingTask() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        deleteDir(new File(context.getFilesDir(), "automation"));
        ToolTraceStore store = new ToolTraceStore(context);

        store.append(trace("trace-1", 100, "task-1", "ui.tap", new JSONObject()));
        store.append(trace("trace-2", 101, "task-2", "ui.tap", new JSONObject()));
        store.append(trace("trace-3", 102, "task-1", "ui.tap", new JSONObject()));

        List<ToolTraceEvent> events = store.loadByTaskId("task-1");

        Assert.assertEquals(2, events.size());
        Assert.assertEquals("trace-1", events.get(0).id);
        Assert.assertEquals("trace-3", events.get(1).id);
    }

    @Test
    public void corruptJsonLinesAreSkipped() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        deleteDir(new File(context.getFilesDir(), "automation"));
        File dir = new File(context.getFilesDir(), "automation");
        Assert.assertTrue(dir.mkdirs() || dir.isDirectory());
        Files.write(new File(dir, "traces.jsonl").toPath(),
            ("not-json\n" + trace("valid", 100, "task-1", "ui.tap", new JSONObject()).toJson().toString() + "\n{\n")
                .getBytes(StandardCharsets.UTF_8));
        ToolTraceStore store = new ToolTraceStore(context);

        List<ToolTraceEvent> events = store.loadBetween(0, 200);

        Assert.assertEquals(1, events.size());
        Assert.assertEquals("valid", events.get(0).id);
    }

    @Test
    public void multipleTraceLinesSkipCorruptLines() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        deleteDir(new File(context.getFilesDir(), "automation"));
        File dir = new File(context.getFilesDir(), "automation");
        Assert.assertTrue(dir.mkdirs() || dir.isDirectory());
        StringBuilder jsonl = new StringBuilder();
        jsonl.append(trace("trace-1", 100, "task-1", "ui.tap", new JSONObject()).toJson()).append('\n');
        jsonl.append("{").append('\n');
        jsonl.append(trace("trace-2", 101, "task-1", "ui.tap", new JSONObject()).toJson()).append('\n');
        jsonl.append("not-json").append('\n');
        jsonl.append(trace("trace-3", 102, "task-2", "ui.tap", new JSONObject()).toJson()).append('\n');
        Files.write(new File(dir, "traces.jsonl").toPath(), jsonl.toString().getBytes(StandardCharsets.UTF_8));
        ToolTraceStore store = new ToolTraceStore(context);

        List<ToolTraceEvent> events = store.loadBetween(100, 102);

        Assert.assertEquals(3, events.size());
        Assert.assertEquals("trace-1", events.get(0).id);
        Assert.assertEquals("trace-2", events.get(1).id);
        Assert.assertEquals("trace-3", events.get(2).id);
    }

    @Test
    public void rotationKeepsRecentCompleteTraceLines() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        deleteDir(new File(context.getFilesDir(), "automation"));
        ToolTraceStore store = new ToolTraceStore(context, 650, 360);

        for (int i = 0; i < 10; i++) {
            store.append(trace("trace-" + i, 100 + i, "task-1", "ui.tap",
                new JSONObject().put("payload", "value-" + i)));
        }

        List<ToolTraceEvent> events = store.loadBetween(0, 200);

        Assert.assertFalse(events.isEmpty());
        Assert.assertTrue(events.size() < 10);
        Assert.assertEquals("trace-9", events.get(events.size() - 1).id);
        Assert.assertTrue(events.get(0).timestampMs > 100);
    }

    private static ToolTraceEvent trace(String id, long timestampMs, String taskId, String toolName,
                                        JSONObject arguments) {
        return new ToolTraceEvent(id, timestampMs, taskId, toolName, arguments,
            true, "ok", "pkg", "Activity");
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
