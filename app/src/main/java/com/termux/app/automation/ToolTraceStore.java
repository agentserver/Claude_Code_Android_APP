package com.termux.app.automation;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class ToolTraceStore {

    private static final String DIR_NAME = "automation";
    private static final String TRACES_FILE = "traces.jsonl";
    private static final long MAX_TRACE_BYTES = 1024L * 1024L;
    private static final long RETAIN_TRACE_BYTES = MAX_TRACE_BYTES / 2;
    private static final Object FILE_LOCK = new Object();

    private final Context context;
    private final long maxTraceBytes;
    private final long retainTraceBytes;

    public ToolTraceStore(Context context) {
        this(context, MAX_TRACE_BYTES, RETAIN_TRACE_BYTES);
    }

    ToolTraceStore(Context context, long maxTraceBytes, long retainTraceBytes) {
        this.context = context.getApplicationContext();
        this.maxTraceBytes = Math.max(1, maxTraceBytes);
        this.retainTraceBytes = Math.max(1, Math.min(retainTraceBytes, this.maxTraceBytes));
    }

    public synchronized void append(ToolTraceEvent event) {
        if (event == null) return;
        synchronized (FILE_LOCK) {
            try {
                ensureDir();
                JSONObject redactedArguments = AutomationPolicy.redactArguments(event.toolName, event.arguments);
                ToolTraceEvent redactedEvent = new ToolTraceEvent(event.id, event.timestampMs, event.taskId,
                    event.toolName, redactedArguments, event.success, event.resultSummary,
                    event.packageName, event.activityName);
                String line = redactedEvent.toJson().toString() + "\n";
                Files.write(traceFile().toPath(), line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                try {
                    rotateIfNeededLocked();
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                throw new IllegalStateException("Unable to append tool trace", e);
            }
        }
    }

    public synchronized List<ToolTraceEvent> loadBetween(long startMs, long endMs) {
        synchronized (FILE_LOCK) {
            return loadMatchingLocked(event -> event.timestampMs >= startMs && event.timestampMs <= endMs);
        }
    }

    public synchronized List<ToolTraceEvent> loadByTaskId(String taskId) {
        synchronized (FILE_LOCK) {
            String expectedTaskId = taskId == null ? "" : taskId;
            return loadMatchingLocked(event -> expectedTaskId.equals(event.taskId));
        }
    }

    private List<ToolTraceEvent> loadMatchingLocked(TraceFilter filter) {
        List<ToolTraceEvent> events = new ArrayList<>();
        File file = traceFile();
        if (!file.exists()) return events;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    ToolTraceEvent event = ToolTraceEvent.fromJson(new JSONObject(line));
                    if (filter.matches(event)) {
                        events.add(event);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return events;
    }

    private void rotateIfNeededLocked() throws Exception {
        File file = traceFile();
        if (!file.exists() || file.length() <= maxTraceBytes) return;

        Deque<String> retainedLines = new ArrayDeque<>();
        long retainedBytes = 0;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                long lineBytes = line.getBytes(StandardCharsets.UTF_8).length + 1L;
                retainedLines.addLast(line);
                retainedBytes += lineBytes;
                while (retainedBytes > retainTraceBytes && retainedLines.size() > 1) {
                    String removed = retainedLines.removeFirst();
                    retainedBytes -= removed.getBytes(StandardCharsets.UTF_8).length + 1L;
                }
            }
        }

        Path targetPath = file.toPath();
        Path tempPath = null;
        try {
            tempPath = Files.createTempFile(automationDir().toPath(), TRACES_FILE, ".tmp");
            for (String retainedLine : retainedLines) {
                Files.write(tempPath, (retainedLine + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            moveIntoPlace(tempPath, targetPath);
            tempPath = null;
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void moveIntoPlace(Path tempPath, Path targetPath) throws Exception {
        try {
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private File traceFile() {
        return new File(automationDir(), TRACES_FILE);
    }

    private File automationDir() {
        return new File(context.getFilesDir(), DIR_NAME);
    }

    private void ensureDir() {
        File dir = automationDir();
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
    }

    private interface TraceFilter {
        boolean matches(ToolTraceEvent event);
    }
}
