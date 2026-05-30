package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "memories")
public class Memory {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int characterId; // The character this memory is about
    public int type; // 0 for normal, 1 for important
    public String content;
    public int starLevel; // 1 to 5 for important memory
    public long timestamp;
    public String category; // e.g., "喜欢", "讨厌", "想要", "提醒", "其他"
}
