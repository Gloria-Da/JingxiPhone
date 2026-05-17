package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.io.Serializable;

@Entity(tableName = "worldbook_entries")
public class WorldbookEntry implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    // 0: 前 (Pre), 1: 中 (Mid), 2: 后 (Post)
    public int type; 
    
    public String title;
    
    // 触发关键词，多个以英文逗号分隔，主要用于中世界书
    public String keyword; 
    
    public String content;
    
    public boolean isEnabled;
}
