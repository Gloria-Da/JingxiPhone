package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.Memo;

import java.util.List;

@Dao
public interface MemoDao {
    @Query("SELECT * FROM memos WHERE characterId = :characterId ORDER BY targetDate ASC, timestamp DESC")
    LiveData<List<Memo>> getMemosByCharacterId(int characterId);

    @Query("SELECT * FROM memos WHERE characterId = :characterId AND targetDate = :targetDate ORDER BY timestamp DESC")
    List<Memo> getMemosByDate(int characterId, String targetDate);

    @Query("SELECT * FROM memos WHERE characterId = :characterId AND targetDate <= :targetDate AND status = 0 ORDER BY targetDate ASC")
    List<Memo> getPendingMemos(int characterId, String targetDate);

    @Query("SELECT * FROM memos WHERE characterId = :characterId AND status = 0 ORDER BY timestamp DESC")
    List<Memo> getPendingMemosSync(int characterId);

    @Insert
    long insert(Memo memo);

    @Update
    void update(Memo memo);

    @Delete
    void delete(Memo memo);

    @Query("SELECT * FROM memos WHERE id = :id")
    Memo getMemoByIdSync(int id);
}
