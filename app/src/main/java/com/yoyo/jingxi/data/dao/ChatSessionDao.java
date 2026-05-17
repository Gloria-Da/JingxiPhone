package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.ChatSession;

import java.util.List;

@Dao
public interface ChatSessionDao {
    @Insert
    long insert(ChatSession chatSession);

    @Update
    void update(ChatSession chatSession);

    @Delete
    void delete(ChatSession chatSession);

    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageTimestamp DESC")
    LiveData<List<ChatSession>> getAllSessions();

    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageTimestamp DESC")
    List<ChatSession> getAllSessionsSync();

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    ChatSession getSessionById(int id);
    
    @Query("UPDATE chat_sessions SET isPinned = :isPinned WHERE id = :id")
    void updatePinnedStatus(int id, boolean isPinned);

    @Query("UPDATE chat_sessions SET unreadCount = :count WHERE id = :id")
    void updateUnreadCount(int id, int count);

    @Query("UPDATE chat_sessions SET unreadCount = unreadCount + :count WHERE id = :id")
    void incrementUnreadCount(int id, int count);
    
    @Query("SELECT * FROM chat_sessions WHERE characterId = :characterId AND myPersonaName = :myPersonaName LIMIT 1")
    ChatSession getSessionByCharacterAndPersona(int characterId, String myPersonaName);

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    void deleteById(int id);
}
