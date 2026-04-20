package com.termux.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.util.List;

/** RecyclerView Adapter，渲染用户消息（右侧蓝色气泡）和 Claude 回复（左侧灰色气泡）。 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;
    private static final int TYPE_SYSTEM = 2;

    private final List<ChatMessage> mMessages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.mMessages = messages;
    }

    // -------------------------------------------------------------------------
    // Adapter 标准方法
    // -------------------------------------------------------------------------

    @Override
    public int getItemViewType(int position) {
        switch (mMessages.get(position).type) {
            case USER:      return TYPE_USER;
            case SYSTEM:    return TYPE_SYSTEM;
            default:        return TYPE_ASSISTANT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserViewHolder(inflater.inflate(R.layout.item_msg_user, parent, false));
        } else if (viewType == TYPE_SYSTEM) {
            return new SystemViewHolder(inflater.inflate(R.layout.item_msg_system, parent, false));
        } else {
            return new AssistantViewHolder(inflater.inflate(R.layout.item_msg_assistant, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = mMessages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(msg.content);
        } else if (holder instanceof SystemViewHolder) {
            ((SystemViewHolder) holder).bind(msg.content);
        } else {
            ((AssistantViewHolder) holder).bind(msg.content);
        }
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    // -------------------------------------------------------------------------
    // 辅助方法（HomeFragment 调用）
    // -------------------------------------------------------------------------

    /** 在列表末尾添加新消息并通知刷新。 */
    public void addMessage(ChatMessage msg) {
        mMessages.add(msg);
        notifyItemInserted(mMessages.size() - 1);
    }

    /**
     * 更新最后一条 ASSISTANT 消息的内容（用于实时追加 Claude 回复）。
     * 如果不存在 ASSISTANT 消息则自动创建。
     */
    public void updateLastAssistant(String content) {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            if (mMessages.get(i).type == ChatMessage.Type.ASSISTANT) {
                if (!mMessages.get(i).content.equals(content)) {
                    mMessages.get(i).content = content;
                    notifyItemChanged(i);
                }
                return;
            }
        }
        // 没有 ASSISTANT 消息时自动创建
        if (!content.isEmpty()) {
            addMessage(ChatMessage.assistant(content));
        }
    }

    /** 获取最后一条 ASSISTANT 消息，不存在返回 null。 */
    @Nullable
    public ChatMessage getLastAssistantMessage() {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            if (mMessages.get(i).type == ChatMessage.Type.ASSISTANT) {
                return mMessages.get(i);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView mText;

        UserViewHolder(View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.msg_text);
        }

        void bind(String content) {
            mText.setText(content);
        }
    }

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        private final TextView mText;

        AssistantViewHolder(View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.msg_text);
        }

        void bind(String content) {
            mText.setText(content);
        }
    }

    static class SystemViewHolder extends RecyclerView.ViewHolder {
        private final TextView mText;

        SystemViewHolder(View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.msg_text);
        }

        void bind(String content) {
            mText.setText(content);
        }
    }
}
