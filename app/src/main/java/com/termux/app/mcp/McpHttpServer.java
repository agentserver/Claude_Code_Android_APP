package com.termux.app.mcp;

import android.content.Context;
import android.util.Log;

import com.termux.app.automation.ToolTraceEvent;
import com.termux.app.automation.ToolTraceStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MCP Streamable HTTP server on 127.0.0.1:8765.
 *
 * Implements the Model Context Protocol (2024-11-05) over plain HTTP/1.1.
 * Claude Code connects via: claude mcp add --transport http android-mcp http://127.0.0.1:8765/mcp
 *
 * Handled JSON-RPC 2.0 methods:
 *   initialize              → server info + capabilities
 *   notifications/initialized → notification (no response body, HTTP 204)
 *   tools/list              → registered tool schemas
 *   tools/call              → dispatch to McpTool implementation
 */
public class McpHttpServer {

    private static final String TAG = "McpHttpServer";
    static final int PORT = 8765;

    private final Context mContext;
    private final AuditLogger mAudit;
    private final ToolTraceStore mTraceStore;
    private final List<McpTool> mTools = new ArrayList<>();

    private volatile boolean mRunning = false;
    private volatile ServerSocket mServerSocket;

    public McpHttpServer(Context context, AuditLogger audit) {
        mContext = context.getApplicationContext();
        mAudit   = audit;
        mTraceStore = new ToolTraceStore(mContext);
    }

    public void registerTool(McpTool tool) {
        mTools.add(tool);
    }

