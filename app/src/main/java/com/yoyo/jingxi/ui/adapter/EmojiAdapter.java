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
import com.yoyo.jingxi.data.entity.EmojiEntry;

import java.util.ArrayList;
import java.util.List;

public class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder> {

    private List<EmojiEntry> emojis = new ArrayList<>();
    private OnEmojiClickListener listener;

    public interface OnEmojiClickListener {
        void onEmojiClick(EmojiEntry emoji);
        void onEmojiLongClick(EmojiEntry emoji, View view);
    }

    public void setOnEmojiClickListener(OnEmojiClickListener listener) {
        this.listener = listener;
    }

    public void setEmojis(List<EmojiEntry> emojis) {
        this.emojis = emojis;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emoji, parent, false);
        return new EmojiViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmojiViewHolder holder, int position) {
        EmojiEntry emoji = emojis.get(position);
        holder.tvEmojiName.setText(emoji.name);
        
        android.content.Context ctx = holder.itemView.getContext();
        boolean isValid = true;
        if (ctx instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) ctx;
            isValid = !activity.isFinishing() && !activity.isDestroyed();
        }
        if (isValid) {
            Glide.with(ctx)
                    .load(emoji.imageUrl)
                    .into(holder.ivEmoji);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEmojiClick(emoji);
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onEmojiLongClick(emoji, holder.itemView);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return emojis.size();
    }

    static class EmojiViewHolder extends RecyclerView.ViewHolder {
        ImageView ivEmoji;
        TextView tvEmojiName;

        public EmojiViewHolder(@NonNull View itemView) {
            super(itemView);
            ivEmoji = itemView.findViewById(R.id.ivEmoji);
            tvEmojiName = itemView.findViewById(R.id.tvEmojiName);
        }
    }
}
