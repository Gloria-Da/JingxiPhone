package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;

import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.Message;

import java.util.List;

@Dao
public interface SessionWithLastMessageDao {

    static class SessionWithLastMessage {
        public int sessionId;
        public String friendName;
        public String friendAvatarUrl;
        public String myPersonaName;
        public String lastMessageContent;
        public long lastMessageTimestamp;
        public boolean isPinned;
        public int unreadCount;
    }

    @Transaction
    @Query("SELECT s.id as sessionId, c.name as friendName, c.avatarPath as friendAvatarUrl, " +
            "s.myPersonaName as myPersonaName, " +
            "s.isPinned as isPinned, s.unreadCount as unreadCount, " +
            "(SELECT content FROM messages WHERE sessionId = s.id ORDER BY timestamp DESC LIMIT 1) as lastMessageContent, " +
            "(SELECT timestamp FROM messages WHERE sessionId = s.id ORDER BY timestamp DESC LIMIT 1) as lastMessageTimestamp " +
            "FROM chat_sessions s " +
            "LEFT JOIN characters c ON s.characterId = c.id " +
            "ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    LiveData<List<SessionWithLastMessage>> getSessionsWithLastMessage();
}
