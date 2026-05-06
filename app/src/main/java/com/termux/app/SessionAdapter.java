package com.termux.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.util.List;

/** 历史会话列表 RecyclerView Adapter。 */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.VH> {

    public interface Listener {
        void onSessionSelected(SessionStore.Entry entry);
        void onSessionLongPress(SessionStore.Entry entry);
    }

    private final List<SessionStore.Entry> mEntries;
    private final Listener                 mListener;
    private String                         mActiveId;  // 当前正在使用的 session ID

    public SessionAdapter(List<SessionStore.Entry> entries, String activeId, Listener listener) {
        this.mEntries  = entries;
        this.mActiveId = activeId;
        this.mListener = listener;
    }

    public void setActiveId(String id) {
        mActiveId = id;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        SessionStore.Entry e = mEntries.get(position);
        holder.preview.setText(e.preview.isEmpty() ? "（空对话）" : e.preview);
        holder.time.setText(e.formatTime());
        // 当前激活的 session 加高亮背景色
        boolean active = e.id.equals(mActiveId);
        holder.itemView.setBackgroundColor(active ? 0xFFE3F2FD : 0x00000000);
        holder.itemView.setOnClickListener(v -> mListener.onSessionSelected(e));
        holder.itemView.setOnLongClickListener(v -> { mListener.onSessionLongPress(e); return true; });
    }

    @Override
    public int getItemCount() { return mEntries.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView preview;
        final TextView time;

        VH(View v) {
            super(v);
            preview = v.findViewById(R.id.session_preview);
            time    = v.findViewById(R.id.session_time);
        }
    }
}
