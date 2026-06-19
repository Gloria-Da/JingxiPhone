package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "semester_configs")
public class SemesterConfig {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;            // "2026春季学期"
    public String startDate;       // 学期第一天 "yyyy-MM-dd"
    public int totalWeeks;         // 总周数
    public int periodDuration;     // 每节课时长（分钟），默认45
    public int periodBreak;        // 课间休息（分钟），默认10
    public String firstPeriodStart; // 第一节开始时间 "HH:mm"，默认"08:00"
    public int periodsPerDay;      // 每天节数，默认12
    public String customPeriods;   // 自定义时间段 null=自动计算, "08:00-08:45,08:50-09:35,..."
    public boolean isActive;       // 是否为当前学期
    public boolean isDefault;      // 已弃用，仅保留用于 DB schema 兼容

    public SemesterConfig() {
        this.periodDuration = 45;
        this.periodBreak = 10;
        this.firstPeriodStart = "08:00";
        this.periodsPerDay = 12;
        this.totalWeeks = 18;
    }
}
