package com.yoyo.jingxi.ui.model;

/**
 * 日历格子数据模型。每个格子代表日历中的一个日期。
 */
public class CalendarDayItem {
    public int year;
    public int month;           // 0-based (0=January)
    public int dayOfMonth;
    public boolean isCurrentMonth;  // 是否属于当前显示的月份
    public boolean isToday;
    public boolean isSelected;

    // 标记位
    public boolean hasEvent;        // 有日程事件
    public boolean hasPeriod;       // 有经期记录（实际记录）
    public boolean isPredictedPeriod; // 预测经期
    public boolean isOvulation;     // 排卵期
    public boolean isHoliday;       // 是节假日
    public boolean isWorkday;       // 是调休补班日（节假日中的工作日）

    public String holidayName;      // 节日名称
    public int eventCount;          // 该日的事件数量

    public CalendarDayItem(int year, int month, int dayOfMonth, boolean isCurrentMonth) {
        this.year = year;
        this.month = month;
        this.dayOfMonth = dayOfMonth;
        this.isCurrentMonth = isCurrentMonth;
    }

    /**
     * 返回 yyyy-MM-dd 格式的日期字符串
     */
    public String getDateString() {
        return String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth);
    }
}
