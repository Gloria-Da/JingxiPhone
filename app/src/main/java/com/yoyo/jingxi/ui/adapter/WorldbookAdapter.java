package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.WorldbookEntry;

import java.util.ArrayList;
import java.util.List;

public class WorldbookAdapter extends RecyclerView.Adapter<WorldbookAdapter.ViewHolder> {

    private List<WorldbookEntry> entries = new ArrayList<>();
    private OnItemClickListener listener;
    private OnSwitchChangeListener switchListener;

    public interface OnItemClickListener {
        void onItemClick(WorldbookEntry entry);
        void onItemLongClick(WorldbookEntry entry);
    }

    public interface OnSwitchChangeListener {
        void onSwitchChange(WorldbookEntry entry, boolean isChecked);
    }

    public void setEntries(List<WorldbookEntry> entries) {
        this.entries = entries;
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnSwitchChangeListener(OnSwitchChangeListener listener) {
        this.switchListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_worldbook, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WorldbookEntry entry = entries.get(position);

        String typeStr = "[未知]";
        if (entry.type == 0) typeStr = "[前]";
        else if (entry.type == 1) typeStr = "[中]";
        else if (entry.type == 2) typeStr = "[后]";

        holder.tvType.setText(typeStr);
        holder.tvTitle.setText(entry.title != null ? entry.title : "未命名");

        if (entry.type == 1 && entry.keyword != null && !entry.keyword.isEmpty()) {
            holder.tvKeyword.setVisibility(View.VISIBLE);
            holder.tvKeyword.setText("关键词: " + entry.keyword);
        } else {
            holder.tvKeyword.setVisibility(View.GONE);
        }

        holder.tvContent.setText(entry.content);

        // 避免触发 listener
        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(entry.isEnabled);
        holder.switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (switchListener != null) {
                switchListener.onSwitchChange(entry, isChecked);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(entry);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onItemLongClick(entry);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return entries != null ? entries.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvType;
        TextView tvTitle;
        TextView tvKeyword;
        TextView tvContent;
        Switch switchEnabled;

        ViewHolder(View view) {
            super(view);
            tvType = view.findViewById(R.id.tvType);
            tvTitle = view.findViewById(R.id.tvTitle);
            tvKeyword = view.findViewById(R.id.tvKeyword);
            tvContent = view.findViewById(R.id.tvContent);
            switchEnabled = view.findViewById(R.id.switchEnabled);
        }
    }
}
