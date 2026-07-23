package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "cycle_records",
    indices = {
        @Index(value = "startDate", name = "idx_cycle_records_start"),
        @Index(value = "endDate", name = "idx_cycle_records_end")
    })
public class CycleRecord {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String startDate;     // 经期开始日期 "yyyy-MM-dd"
    public String endDate;       // 经期结束日期 "yyyy-MM-dd"
    public Integer flowLevel;       // 经血量: null=未指定, 1=少, 2=中, 3=多
    public String symptoms;      // 症状标签, 逗号分隔 如 "cramps,headache,fatigue"
    public String notes;         // 备注
    public long createdAt;

    public CycleRecord() {
        this.createdAt = System.currentTimeMillis();
    }
}
