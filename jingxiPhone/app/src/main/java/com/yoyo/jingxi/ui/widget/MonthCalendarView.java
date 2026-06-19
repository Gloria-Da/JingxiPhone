package com.yoyo.jingxi.ui.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.ui.adapter.CalendarGridAdapter;
import com.yoyo.jingxi.ui.model.CalendarDayItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 自定义月份日历网格控件（单月）。
 * 作为 ViewPager2 的单页使用，不再处理手势。
 */
public class MonthCalendarView extends RecyclerView {

    private final CalendarGridAdapter adapter;
    private int currentYear;
    private int currentMonth; // 0-based
    private CalendarDayItem selectedDay;

    public MonthCalendarView(@NonNull Context context) {
        this(context, null);
    }

    public MonthCalendarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutManager(new GridLayoutManager(context, 7));
        adapter = new CalendarGridAdapter();
        setAdapter(adapter);
        setNestedScrollingEnabled(false);
        setItemAnimator(null); // 禁用格子动画，避免 ViewPager2 翻页时卡顿
    }

    public void setMonth(int year, int month) {
        this.currentYear = year;
        this.currentMonth = month;
        selectedDay = null;
        refreshGrid();
    }

    public int getCurrentYear() { return currentYear; }
    public int getCurrentMonth() { return currentMonth; }

    public CalendarDayItem getSelectedDay() { return selectedDay; }

    /**
     * 设置选中日期并刷新
     */
    public void selectDay(CalendarDayItem day) {
        if (selectedDay != null) selectedDay.isSelected = false;
        if (day != null) day.isSelected = true;
        this.selectedDay = day;
        adapter.notifyDataSetChanged();
    }

    public void setOnDayClickListener(CalendarGridAdapter.OnDayClickListener l) {
        adapter.setOnDayClickListener(l);
    }

    public void setOnDayLongClickListener(CalendarGridAdapter.OnDayLongClickListener l) {
        adapter.setOnDayLongClickListener(l);
    }

    public void setOnOverflowDayClickListener(CalendarGridAdapter.OnOverflowDayClickListener l) {
        adapter.setOnOverflowDayClickListener(l);
    }

    public List<CalendarDayItem> getDayItems() { return adapter.getItems(); }
    public void notifyGridChanged() { adapter.notifyDataSetChanged(); }

    private void refreshGrid() {
        List<CalendarDayItem> items = new ArrayList<>();

        Calendar today = Calendar.getInstance();
        int todayYear = today.get(Calendar.YEAR);
        int todayMonth = today.get(Calendar.MONTH);
        int todayDay = today.get(Calendar.DAY_OF_MONTH);

        Calendar firstDay = Calendar.getInstance();
        firstDay.set(currentYear, currentMonth, 1);
        int daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH);
        int dayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK);
        int startOffset = dayOfWeek - 1;

        Calendar prevMonth = Calendar.getInstance();
        prevMonth.set(currentYear, currentMonth, 1);
        prevMonth.add(Calendar.MONTH, -1);
        int daysInPrevMonth = prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH);

        int totalCells = startOffset + daysInMonth;
        int remainder = totalCells % 7;
        if (remainder != 0) totalCells += (7 - remainder);

        for (int i = 0; i < totalCells; i++) {
            int displayMonth, displayYear, dayOfMonth;
            boolean isCurrentMonth;

            if (i < startOffset) {
                displayMonth = prevMonth.get(Calendar.MONTH);
                displayYear = prevMonth.get(Calendar.YEAR);
                dayOfMonth = daysInPrevMonth - startOffset + i + 1;
                isCurrentMonth = false;
            } else if (i >= startOffset + daysInMonth) {
                displayMonth = (currentMonth + 1) % 12;
                displayYear = currentYear + (currentMonth + 1) / 12;
                dayOfMonth = i - startOffset - daysInMonth + 1;
                isCurrentMonth = false;
            } else {
                displayMonth = currentMonth;
                displayYear = currentYear;
                dayOfMonth = i - startOffset + 1;
                isCurrentMonth = true;
            }

            CalendarDayItem item = new CalendarDayItem(displayYear, displayMonth, dayOfMonth, isCurrentMonth);
            item.isToday = (displayYear == todayYear && displayMonth == todayMonth && dayOfMonth == todayDay);
            items.add(item);
        }

        adapter.setItems(items);
    }
}
