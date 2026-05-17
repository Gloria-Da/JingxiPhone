package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "memos")
public class Memo {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public int characterId;
    public String content;
    public String targetDate; // yyyy-MM-dd
    public int status; // 0 for pending, 1 for completed
    public long timestamp;
    public long createdAt;
}
