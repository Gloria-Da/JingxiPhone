package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.dao.SessionWithLastMessageDao.SessionWithLastMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionListAdapter extends RecyclerView.Adapter<SessionListAdapter.ViewHolder> {

    private List<SessionWithLastMessage> sessionList = new ArrayList<>();
    private OnItemClickListener listener;
    private OnItemLongClickListener longClickListener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public interface OnItemClickListener {
        void onItemClick(SessionWithLastMessage session);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(SessionWithLastMessage session);
    }

    public void setSessions(List<SessionWithLastMessage> sessions) {
        this.sessionList = sessions;
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SessionWithLastMessage session = sessionList.get(position);
        holder.tvName.setText(session.friendName);
        
        String summaryText = session.lastMessageContent != null ? session.lastMessageContent : "暂无消息";
        // Use lastMessageContent directly, handled by view binding or adapter
        // 过滤 <#...#> 格式的表情/动作标签
        summaryText = summaryText.replaceAll("<#[0-9.]+?#>", "");
        // 过滤 (...) 格式的表情/动作标签，比如 (inhale), (chuckle)
        summaryText = summaryText.replaceAll("\\([^)]*\\)", "");
        // 过滤 （...）格式的中文圆括号标签
        summaryText = summaryText.replaceAll("（[^）]*）", "");
        summaryText = summaryText.trim();
        holder.tvSummary.setText(summaryText);

        // Load avatar in session list
        if (session.sessionId > 0) {
            new Thread(() -> {
                com.yoyo.jingxi.data.entity.ChatSession chatSession = AppDatabase.getDatabase(holder.itemView.getContext())
                        .chatSessionDao().getSessionById(session.sessionId);
                if (chatSession != null && chatSession.characterId > 0) {
                    com.yoyo.jingxi.data.entity.Character character = AppDatabase.getDatabase(holder.itemView.getContext())
                            .characterDao().getCharacterById(chatSession.characterId);
                    if (character != null && character.avatarPath != null && !character.avatarPath.isEmpty()) {
                        holder.itemView.post(() -> {
                            android.content.Context ctx = holder.itemView.getContext();
                            boolean isValid = true;
                            if (ctx instanceof android.app.Activity) {
                                android.app.Activity activity = (android.app.Activity) ctx;
                                isValid = !activity.isFinishing() && !activity.isDestroyed();
                            }
                            if (isValid) {
                                com.bumptech.glide.Glide.with(ctx)
                                        .load(character.avatarPath)
                                        .circleCrop()
                                        .placeholder(R.drawable.ic_launcher_round)
                                        .into(holder.ivAvatar);
                            }
                        });
                    } else {
                        holder.itemView.post(() -> {
                            holder.ivAvatar.setImageResource(R.drawable.ic_launcher_round);
                        });
                    }
                } else {
                    holder.itemView.post(() -> {
                        holder.ivAvatar.setImageResource(R.drawable.ic_launcher_round);
                    });
                }
            }).start();
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_launcher_round);
        }
        
        if (session.lastMessageTimestamp > 0) {
            // Reusing tvTime if available, or just ignore for now if not in layout
        }

        if (session.isPinned) {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            holder.itemView.getContext().getTheme().resolveAttribute(R.attr.colorPinnedBackground, typedValue, true);
            holder.itemView.setBackgroundColor(typedValue.data);
        } else {
            // Remove the hardcoded drawable resource that might not exist
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        if (holder.tvUnreadCount != null) {
            if (session.unreadCount > 0) {
                holder.tvUnreadCount.setVisibility(View.VISIBLE);
                holder.tvUnreadCount.setText(String.valueOf(session.unreadCount));
            } else {
                holder.tvUnreadCount.setVisibility(View.GONE);
            }
        }
        
        holder.llRoot.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(session);
            }
        });
        
        holder.llRoot.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(session);
                return true;
            }
            return false;
        });

        // Swipe delete feature
        holder.tvDelete.setOnClickListener(v -> {
            // Reusing long click listener logic for deletion
            new Thread(() -> {
                // Delete all messages in the session
                AppDatabase.getDatabase(v.getContext()).messageDao().deleteMessagesBySessionId(session.sessionId);
                // Delete the session itself
                AppDatabase.getDatabase(v.getContext()).chatSessionDao().deleteById(session.sessionId);
            }).start();
        });
    }

    @Override
    public int getItemCount() {
        return sessionList != null ? sessionList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        TextView tvSummary;
        View llRoot;
        TextView tvDelete;
        TextView tvUnreadCount;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvSummary = itemView.findViewById(R.id.tvSummary);
            llRoot = itemView.findViewById(R.id.llRoot);
            tvDelete = itemView.findViewById(R.id.tvDelete);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
        }
    }
}
