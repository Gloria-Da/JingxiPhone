package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "emoji_entries")
public class EmojiEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public String imageUrl;
    public String groupName;
}
