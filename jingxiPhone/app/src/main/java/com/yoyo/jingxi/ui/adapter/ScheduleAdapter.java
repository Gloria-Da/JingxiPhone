package com.yoyo.jingxi.ui.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.DailySchedule;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private List<DailySchedule.ScheduleItem> items = new ArrayList<>();
    private String currentTimeStr; // "HH:mm"

    public ScheduleAdapter() {
        updateCurrentTime();
    }

    public void updateCurrentTime() {
        this.currentTimeStr = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    public void setItems(List<DailySchedule.ScheduleItem> newItems, String scheduleDate) {
        this.items.clear();
        if (newItems != null) {
            updateCurrentTime();
            
            // Check if the schedule is from a past date
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            boolean isPastDate = false;
            if (scheduleDate != null && scheduleDate.compareTo(today) < 0) {
                isPastDate = true;
            }
            
            for (DailySchedule.ScheduleItem item : newItems) {
                // 如果是过去的日期，显示所有事件；如果是今天且是随机事件，只有时间到了才显示
                if (!isPastDate && item.isRandomEvent) {
                    String startTime = getStartTime(item.time);
                    if (currentTimeStr.compareTo(startTime) < 0) {
                        continue; // 时间还没到，直接不显示（隐藏惊喜）
                    }
                }
                
                // Store the parsed past date status on the item for binding
                // (Since we don't want to modify the entity structure, we'll just evaluate it in onBindViewHolder)
                this.items.add(item);
            }
            
            // 记录当前是否是过去的日期，以便 onBindViewHolder 使用
            this.currentScheduleIsPast = isPastDate;
        }
        notifyDataSetChanged();
    }

    private boolean currentScheduleIsPast = false;

    private String getStartTime(String timeStr) {
        if (timeStr == null) return "00:00";
        if (timeStr.contains("-")) {
            return timeStr.split("-")[0].trim();
        }
        return timeStr.trim();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DailySchedule.ScheduleItem item = items.get(position);
        
        holder.tvTime.setText(item.time);
        holder.tvAction.setText(item.action);

        String startTime = getStartTime(item.time);

        if (currentScheduleIsPast || currentTimeStr.compareTo(startTime) >= 0) {
            // 是过去的日期，或者今天时间已到，展示状态和感受
            holder.llFeeling.setVisibility(View.VISIBLE);
            if (item.isRandomEvent) {
                holder.tvStatus.setText("★");
                holder.tvStatus.setTextColor(Color.parseColor("#FF9800")); // 橙色星星
            } else {
                holder.tvStatus.setText(item.completed ? "√" : "×");
                holder.tvStatus.setTextColor(item.completed ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336")); // 绿勾红叉
            }
            holder.tvFeeling.setText(item.feeling);
        } else {
            // 今天还没到的时间，不展示状态和感受
            holder.llFeeling.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTime;
        TextView tvAction;
        LinearLayout llFeeling;
        TextView tvStatus;
        TextView tvFeeling;

        ViewHolder(View view) {
            super(view);
            tvTime = view.findViewById(R.id.tvTime);
            tvAction = view.findViewById(R.id.tvAction);
            llFeeling = view.findViewById(R.id.llFeeling);
            tvStatus = view.findViewById(R.id.tvStatus);
            tvFeeling = view.findViewById(R.id.tvFeeling);
        }
    }
}