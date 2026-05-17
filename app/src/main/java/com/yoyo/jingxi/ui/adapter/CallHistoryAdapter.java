package com.yoyo.jingxi.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.CallRecord;
import com.yoyo.jingxi.ui.activity.CallHistoryDetailActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CallHistoryAdapter extends RecyclerView.Adapter<CallHistoryAdapter.ViewHolder> {

    private List<CallRecord> records = new ArrayList<>();
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private Context context;

    public void setRecords(List<CallRecord> records) {
        this.records = records;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_call_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CallRecord record = records.get(position);
        
        holder.tvTime.setText(sdf.format(new Date(record.startTime)));
        
        long mins = record.duration / 60;
        long secs = record.duration % 60;
        holder.tvDuration.setText(String.format("时长: %02d:%02d", mins, secs));
        
        if (record.summary != null && !record.summary.isEmpty()) {
            holder.tvSummary.setText(record.summary);
            holder.tvSummary.setVisibility(View.VISIBLE);
        } else {
            holder.tvSummary.setVisibility(View.GONE);
        }
        
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, CallHistoryDetailActivity.class);
            intent.putExtra("call_record_id", record.id);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return records != null ? records.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTime;
        TextView tvDuration;
        TextView tvSummary;

        ViewHolder(View view) {
            super(view);
            tvTime = view.findViewById(R.id.tvTime);
            tvDuration = view.findViewById(R.id.tvDuration);
            tvSummary = view.findViewById(R.id.tvSummary);
        }
    }
}
