package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.yoyo.jingxi.data.entity.Message;

import java.util.List;

@Dao
public interface MessageDao {
    @Insert
    long insert(Message message);

    @androidx.room.Delete
    void delete(Message message);

    @androidx.room.Update
    void update(Message message);

    @Query("SELECT * FROM messages WHERE characterId = :characterId ORDER BY timestamp ASC")
    LiveData<List<Message>> getMessagesByCharacterId(int characterId);

    @Query("SELECT * FROM messages WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT :limit")
    List<Message> getRecentMessagesSync(int characterId, int limit);

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    LiveData<List<Message>> getMessagesBySessionId(int sessionId);

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    List<Message> getRecentMessagesBySessionIdSync(int sessionId, int limit);

    @Query("SELECT * FROM messages WHERE id = :id")
    Message getMessageByIdSync(int id);

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId AND timestamp > :timestamp")
    int getMessagesCountSinceSync(int sessionId, long timestamp);

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    void deleteMessagesBySessionId(int sessionId);

    @Query("SELECT * FROM messages WHERE characterId = :characterId AND timestamp > :timestamp ORDER BY timestamp ASC")
    List<Message> getMessagesSince(int characterId, long timestamp);
}
