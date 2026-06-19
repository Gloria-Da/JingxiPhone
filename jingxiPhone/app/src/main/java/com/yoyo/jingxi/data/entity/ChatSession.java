package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_sessions")
public class ChatSession {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int characterId;
    public String myPersonaName;
    public long lastMessageTimestamp;
    
    public boolean isPinned;
    
    public int unreadCount;
}
