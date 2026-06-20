package com.portalagent.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.util.List;

/** 抽屉里记忆库 / 技能列表的通用 Adapter。 */
public class DrawerFileAdapter extends RecyclerView.Adapter<DrawerFileAdapter.VH> {

    public interface Listener {
        void onTap(FileItem item);
        void onLongPress(FileItem item);
    }

    public static class FileItem {
        public final String name;    // 显示名（不含扩展名）
        public final String preview; // 列表中显示的简短预览
        public final String fullPath;// 文件绝对路径（供读写）

        public FileItem(String name, String preview, String fullPath) {
            this.name     = name;
            this.preview  = preview;
            this.fullPath = fullPath;
        }
    }

    private final List<FileItem> mItems;
    private final Listener       mListener;

    public DrawerFileAdapter(List<FileItem> items, Listener listener) {
        mItems    = items;
        mListener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_drawer_file, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        FileItem item = mItems.get(pos);
        h.name.setText(item.name);
        if (item.preview == null || item.preview.isEmpty()) {
            h.preview.setVisibility(View.GONE);
        } else {
            h.preview.setVisibility(View.VISIBLE);
            h.preview.setText(item.preview);
        }
        h.itemView.setOnClickListener(v -> mListener.onTap(item));
        h.itemView.setOnLongClickListener(v -> { mListener.onLongPress(item); return true; });
    }

    @Override public int getItemCount() { return mItems.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView preview;
        VH(View v) {
            super(v);
            name    = v.findViewById(R.id.file_item_name);
            preview = v.findViewById(R.id.file_item_preview);
        }
    }
}
