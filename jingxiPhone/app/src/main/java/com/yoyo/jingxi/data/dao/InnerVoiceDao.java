package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.InnerVoice;

import java.util.List;

@Dao
public interface InnerVoiceDao {
    @Insert
    long insert(InnerVoice innerVoice);

    @Update
    void update(InnerVoice innerVoice);

    @Delete
    void delete(InnerVoice innerVoice);

    // 按消息 ID 查心声（同步，供 Adapter 绑定用）
    @Query("SELECT * FROM inner_voices WHERE messageId = :messageId LIMIT 1")
    InnerVoice getByMessageIdSync(int messageId);

    // 按会话查所有心声
    @Query("SELECT * FROM inner_voices WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    LiveData<List<InnerVoice>> getBySessionId(int sessionId);

    // 按角色查所有心声
    @Query("SELECT * FROM inner_voices WHERE characterId = :characterId ORDER BY timestamp DESC")
    LiveData<List<InnerVoice>> getByCharacterId(int characterId);

    // 标记单条已读
    @Query("UPDATE inner_voices SET isRead = 1 WHERE id = :id")
    void markAsRead(int id);

    // 统计某会话的未读心声数
    @Query("SELECT COUNT(*) FROM inner_voices WHERE sessionId = :sessionId AND isRead = 0")
    LiveData<Integer> getUnreadCountBySession(int sessionId);

    // 按消息 ID 列表批量查询（同步，Adapter 批量绑定用）
    @Query("SELECT * FROM inner_voices WHERE messageId IN (:messageIds)")
    List<InnerVoice> getByMessageIdsSync(List<Integer> messageIds);

    // 按消息 ID 列表批量查询未读心声（红点用）
    @Query("SELECT * FROM inner_voices WHERE messageId IN (:messageIds) AND isRead = 0")
    List<InnerVoice> getUnreadByMessageIdsSync(List<Integer> messageIds);
}
