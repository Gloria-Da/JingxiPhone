package com.yoyo.jingxi.ui.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.CalendarEvent;
import com.yoyo.jingxi.data.entity.CycleRecord;
import com.yoyo.jingxi.data.entity.HolidayCache;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 日历选中日的详情列表适配器。
 * 展示：节日信息 → 经期记录 → 日程事件
 */
public class CalendarDayDetailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HOLIDAY = 0;
    private static final int TYPE_CYCLE = 1;
    private static final int TYPE_EVENT = 2;
    private static final int TYPE_EMPTY = 3;

    private final List<Object> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onEventClick(CalendarEvent event);
        void onCycleClick(CycleRecord record);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<CalendarEvent> events, CycleRecord cycleRecord, HolidayCache holiday) {
        items.clear();
        if (holiday != null) {
            items.add(holiday);
        }
        if (cycleRecord != null) {
            items.add(cycleRecord);
        }
        if (events != null) {
            items.addAll(events);
        }
        if (items.isEmpty()) {
            items.add("empty");
        }
        notifyDataSetChanged();
    }

    public void clear() {
        items.clear();
        items.add("empty");
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Object obj = items.get(position);
        if (obj instanceof HolidayCache) return TYPE_HOLIDAY;
        if (obj instanceof CycleRecord) return TYPE_CYCLE;
        if (obj instanceof CalendarEvent) return TYPE_EVENT;
        return TYPE_EMPTY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_EMPTY) {
            View view = inflater.inflate(R.layout.item_calendar_empty, parent, false);
            return new EmptyViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_calendar_event, parent, false);
        switch (viewType) {
            case TYPE_HOLIDAY: return new HolidayViewHolder(view);
            case TYPE_CYCLE:   return new CycleViewHolder(view);
            default:           return new EventViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object obj = items.get(position);
        if (holder instanceof HolidayViewHolder && obj instanceof HolidayCache) {
            ((HolidayViewHolder) holder).bind((HolidayCache) obj);
        } else if (holder instanceof CycleViewHolder && obj instanceof CycleRecord) {
            ((CycleViewHolder) holder).bind((CycleRecord) obj);
        } else if (holder instanceof EventViewHolder && obj instanceof CalendarEvent) {
            ((EventViewHolder) holder).bind((CalendarEvent) obj);
        } else if (holder instanceof EmptyViewHolder) {
            ((EmptyViewHolder) holder).bind();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ---- ViewHolders ----

    class HolidayViewHolder extends RecyclerView.ViewHolder {
        TextView tvEventTime, tvEventTitle, tvEventNotes;
        ImageView ivRecurrence;
        View colorTag;

        HolidayViewHolder(View v) {
            super(v);
            tvEventTime = v.findViewById(R.id.tvEventTime);
            tvEventTitle = v.findViewById(R.id.tvEventTitle);
            tvEventNotes = v.findViewById(R.id.tvEventNotes);
            ivRecurrence = v.findViewById(R.id.ivRecurrence);
            colorTag = v.findViewById(R.id.colorTag);
            tvEventNotes.setVisibility(View.GONE);
            ivRecurrence.setVisibility(View.GONE);
        }

        void bind(HolidayCache holiday) {
            tvEventTime.setText(holiday.isOffDay ? "🎉" : "🏢");
            tvEventTime.setTextSize(18);
            tvEventTitle.setText(holiday.isOffDay ? holiday.name + " · 休息日" : holiday.name + " · 补班日");
            tvEventTitle.setTextColor(holiday.isOffDay ? 0xFFE53935 : 0xFFEF6C00);
            if (colorTag != null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(8f);
                bg.setColor(holiday.isOffDay ? 0xFFE53935 : 0xFFEF6C00);
                colorTag.setBackground(bg);
            }
        }
    }

    class CycleViewHolder extends RecyclerView.ViewHolder {
        TextView tvEventTime, tvEventTitle, tvEventNotes;
        ImageView ivRecurrence;
        View colorTag;

        CycleViewHolder(View v) {
            super(v);
            tvEventTime = v.findViewById(R.id.tvEventTime);
            tvEventTitle = v.findViewById(R.id.tvEventTitle);
            tvEventNotes = v.findViewById(R.id.tvEventNotes);
            ivRecurrence = v.findViewById(R.id.ivRecurrence);
            colorTag = v.findViewById(R.id.colorTag);
            ivRecurrence.setVisibility(View.GONE);

            if (colorTag != null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(8f);
                bg.setColor(0xFFE57373);
                colorTag.setBackground(bg);
            }

            v.setOnClickListener(vi -> {
                if (listener != null) {
                    Object obj = items.get(getAdapterPosition());
                    if (obj instanceof CycleRecord) listener.onCycleClick((CycleRecord) obj);
                }
            });
        }

        void bind(CycleRecord record) {
            tvEventTime.setText("🩸");
            tvEventTime.setTextSize(18);
            tvEventTitle.setText("经期  " + record.startDate + " ~ " + record.endDate);

            String detail = "";
            String[] flowLabels = {"", "少量", "中量", "大量"};
            if (record.flowLevel >= 1 && record.flowLevel <= 3) {
                detail = flowLabels[record.flowLevel];
            }
            if (record.symptoms != null && !record.symptoms.isEmpty()) {
                if (!detail.isEmpty()) detail += "  ·  ";
                detail += record.symptoms;
            }
            if (!detail.isEmpty()) {
                tvEventNotes.setText(detail);
                tvEventNotes.setVisibility(View.VISIBLE);
            } else {
                tvEventNotes.setVisibility(View.GONE);
            }
        }
    }

    class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvEventTime, tvEventTitle, tvEventNotes;
        ImageView ivRecurrence;
        View colorTag;

        EventViewHolder(View v) {
            super(v);
            tvEventTime = v.findViewById(R.id.tvEventTime);
            tvEventTitle = v.findViewById(R.id.tvEventTitle);
            tvEventNotes = v.findViewById(R.id.tvEventNotes);
            ivRecurrence = v.findViewById(R.id.ivRecurrence);
            colorTag = v.findViewById(R.id.colorTag);

            if (colorTag != null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(8f);
                bg.setColor(0xFF81C784);
                colorTag.setBackground(bg);
            }

            v.setOnClickListener(vi -> {
                if (listener != null) {
                    Object obj = items.get(getAdapterPosition());
                    if (obj instanceof CalendarEvent) listener.onEventClick((CalendarEvent) obj);
                }
            });
        }

        void bind(CalendarEvent event) {
            tvEventTitle.setText(event.title);
            tvEventTime.setTextSize(13);

            if (event.allDay) {
                tvEventTime.setText("全天");
            } else if (event.startTime > 0) {
                SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                if (event.endTime > 0 && event.endTime != event.startTime) {
                    // 时间段
                    tvEventTime.setText(tf.format(new Date(event.startTime)) + "~");
                } else {
                    // 时间点
                    tvEventTime.setText(tf.format(new Date(event.startTime)));
                }
            } else {
                tvEventTime.setText("");
            }

            if (event.notes != null && !event.notes.isEmpty()) {
                tvEventNotes.setText(event.notes);
                tvEventNotes.setVisibility(View.VISIBLE);
            } else {
                tvEventNotes.setVisibility(View.GONE);
            }

            if (event.recurrence != null && !event.recurrence.equals("NONE")) {
                ivRecurrence.setVisibility(View.VISIBLE);
            } else {
                ivRecurrence.setVisibility(View.GONE);
            }
        }
    }

    class EmptyViewHolder extends RecyclerView.ViewHolder {
        TextView tvEventTime, tvEventTitle, tvEventNotes;
        ImageView ivRecurrence;
        View colorTag;

        EmptyViewHolder(View v) {
            super(v);
            tvEventTime = v.findViewById(R.id.tvEventTime);
            tvEventTitle = v.findViewById(R.id.tvEventTitle);
            tvEventNotes = v.findViewById(R.id.tvEventNotes);
            ivRecurrence = v.findViewById(R.id.ivRecurrence);
            colorTag = v.findViewById(R.id.colorTag);
            if (ivRecurrence != null) ivRecurrence.setVisibility(View.GONE);
        }

        void bind() {
            tvEventTime.setText("📅");
            tvEventTime.setTextSize(20);
            tvEventTitle.setText("今天没有记录");
            tvEventTitle.setTextColor(0xFFAAAAAA);
            tvEventNotes.setVisibility(View.GONE);
            if (colorTag != null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(8f);
                bg.setColor(0xFFDDDDDD);
                colorTag.setBackground(bg);
            }
        }
    }
}
