package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "course_entries",
    indices = {
        @Index(value = "semesterId", name = "idx_course_semester"),
        @Index(value = "dayOfWeek", name = "idx_course_day")
    })
public class CourseEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int semesterId;         // 所属学期
    public String name;            // 课程名称
    public String teacher;         // 老师
    public String location;        // 教室/地点
    public int dayOfWeek;          // 0=周一 ... 6=周日
    public int startPeriod;        // 开始节次（1-based）
    public int periodCount;        // 连上几节（默认1）
    public String weekPattern;     // EVERY / ODD / EVEN 或 "1,3-5,8"
    public int color;             // 卡片颜色
    public String notes;          // 备注
    public long createdAt;

    public CourseEntry() {
        this.periodCount = 1;
        this.weekPattern = "EVERY";
        this.color = 0xFFBBDEFB;
        this.createdAt = System.currentTimeMillis();
    }
}
