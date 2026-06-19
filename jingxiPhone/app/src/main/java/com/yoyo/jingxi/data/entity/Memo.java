package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "memos",
    indices = {
        @Index(value = "characterId", name = "idx_memos_characterId"),
        @Index(value = "targetDate", name = "idx_memos_targetDate"),
        @Index(value = "status", name = "idx_memos_status")
    })
public class Memo {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public int characterId;
    public String content;
    public String targetDate; // yyyy-MM-dd
    public int status; // 0 for pending, 1 for completed
    public long createdAt;
}
