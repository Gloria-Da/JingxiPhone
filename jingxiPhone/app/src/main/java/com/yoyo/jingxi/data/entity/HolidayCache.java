package com.yoyo.jingxi.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "holiday_cache")
public class HolidayCache {
    @PrimaryKey
    @NonNull
    public String date;          // "yyyy-MM-dd" 作为主键

    public String name;          // 节日名称 (如 "元旦", "春节")
    public boolean isOffDay;     // true=休息日, false=调休补班日
    public long fetchedAt;       // 数据拉取时间戳
}
