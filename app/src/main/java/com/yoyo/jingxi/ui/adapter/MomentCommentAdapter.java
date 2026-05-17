package com.yoyo.jingxi.ui.adapter;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.MomentComment;

import java.util.ArrayList;
import java.util.List;

public class MomentCommentAdapter extends RecyclerView.Adapter<MomentCommentAdapter.ViewHolder> {

    private final Context context;
    private List<MomentComment> comments = new ArrayList<>();
    private final OnCommentClickListener listener;

    public interface OnCommentClickListener {
        void onCommentClick(MomentComment comment);
    }

    public MomentCommentAdapter(Context context, OnCommentClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setComments(List<MomentComment> comments) {
        this.comments = comments;
        notifyDataSetActivity();
    }

    private void notifyDataSetActivity() {
        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_moment_comment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MomentComment comment = comments.get(position);
        
        SpannableStringBuilder builder = new SpannableStringBuilder();
        
        // 作者名字
        String authorName = comment.authorName != null ? comment.authorName : "未知";
        int start = builder.length();
        builder.append(authorName);
        builder.setSpan(new ForegroundColorSpan(0xFF576B95), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        // 如果是回复某人
        if (comment.replyToId != null && !comment.replyToId.isEmpty()) {
            builder.append("回复");
            String replyName = comment.replyToName != null ? comment.replyToName : "未知";
            start = builder.length();
            builder.append(replyName);
            builder.setSpan(new ForegroundColorSpan(0xFF576B95), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        builder.append("：");
        builder.append(comment.content != null ? comment.content : "");
        
        holder.tvCommentContent.setText(builder);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCommentClick(comment);
            }
        });
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCommentContent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCommentContent = itemView.findViewById(R.id.tv_comment_content);
        }
    }
}
