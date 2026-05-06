package com.termux.app;

/** 聊天消息数据类，用于简化 UI 的聊天视图。 */
public class ChatMessage {

    public enum Type { USER, ASSISTANT, SYSTEM }

    public final Type type;
    public String content;           // 回复正文，可流式更新
    public String thinking;          // 思考过程原文（null = 无思考内容）
    public boolean thinkingCollapsed; // true = 折叠显示（回复完成后）

    public ChatMessage(Type type, String content) {
        this.type = type;
        this.content = content;
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Type.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Type.ASSISTANT, content);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Type.SYSTEM, content);
    }
}
