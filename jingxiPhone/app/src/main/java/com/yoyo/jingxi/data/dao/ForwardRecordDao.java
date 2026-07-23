package com.yoyo.jingxi.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.yoyo.jingxi.data.entity.ForwardRecord;

import java.util.List;

@Dao
public interface ForwardRecordDao {
    @Insert
    long insert(ForwardRecord record);

    @Query("SELECT * FROM forward_records WHERE forwardBatchId = :batchId")
    List<ForwardRecord> getByBatchId(String batchId);

    @Query("SELECT * FROM forward_records WHERE targetSessionId = :sessionId ORDER BY forwardTimestamp DESC")
    List<ForwardRecord> getByTargetSessionId(int sessionId);

    @Query("SELECT * FROM forward_records WHERE sourceMessageId = :messageId")
    List<ForwardRecord> getBySourceMessageId(int messageId);
}
