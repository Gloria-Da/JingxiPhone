package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.CallRecord;

import java.util.List;

@Dao
public interface CallRecordDao {
    @Insert
    long insert(CallRecord callRecord);

    @Update
    void update(CallRecord callRecord);

    @Delete
    void delete(CallRecord callRecord);

    @Query("SELECT * FROM call_records WHERE sessionId = :sessionId ORDER BY startTime DESC")
    LiveData<List<CallRecord>> getRecordsBySessionId(int sessionId);

    @Query("SELECT * FROM call_records WHERE id = :id")
    CallRecord getRecordByIdSync(int id);
}
