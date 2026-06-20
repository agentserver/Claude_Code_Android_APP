package com.portalagent.mcp;

import android.content.Context;
import org.json.JSONObject;

/** A single MCP tool exposed by the Android MCP Server. */
public interface McpTool {

    /** Dot-namespaced tool name, e.g. {@code "android.get_status"}. */
    String getName();

    String getDescription();

    /**
     * JSON Schema string for the {@code inputSchema} field in tools/list.
     * Minimum: {@code {"type":"object","properties":{}}}
     */
    String getInputSchema();

    /**
     * Execute the tool synchronously on a background thread.
     *
     * @return MCP content-array JSON string, e.g.
     *         {@code [{"type":"text","text":"..."}]}
     *         or {@code [{"type":"image","data":"base64...","mimeType":"image/jpeg"}]}
     * @throws Exception on hard failure; McpHttpServer wraps this into isError=true
     */
    String call(JSONObject args, Context context) throws Exception;
}