    public void start() {
        if (mRunning) return;
        mRunning = true;
        Thread t = new Thread(this::runServer, "mcp-http-server");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        mRunning = false;
        ServerSocket ss = mServerSocket;
        if (ss != null) {
            try { ss.close(); } catch (IOException ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Server loop
    // ─────────────────────────────────────────────────────────────────────────

    private void runServer() {
        while (mRunning) {
            try (ServerSocket server = new ServerSocket(PORT, 10,
                    InetAddress.getByName("127.0.0.1"))) {
                server.setReuseAddress(true);
                mServerSocket = server;
                Log.i(TAG, "MCP server listening on 127.0.0.1:" + PORT);
                while (mRunning) {
                    Socket client = server.accept();
                    Thread ct = new Thread(() -> handleClient(client), "mcp-req");
                    ct.setDaemon(true);
                    ct.start();
                }
            } catch (IOException e) {
                if (!mRunning) break;
                try { Thread.sleep(5000); } catch (InterruptedException ignored) { break; }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP request handling
    // ─────────────────────────────────────────────────────────────────────────

    private void handleClient(Socket sock) {
        try (Socket s = sock) {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = s.getOutputStream();

            // Read request line
            String requestLine = reader.readLine();
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ", 3);
            if (parts.length < 2) return;
            String method = parts[0];
            String path   = parts[1].split("\\?")[0]; // strip query string

            // Read headers
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    try { contentLength = Integer.parseInt(line.substring(15).trim()); }
                    catch (NumberFormatException ignored) {}
                }
            }

            if (!"POST".equals(method) || !"/mcp".equals(path)) {
                sendHttp(out, 404, "application/json", "{\"error\":\"Not found\"}");
                return;
            }

            // Read body
            if (contentLength <= 0) {
                sendHttp(out, 400, "application/json", "{\"error\":\"Missing body\"}");
                return;
            }
            char[] body = new char[contentLength];
            int read = 0;
            while (read < contentLength) {
                int n = reader.read(body, read, contentLength - read);
                if (n < 0) break;
                read += n;
            }
            String bodyStr = new String(body, 0, read);

            handleJsonRpc(bodyStr, out);

        } catch (Exception e) {
            Log.w(TAG, "Request error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON-RPC 2.0 dispatch
    // ─────────────────────────────────────────────────────────────────────────

    private void handleJsonRpc(String bodyStr, OutputStream out) throws Exception {
        JSONObject req;
        try {
            req = new JSONObject(bodyStr);
        } catch (Exception e) {
            sendHttp(out, 400, "application/json",
                "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}");
            return;
        }

        String rpcMethod = req.optString("method", "");
        // id may be int, string, or absent (notification). Keep as raw Object.
        Object id = req.has("id") ? req.get("id") : null;

        // Notifications have no id — send HTTP 204, no body
        if (id == null) {
            sendHttp(out, 204, null, null);
            return;
        }

        try {
            String responseBody;
            switch (rpcMethod) {
                case "initialize":
                    responseBody = handleInitialize(id);
                    break;
                case "tools/list":
                    responseBody = handleToolsList(id);
                    break;
                case "tools/call":
                    responseBody = handleToolsCall(id, req.optJSONObject("params"));
                    break;
                default:
                    responseBody = errorResponse(id, -32601, "Method not found: " + rpcMethod);
            }
            sendHttp(out, 200, "application/json", responseBody);
        } catch (Exception e) {
            Log.e(TAG, "JSON-RPC dispatch error", e);
            sendHttp(out, 200, "application/json",
                errorResponse(id, -32603, "Internal error: " + e.getMessage()));
        }
    }

    private String handleInitialize(Object id) throws Exception {
        JSONObject serverInfo = new JSONObject();
        serverInfo.put("name", "android-mcp");
        serverInfo.put("version", "0.1.0");

        JSONObject capabilities = new JSONObject();
        capabilities.put("tools", new JSONObject());

        JSONObject result = new JSONObject();
        result.put("protocolVersion", "2024-11-05");
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);

        return successResponse(id, result);
    }

    private String handleToolsList(Object id) throws Exception {
        JSONArray toolArray = new JSONArray();
        for (McpTool tool : mTools) {
            JSONObject t = new JSONObject();
            t.put("name", tool.getName());
            t.put("description", tool.getDescription());
            t.put("inputSchema", new JSONObject(tool.getInputSchema()));
            toolArray.put(t);
        }
        JSONObject result = new JSONObject();
        result.put("tools", toolArray);
        return successResponse(id, result);
    }

    private String handleToolsCall(Object id, JSONObject params) throws Exception {
        if (params == null) {
            return errorResponse(id, -32602, "Missing params");
        }
        String toolName = params.optString("name", "");
        JSONObject args = params.optJSONObject("arguments");
        if (args == null) args = new JSONObject();

        McpTool tool = findTool(toolName);
        if (tool == null) {
            return errorResponse(id, -32602, "Unknown tool: " + toolName);
        }

        String taskId = args.optString("task_id", "");
        boolean success = true;
        String contentStr;
        try {
            contentStr = tool.call(args, mContext);
        } catch (Exception e) {
            success = false;
            contentStr = errorContent("Tool error: " + e.getMessage());
        }
        mAudit.log(toolName, taskId, success, success ? null : contentStr);
        appendToolTrace(toolName, args, taskId, success, contentStr);

        JSONObject result = new JSONObject();
        result.put("content", new JSONArray(contentStr));
        result.put("isError", !success);
        return successResponse(id, result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private McpTool findTool(String name) {
        for (McpTool t : mTools) {
            if (t.getName().equals(name)) return t;
        }
        return null;
    }

    private void appendToolTrace(String toolName, JSONObject args, String taskId, boolean success, String contentStr) {
        try {
            String packageName = "";
            String activityName = "";
            if (McpAccessibilityService.isRunning()) {
                McpAccessibilityService service = McpAccessibilityService.getInstance();
                if (service != null) {
                    packageName = service.getCurrentPackage();
                    activityName = service.getCurrentActivity();
                }
            }
            ToolTraceEvent event = new ToolTraceEvent(UUID.randomUUID().toString(), System.currentTimeMillis(),
                taskId, toolName, args, success, summarizeTraceResult(contentStr), packageName, activityName);
            mTraceStore.append(event);
        } catch (Exception e) {
            Log.w(TAG, "Failed to append tool trace", e);
        }
    }

    private static String summarizeTraceResult(String contentStr) {
        if (contentStr == null) return "";
        String summary = contentStr.replace('\n', ' ').replace('\r', ' ').trim();
        if (summary.length() > 240) {
            return summary.substring(0, 237) + "...";
        }
        return summary;
    }

    private static String successResponse(Object id, JSONObject result) throws Exception {
        JSONObject r = new JSONObject();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        r.put("result", result);
        return r.toString();
    }

    private static String errorResponse(Object id, int code, String message) {
        try {
            JSONObject err = new JSONObject();
            err.put("code", code);
            err.put("message", message);
            JSONObject r = new JSONObject();
            r.put("jsonrpc", "2.0");
            r.put("id", id != null ? id : JSONObject.NULL);
            r.put("error", err);
            return r.toString();
        } catch (Exception e) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal\"}}";
        }
    }

    private static String errorContent(String message) {
        try {
            JSONObject item = new JSONObject();
            item.put("type", "text");
            item.put("text", message);
            return new JSONArray().put(item).toString();
        } catch (Exception e) {
            return "[{\"type\":\"text\",\"text\":\"error\"}]";
        }
    }

    private static void sendHttp(OutputStream out, int code, String contentType, String body)
            throws IOException {
        String status;
        switch (code) {
            case 200: status = "200 OK"; break;
            case 204: status = "204 No Content"; break;
            case 400: status = "400 Bad Request"; break;
            case 404: status = "404 Not Found"; break;
            default:  status = code + " Error"; break;
        }
        StringBuilder header = new StringBuilder("HTTP/1.1 ").append(status).append("\r\n");
        if (body != null && contentType != null) {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            header.append("Content-Type: ").append(contentType).append("; charset=utf-8\r\n");
            header.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            header.append("Connection: close\r\n\r\n");
            out.write(header.toString().getBytes(StandardCharsets.UTF_8));
            out.write(bodyBytes);
        } else {
            header.append("Content-Length: 0\r\nConnection: close\r\n\r\n");
            out.write(header.toString().getBytes(StandardCharsets.UTF_8));
        }
        out.flush();
    }
}
