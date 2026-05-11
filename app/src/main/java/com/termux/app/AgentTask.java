package com.termux.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AgentTask {

    public enum Status { RUNNING, COMPLETED }

    public String                id;
    public String                prompt;
    public Status                status;
    public long                  timestamp;
    public List<ChatMessage>     messages;

    public AgentTask() {
        this.id        = UUID.randomUUID().toString();
        this.status    = Status.RUNNING;
        this.timestamp = System.currentTimeMillis();
        this.messages  = new ArrayList<>();
    }

    public AgentTask(String id, String prompt, Status status, long timestamp,
                     List<ChatMessage> messages) {
        this.id        = id;
        this.prompt    = prompt;
        this.status    = status;
        this.timestamp = timestamp;
        this.messages  = messages != null ? messages : new ArrayList<>();
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id",     id);
        o.put("prompt", prompt != null ? prompt : "");
        o.put("status", status != null ? status.name() : Status.RUNNING.name());
        o.put("ts",     timestamp);
        JSONArray arr = new JSONArray();
        for (ChatMessage m : messages) {
            JSONObject mo = new JSONObject();
            mo.put("type",     m.type.name());
            mo.put("content",  m.content != null ? m.content : "");
            if (m.thinking != null) mo.put("thinking", m.thinking);
            mo.put("tc",       m.thinkingCollapsed);
            arr.put(mo);
        }
        o.put("messages", arr);
        return o;
    }

    public static AgentTask fromJson(JSONObject o) {
        try {
            String id     = o.optString("id");
            String prompt = o.optString("prompt");
            Status status = Status.valueOf(o.optString("status", "COMPLETED"));
            long ts       = o.optLong("ts", System.currentTimeMillis());
            List<ChatMessage> msgs = new ArrayList<>();
            JSONArray arr = o.optJSONArray("messages");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject mo = arr.getJSONObject(i);
                    ChatMessage.Type t =
                        ChatMessage.Type.valueOf(mo.optString("type", "SYSTEM"));
                    ChatMessage m = new ChatMessage(t, mo.optString("content", ""));
                    if (mo.has("thinking")) m.thinking = mo.optString("thinking");
                    m.thinkingCollapsed = mo.optBoolean("tc", false);
                    msgs.add(m);
                }
            }
            return new AgentTask(id, prompt, status, ts, msgs);
        } catch (Exception e) {
            return null;
        }
    }

    /** 列表预览：prompt 第一行 + 截断到 80 字符 */
    public String previewLine() {
        if (prompt == null) return "";
        int nl = prompt.indexOf('\n');
        String first = nl >= 0 ? prompt.substring(0, nl) : prompt;
        first = first.trim();
        if (first.length() > 80) first = first.substring(0, 77) + "…";
        return first;
    }
}
