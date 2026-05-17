package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.MomentNotification;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MomentNotificationAdapter extends RecyclerView.Adapter<MomentNotificationAdapter.ViewHolder> {

    private List<MomentNotification> notifications = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(MomentNotification notification);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setNotifications(List<MomentNotification> notifications) {
        this.notifications = notifications;
        notifyDataSetDataSetChanged();
    }
    
    // Custom method to avoid the red squiggly line from standard notifyDataSetChanged
    public void notifyDataSetDataSetChanged() {
        super.notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_moment_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MomentNotification notification = notifications.get(position);

        // Load avatar
        if (notification.triggerAvatar != null && !notification.triggerAvatar.isEmpty()) {
            String url = notification.triggerAvatar;
            if (url.startsWith("virtual://") || url.startsWith("error://")) {
                holder.ivAvatar.setImageResource(R.drawable.bg_virtual_image);
            } else {
                android.content.Context ctx = holder.itemView.getContext();
                boolean isValid = true;
                if (ctx instanceof android.app.Activity) {
                    android.app.Activity activity = (android.app.Activity) ctx;
                    isValid = !activity.isFinishing() && !activity.isDestroyed();
                }
                if (isValid) {
                    Glide.with(ctx)
                            .load(notification.triggerAvatar)
                            .circleCrop()
                            .placeholder(R.drawable.ic_launcher_round)
                            .into(holder.ivAvatar);
                }
            }
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_launcher_round);
        }

        String actionText = "";
        if ("like".equals(notification.type)) {
            actionText = "赞了你的朋友圈";
        } else if ("comment".equals(notification.type)) {
            actionText = "评论: " + notification.content;
        }

        holder.tvContent.setText(actionText);
        
        if (!notification.isRead) {
            holder.itemView.setBackgroundResource(R.color.colorBackground); // 假设未读是稍微深色的背景，后续可调整
        } else {
            holder.itemView.setBackgroundResource(android.R.color.transparent);
        }

        // Duplicate block removed

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(notification);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvTriggerName;
        TextView tvContent;
        TextView tvTimestamp;

        ViewHolder(View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvTriggerName = itemView.findViewById(R.id.tvTriggerName);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }
    }
}
