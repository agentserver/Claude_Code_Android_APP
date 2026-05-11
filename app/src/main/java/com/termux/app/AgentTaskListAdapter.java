package com.termux.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AgentTaskListAdapter
        extends RecyclerView.Adapter<AgentTaskListAdapter.VH> {

    public interface Listener {
        void onTap(AgentTask task);
        void onLongPress(AgentTask task);
    }

    private final List<AgentTask> mItems;
    private final Listener        mListener;

    public AgentTaskListAdapter(List<AgentTask> items, Listener listener) {
        this.mItems    = items;
        this.mListener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_agent_task, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AgentTask t = mItems.get(position);
        h.time.setText(formatTime(t.timestamp));
        String preview = t.previewLine();
        h.preview.setText(preview.isEmpty() ? "（空任务）" : preview);
        if (t.status == AgentTask.Status.RUNNING) {
            h.status.setText("● 运行中");
            h.status.setTextColor(0xFFFFFFFF);
            h.status.setBackgroundColor(0xFFF57C00);
        } else {
            h.status.setText("● 已完成");
            h.status.setTextColor(0xFFFFFFFF);
            h.status.setBackgroundColor(0xFF388E3C);
        }
        h.itemView.setOnClickListener(v -> mListener.onTap(t));
        h.itemView.setOnLongClickListener(v -> { mListener.onLongPress(t); return true; });
    }

    @Override
    public int getItemCount() { return mItems.size(); }

    private String formatTime(long ts) {
        long now = System.currentTimeMillis();
        long today = now - (now % 86_400_000L);
        if (ts >= today) {
            return "今天 " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
        }
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(ts));
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView time, status, preview;
        VH(View v) {
            super(v);
            time    = v.findViewById(R.id.agent_task_time);
            status  = v.findViewById(R.id.agent_task_status);
            preview = v.findViewById(R.id.agent_task_preview);
        }
    }
}
