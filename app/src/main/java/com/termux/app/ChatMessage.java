package com.termux.app;

/** 聊天消息数据类，用于简化 UI 的聊天视图。 */
public class ChatMessage {

    public enum Type { USER, ASSISTANT, SYSTEM }

    public final Type type;
    public String content; // ASSISTANT 消息内容可原地更新（流式追加）

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
