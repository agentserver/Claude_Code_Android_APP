package com.portalagent.keys;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.termux.R;

import java.util.List;

/** RecyclerView Adapter，展示 API Key 列表。 */
public class ApiKeyAdapter extends RecyclerView.Adapter<ApiKeyAdapter.ViewHolder> {

    public interface Listener {
        void onSetActive(ApiKeyStore.Entry entry);
        void onDelete(ApiKeyStore.Entry entry);
    }

    private final List<ApiKeyStore.Entry> mEntries;
    private String                        mActiveId;
    private final Listener                mListener;

    public ApiKeyAdapter(List<ApiKeyStore.Entry> entries, String activeId, Listener listener) {
        this.mEntries  = entries;
        this.mActiveId = activeId;
        this.mListener = listener;
    }

    /** 更新激活 Key ID 并刷新列表。 */
    public void setActiveId(String activeId) {
        this.mActiveId = activeId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                               .inflate(R.layout.item_api_key, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ApiKeyStore.Entry entry = mEntries.get(position);
        boolean isActive = entry.id.equals(mActiveId);

        h.alias.setText(entry.alias.isEmpty() ? "（无别名）" : entry.alias);
        h.maskedKey.setText(mask(entry.value));
        h.activeBadge.setVisibility(isActive ? View.VISIBLE : View.GONE);
        h.btnSetActive.setVisibility(isActive ? View.GONE : View.VISIBLE);

        h.btnSetActive.setOnClickListener(v -> mListener.onSetActive(entry));
        h.btnDelete.setOnClickListener(v -> mListener.onDelete(entry));
    }

    @Override
    public int getItemCount() {
        return mEntries.size();
    }

    // -------------------------------------------------------------------------

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView       alias;
        TextView       maskedKey;
        TextView       activeBadge;
        MaterialButton btnSetActive;
        MaterialButton btnDelete;

        ViewHolder(View v) {
            super(v);
            alias       = v.findViewById(R.id.key_alias);
            maskedKey   = v.findViewById(R.id.key_masked);
            activeBadge = v.findViewById(R.id.key_active_badge);
            btnSetActive = v.findViewById(R.id.btn_set_active);
            btnDelete   = v.findViewById(R.id.btn_delete_key);
        }
    }

    // -------------------------------------------------------------------------

    /** 脱敏显示：保留前 10 位 + *** + 后 4 位。 */
    static String mask(String key) {
        if (key == null || key.length() <= 14) return "***";
        return key.substring(0, 10) + "***" + key.substring(key.length() - 4);
    }
}
