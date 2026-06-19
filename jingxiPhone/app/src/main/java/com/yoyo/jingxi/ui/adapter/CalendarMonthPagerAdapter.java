package com.yoyo.jingxi.ui.adapter;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.ui.model.CalendarDayItem;
import com.yoyo.jingxi.ui.widget.MonthCalendarView;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewPager2 适配器，每页一个月份。
 * 使用大偏移量模拟无限翻页。
 */
public class CalendarMonthPagerAdapter extends RecyclerView.Adapter<CalendarMonthPagerAdapter.MonthViewHolder> {

    public static final int BASE_POSITION = 5000;
    private static final int BASE_YEAR = 2026;
    private static final int BASE_MONTH = 5; // June 2026 (0-based)

    private final List<MonthCalendarView> activeViews = new ArrayList<>();

    private CalendarGridAdapter.OnDayClickListener dayClickListener;
    private CalendarGridAdapter.OnDayLongClickListener dayLongClickListener;
    private CalendarGridAdapter.OnOverflowDayClickListener overflowClickListener;

    public void setOnDayClickListener(CalendarGridAdapter.OnDayClickListener listener) {
        this.dayClickListener = listener;
    }

    public void setOnDayLongClickListener(CalendarGridAdapter.OnDayLongClickListener listener) {
        this.dayLongClickListener = listener;
    }

    public void setOnOverflowDayClickListener(CalendarGridAdapter.OnOverflowDayClickListener listener) {
        this.overflowClickListener = listener;
    }

    /**
     * 根据 position 计算对应的年月
     */
    public static int[] positionToYearMonth(int position) {
        int offset = position - BASE_POSITION;
        int totalMonths = BASE_YEAR * 12 + BASE_MONTH + offset;
        int year = totalMonths / 12;
        int month = totalMonths % 12;
        return new int[]{year, month};
    }

    /**
     * 根据年月计算 position
     */
    public static int yearMonthToPosition(int year, int month) {
        int totalMonths = year * 12 + month;
        int baseTotal = BASE_YEAR * 12 + BASE_MONTH;
        return BASE_POSITION + (totalMonths - baseTotal);
    }

    @Override
    public int getItemCount() {
        return Integer.MAX_VALUE; // 无限
    }

    @NonNull
    @Override
    public MonthViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MonthCalendarView view = new MonthCalendarView(parent.getContext());
        view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        view.setOnDayClickListener(dayClickListener);
        view.setOnDayLongClickListener(dayLongClickListener);
        view.setOnOverflowDayClickListener(overflowClickListener);
        return new MonthViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MonthViewHolder holder, int position) {
        int[] ym = positionToYearMonth(position);
        holder.view.setMonth(ym[0], ym[1]);
    }

    static class MonthViewHolder extends RecyclerView.ViewHolder {
        MonthCalendarView view;

        MonthViewHolder(@NonNull MonthCalendarView itemView) {
            super(itemView);
            this.view = itemView;
        }
    }
}
