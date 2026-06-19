package com.yoyo.jingxi.ui.adapter;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.ui.model.CalendarDayItem;

import java.util.ArrayList;
import java.util.List;

public class CalendarGridAdapter extends RecyclerView.Adapter<CalendarGridAdapter.ViewHolder> {

    private List<CalendarDayItem> items = new ArrayList<>();
    private OnDayClickListener clickListener;
    private OnDayLongClickListener longClickListener;
    private OnOverflowDayClickListener overflowClickListener;

    // Theme-resolved colors
    private int colorTextPrimary;
    private int colorTextSecondary;
    // Resource-based colors (auto-switch light/dark via values-night)
    private int colorPeriodText;
    private int colorSundayText;
    private int colorSaturdayText;
    private int colorNonCurrentMonth;
    private int colorSelectedText;
    private boolean colorsResolved = false;

    public interface OnDayClickListener {
        void onDayClick(CalendarDayItem item);
    }

    public interface OnDayLongClickListener {
        void onDayLongClick(CalendarDayItem item);
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnDayLongClickListener(OnDayLongClickListener listener) {
        this.longClickListener = listener;
    }

    public interface OnOverflowDayClickListener {
        void onOverflowDayClick(CalendarDayItem item);
    }

    public void setOnOverflowDayClickListener(OnOverflowDayClickListener listener) {
        this.overflowClickListener = listener;
    }

