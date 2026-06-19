package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "calendar_events",
    indices = {
        @Index(value = "eventDate", name = "idx_calendar_events_date"),
        @Index(value = "startTime", name = "idx_calendar_events_start")
    })
public class CalendarEvent {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String title;          // 事件标题
    public String notes;          // 备注
    public String eventDate;      // "yyyy-MM-dd"
    public long startTime;        // 开始时间戳 (毫秒), 全天事件时为当天0点
    public long endTime;          // 结束时间戳 (毫秒), 全天事件时为当天23:59
    public boolean allDay;        // 是否全天事件
    public String recurrence;     // NONE / DAILY / WEEKLY / MONTHLY / YEARLY
    public long createdAt;

    public CalendarEvent() {
        this.recurrence = "NONE";
        this.allDay = false;
        this.createdAt = System.currentTimeMillis();
    }
}
