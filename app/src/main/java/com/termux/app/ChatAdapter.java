package com.termux.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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
            ((AssistantViewHolder) holder).bind(msg);
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
     * 更新最后一条 ASSISTANT 消息的正文和思考内容（流式调用）。
     * 如果不存在 ASSISTANT 消息则自动创建。
     */
    public void updateLastAssistant(String content, String thinking) {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            ChatMessage msg = mMessages.get(i);
            if (msg.type == ChatMessage.Type.ASSISTANT) {
                boolean changed = !msg.content.equals(content)
                        || !java.util.Objects.equals(msg.thinking, thinking);
                if (changed) {
                    msg.content = content;
                    msg.thinking = thinking;
                    notifyItemChanged(i);
                }
                return;
            }
        }
        if (!content.isEmpty() || thinking != null) {
            ChatMessage m = ChatMessage.assistant(content);
            m.thinking = thinking;
            addMessage(m);
        }
    }

    /** 兼容旧调用（无思考内容）。 */
    public void updateLastAssistant(String content) {
        updateLastAssistant(content, null);
    }

    /** 回复完成后，将最后一条 ASSISTANT 消息的思考内容折叠。 */
    public void collapseLastAssistantThinking() {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            ChatMessage msg = mMessages.get(i);
            if (msg.type == ChatMessage.Type.ASSISTANT && msg.thinking != null
                    && !msg.thinkingCollapsed) {
                msg.thinkingCollapsed = true;
                notifyItemChanged(i);
                return;
            }
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
            itemView.setOnLongClickListener(v -> { copyToClipboard(v, mText.getText().toString()); return true; });
        }

        void bind(String content) {
            mText.setText(content);
        }
    }

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        private final TextView mText;
        private final View     mThinkingContainer;
        private final TextView mThinkingHeader;
        private final TextView mThinkingText;

        AssistantViewHolder(View itemView) {
            super(itemView);
            mText              = itemView.findViewById(R.id.msg_text);
            mThinkingContainer = itemView.findViewById(R.id.thinking_container);
            mThinkingHeader    = itemView.findViewById(R.id.thinking_header);
            mThinkingText      = itemView.findViewById(R.id.thinking_text);
            itemView.setOnLongClickListener(v -> { copyToClipboard(v, mText.getText().toString()); return true; });
        }

        void bind(ChatMessage msg) {
            mText.setText(msg.content);
            String thinking = msg.thinking;
            if (thinking == null || thinking.isEmpty()) {
                mThinkingContainer.setVisibility(View.GONE);
                return;
            }
            mThinkingContainer.setVisibility(View.VISIBLE);
            mThinkingText.setText(thinking);
            if (msg.thinkingCollapsed) {
                mThinkingText.setVisibility(View.GONE);
                mThinkingHeader.setText("💭 思考过程 ▶");
            } else {
                mThinkingText.setVisibility(View.VISIBLE);
                mThinkingHeader.setText("💭 思考中… ▼");
            }
            mThinkingContainer.setOnClickListener(v -> {
                if (mThinkingText.getVisibility() == View.VISIBLE) {
                    mThinkingText.setVisibility(View.GONE);
                    mThinkingHeader.setText("💭 思考过程 ▶");
                } else {
                    mThinkingText.setVisibility(View.VISIBLE);
                    mThinkingHeader.setText(msg.thinkingCollapsed ? "💭 思考过程 ▼" : "💭 思考中… ▼");
                }
            });
        }
    }

    static class SystemViewHolder extends RecyclerView.ViewHolder {
        private final TextView mText;

        SystemViewHolder(View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.msg_text);
            itemView.setOnLongClickListener(v -> { copyToClipboard(v, mText.getText().toString()); return true; });
        }

        void bind(String content) {
            mText.setText(content);
        }
    }

    private static void copyToClipboard(View v, String text) {
        ClipboardManager cm = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("message", text));
            Toast.makeText(v.getContext(), "已复制", Toast.LENGTH_SHORT).show();
        }
    }
}