    public void setItems(List<CalendarDayItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public List<CalendarDayItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();
        View view = LayoutInflater.from(ctx).inflate(R.layout.item_calendar_day, parent, false);

        if (!colorsResolved) {
            // Theme attributes
            TypedArray ta = ctx.obtainStyledAttributes(new int[]{
                    R.attr.colorTextPrimary,
                    R.attr.colorTextSecondary
            });
            colorTextPrimary = ta.getColor(0, 0xFF333333);
            colorTextSecondary = ta.getColor(1, 0xFF888888);
            ta.recycle();

            // Resource-based colors (respect values-night)
            colorPeriodText = ContextCompat.getColor(ctx, R.color.calendar_period_text);
            colorSundayText = ContextCompat.getColor(ctx, R.color.calendar_sunday_text);
            colorSaturdayText = ContextCompat.getColor(ctx, R.color.calendar_saturday_text);
            colorNonCurrentMonth = 0xFF666666;
            colorSelectedText = 0xFFFFFFFF;

            colorsResolved = true;
        }

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CalendarDayItem item = items.get(position);
        holder.bind(item, clickListener, longClickListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvDayNumber;
        private final TextView tvHolidayName;
        private final View dayBackground;
        private final View periodBackground;
        private final View predictedBackground;
        private final View ovulationBackground;
        private final View holidayBackground;
        private final View dotPeriod;
        private final View dotOvulation;
        private final View dotEvent;
        private final View dotRow;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayNumber = itemView.findViewById(R.id.tvDayNumber);
            tvHolidayName = itemView.findViewById(R.id.tvHolidayName);
            dayBackground = itemView.findViewById(R.id.dayBackground);
            periodBackground = itemView.findViewById(R.id.periodBackground);
            predictedBackground = itemView.findViewById(R.id.predictedBackground);
            ovulationBackground = itemView.findViewById(R.id.ovulationBackground);
            holidayBackground = itemView.findViewById(R.id.holidayBackground);
            dotPeriod = itemView.findViewById(R.id.dotPeriod);
            dotOvulation = itemView.findViewById(R.id.dotOvulation);
            dotEvent = itemView.findViewById(R.id.dotEvent);
            dotRow = itemView.findViewById(R.id.dotRow);
        }

        void bind(CalendarDayItem item, OnDayClickListener clickL, OnDayLongClickListener longL) {
            tvDayNumber.setText(String.valueOf(item.dayOfMonth));

            // Reset all
            dayBackground.setBackgroundResource(android.R.color.transparent);
            dayBackground.setVisibility(View.GONE);
            periodBackground.setVisibility(View.GONE);
            predictedBackground.setVisibility(View.GONE);
            ovulationBackground.setVisibility(View.GONE);
            holidayBackground.setVisibility(View.GONE);
            dotPeriod.setVisibility(View.GONE);
            dotOvulation.setVisibility(View.GONE);
            dotEvent.setVisibility(View.GONE);
            dotRow.setVisibility(View.GONE);
            tvHolidayName.setVisibility(View.GONE);
            tvDayNumber.setTypeface(null, Typeface.NORMAL);

            // --- Text color ---
            if (!item.isCurrentMonth) {
                tvDayNumber.setTextColor(colorNonCurrentMonth);
            } else if (item.isSelected) {
                tvDayNumber.setTextColor(colorSelectedText);
            } else if (item.hasPeriod) {
                tvDayNumber.setTextColor(colorPeriodText);
            } else {
                int dow = getAdapterPosition() % 7;
                if (dow == 0) {
                    tvDayNumber.setTextColor(colorSundayText);
                } else if (dow == 6) {
                    tvDayNumber.setTextColor(colorSaturdayText);
                } else {
                    tvDayNumber.setTextColor(colorTextPrimary);
                }
            }

            // --- Background ---
            if (item.isSelected) {
                dayBackground.setVisibility(View.VISIBLE);
                dayBackground.setBackgroundResource(R.drawable.bg_calendar_day_selected);
                tvDayNumber.setTextColor(colorSelectedText);
                tvDayNumber.setTypeface(null, Typeface.BOLD);
            } else if (item.hasPeriod) {
                periodBackground.setVisibility(View.VISIBLE);
                tvDayNumber.setTypeface(null, Typeface.BOLD);
            } else if (item.isPredictedPeriod) {
                predictedBackground.setVisibility(View.VISIBLE);
            } else if (item.isOvulation) {
                ovulationBackground.setVisibility(View.VISIBLE);
            } else if (item.isHoliday) {
                holidayBackground.setVisibility(View.VISIBLE);
            } else if (item.isToday) {
                dayBackground.setVisibility(View.VISIBLE);
                dayBackground.setBackgroundResource(R.drawable.bg_calendar_day_today);
                tvDayNumber.setTypeface(null, Typeface.BOLD);
            }

            // --- Dots ---
            boolean showDots = false;
            if (item.hasPeriod && !item.isSelected) {
                dotPeriod.setVisibility(View.VISIBLE);
                showDots = true;
            }
            if (item.isOvulation && !item.isSelected && !item.hasPeriod) {
                dotOvulation.setVisibility(View.VISIBLE);
                showDots = true;
            }
            if (item.hasEvent) {
                dotEvent.setVisibility(View.VISIBLE);
                showDots = true;
            }
            if (showDots) {
                dotRow.setVisibility(View.VISIBLE);
            }

            // --- Holiday label ---
            if (item.isHoliday && item.holidayName != null && !item.holidayName.isEmpty()) {
                tvHolidayName.setVisibility(View.VISIBLE);
                String shortName = item.holidayName.length() > 3
                        ? item.holidayName.substring(0, 3)
                        : item.holidayName;
                tvHolidayName.setText(shortName);
            }

            // --- Click / Long-click ---
            itemView.setAlpha(1.0f);
            if (item.isCurrentMonth) {
                itemView.setOnClickListener(v -> {
                    if (clickL != null) clickL.onDayClick(item);
                });
                itemView.setOnLongClickListener(v -> {
                    if (longL != null) {
                        longL.onDayLongClick(item);
                        return true;
                    }
                    return false;
                });
            } else {
                // 溢出日：点击翻到对应月份
                itemView.setOnClickListener(v -> {
                    if (overflowClickListener != null) overflowClickListener.onOverflowDayClick(item);
                });
                itemView.setOnLongClickListener(null);
            }
        }
    }
}
