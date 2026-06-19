package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "schedule_entries")
public class ScheduleEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int characterId;
    public String date; // Format: "yyyy-MM-dd"
    public String contentJson; // JSON representation of DailySchedule
    public long timestamp;
}
