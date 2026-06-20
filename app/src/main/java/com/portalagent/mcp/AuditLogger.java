package com.portalagent.mcp;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Appends one line per MCP tool call to ~/mcp-audit.log inside Termux HOME. */
public class AuditLogger {

    private static final String TAG = "McpAudit";

    private final File mLogFile;
    private final SimpleDateFormat mFmt;

    public AuditLogger(String termuxHome) {
        mLogFile = new File(termuxHome, "mcp-audit.log");
        mFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        mFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /** Log a tool invocation result. Safe to call from any thread. */
    public synchronized void log(String toolName, String taskId, boolean success, String detail) {
        String ts = mFmt.format(new Date());
        String tid = (taskId != null && !taskId.isEmpty()) ? taskId : "-";
        String line = "[" + ts + "] tool=" + toolName
                + " task_id=" + tid
                + " result=" + (success ? "ok" : "error")
                + (detail != null && !detail.isEmpty() ? " detail=" + detail.replace('\n', ' ') : "")
                + "\n";
        try {
            mLogFile.getParentFile().mkdirs();
            try (FileWriter fw = new FileWriter(mLogFile, true)) {
                fw.write(line);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to write audit log: " + e.getMessage());
        }
    }
}
