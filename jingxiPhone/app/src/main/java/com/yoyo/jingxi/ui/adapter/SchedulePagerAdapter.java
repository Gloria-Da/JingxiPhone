package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.DailySchedule;
import com.yoyo.jingxi.data.entity.ScheduleEntry;

import java.util.ArrayList;
import java.util.List;

public class SchedulePagerAdapter extends RecyclerView.Adapter<SchedulePagerAdapter.SchedulePageViewHolder> {

    private List<ScheduleEntry> entries = new ArrayList<>();
    private Gson gson = new Gson();

    public void setEntries(List<ScheduleEntry> newEntries) {
        this.entries = newEntries;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SchedulePageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule_page, parent, false);
        return new SchedulePageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SchedulePageViewHolder holder, int position) {
        ScheduleEntry entry = entries.get(position);
        DailySchedule schedule = gson.fromJson(entry.contentJson, DailySchedule.class);
        holder.bind(schedule, entry.date);
    }

    @Override
    public int getItemCount() {
        return entries == null ? 0 : entries.size();
    }

    class SchedulePageViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate;
        TextView tvWeather;
        TextView tvOverallPlan;
        RecyclerView rvScheduleItems;
        ScheduleAdapter itemAdapter;

        public SchedulePageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvWeather = itemView.findViewById(R.id.tvWeather);
            tvOverallPlan = itemView.findViewById(R.id.tvOverallPlan);
            rvScheduleItems = itemView.findViewById(R.id.rvScheduleItems);
            
            rvScheduleItems.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            itemAdapter = new ScheduleAdapter();
            rvScheduleItems.setAdapter(itemAdapter);
        }

        public void bind(DailySchedule schedule, String scheduleDate) {
            if (schedule != null) {
                tvDate.setText(schedule.date);
                tvWeather.setText(schedule.weather);
                tvOverallPlan.setText(schedule.overallPlan);
                itemAdapter.setItems(schedule.items, scheduleDate);
            }
        }
    }
}
