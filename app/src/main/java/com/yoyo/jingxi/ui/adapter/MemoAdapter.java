package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.Memo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MemoAdapter extends RecyclerView.Adapter<MemoAdapter.ViewHolder> {

    private List<Memo> items = new ArrayList<>();
    private OnMemoClickListener listener;

    public interface OnMemoClickListener {
        void onMemoClick(Memo memo);
        void onMemoLongClick(Memo memo, View view);
    }

    public void setOnMemoClickListener(OnMemoClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Memo> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Memo memo = items.get(position);
        
        holder.tvMemoContent.setText(memo.content);
        
        if (!android.text.TextUtils.isEmpty(memo.targetDate) && memo.status != 2) {
            holder.tvMemoDate.setVisibility(View.VISIBLE);
            holder.tvMemoDate.setText(memo.targetDate);
        } else {
            holder.tvMemoDate.setVisibility(View.GONE);
        }
        
        if (memo.status == 1) {
            holder.tvMemoStatus.setText("已完成");
            holder.tvMemoStatus.setTextColor(0xFF4CAF50); // Green
        } else if (memo.status == 2) {
            holder.tvMemoStatus.setText("仅记录");
            holder.tvMemoStatus.setTextColor(0xFF9E9E9E); // Grey
        } else {
            holder.tvMemoStatus.setText("待完成");
            holder.tvMemoStatus.setTextColor(0xFFFF9800); // Orange
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
        String timeStr = sdf.format(new java.util.Date(memo.createdAt > 0 ? memo.createdAt : memo.timestamp));
        holder.tvMemoCreatedAt.setText("记录时间：" + timeStr);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMemoClick(memo);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onMemoLongClick(memo, v);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMemoContent;
        TextView tvMemoDate;
        TextView tvMemoStatus;
        TextView tvMemoCreatedAt;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMemoContent = itemView.findViewById(R.id.tvMemoContent);
            tvMemoDate = itemView.findViewById(R.id.tvMemoDate);
            tvMemoStatus = itemView.findViewById(R.id.tvMemoStatus);
            tvMemoCreatedAt = itemView.findViewById(R.id.tvMemoCreatedAt);
        }
    }
}
