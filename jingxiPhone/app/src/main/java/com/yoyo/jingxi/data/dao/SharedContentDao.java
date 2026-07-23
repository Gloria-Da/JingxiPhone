package com.yoyo.jingxi.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.SharedContent;

import java.util.List;

@Dao
public interface SharedContentDao {
    @Insert
    long insert(SharedContent content);

    @Update
    void update(SharedContent content);

    @Delete
    void delete(SharedContent content);

    @Query("SELECT * FROM shared_contents WHERE messageId = :messageId LIMIT 1")
    SharedContent getByMessageId(int messageId);

    @Query("SELECT * FROM shared_contents WHERE id = :id LIMIT 1")
    SharedContent getById(int id);

    @Query("SELECT * FROM shared_contents WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    List<SharedContent> getBySessionId(int sessionId);

    @Query("SELECT * FROM shared_contents WHERE sourceUrl = :url AND sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    SharedContent getByUrlAndSession(String url, int sessionId);
}
