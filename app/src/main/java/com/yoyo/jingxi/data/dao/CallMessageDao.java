package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.yoyo.jingxi.data.entity.CallMessage;

import java.util.List;

@Dao
public interface CallMessageDao {
    @Insert
    long insert(CallMessage callMessage);

    @Query("SELECT * FROM call_messages WHERE callId = :callId ORDER BY timestamp ASC")
    LiveData<List<CallMessage>> getCallMessagesByCallId(int callId);
    
    @Query("SELECT * FROM call_messages WHERE callId = :callId ORDER BY timestamp ASC")
    List<CallMessage> getCallMessagesByCallIdSync(int callId);
}