package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;

import java.util.List;

public class DesktopAppAdapter extends RecyclerView.Adapter<DesktopAppAdapter.ViewHolder> {

    private List<AppItem> appList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(AppItem item);
    }

    public static class AppItem {
        public String name;
        public int iconResId;
        public Class<?> targetActivity;

        public AppItem(String name, int iconResId, Class<?> targetActivity) {
            this.name = name;
            this.iconResId = iconResId;
            this.targetActivity = targetActivity;
        }
    }

    public DesktopAppAdapter(List<AppItem> appList, OnItemClickListener listener) {
        this.appList = appList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_desktop_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppItem item = appList.get(position);
        holder.tvAppName.setText(item.name);
        if (item.iconResId != 0) {
            holder.ivAppIcon.setImageResource(item.iconResId);
        } else {
            // Default to ic_launcher if iconResId is 0 or invalid, simplistic handle for now
            holder.ivAppIcon.setImageResource(R.drawable.ic_launcher);
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
        }
    }
}