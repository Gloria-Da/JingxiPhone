package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.ScheduleEntry;

import java.util.List;

@Dao
public interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ScheduleEntry entry);

    @Update
    void update(ScheduleEntry entry);

    @Query("SELECT * FROM schedule_entries WHERE characterId = :characterId AND date = :date LIMIT 1")
    ScheduleEntry getScheduleByDate(int characterId, String date);

    @Query("SELECT * FROM schedule_entries WHERE characterId = :characterId ORDER BY date ASC")
    LiveData<List<ScheduleEntry>> getAllSchedules(int characterId);
    
    @Query("SELECT * FROM schedule_entries WHERE characterId = :characterId ORDER BY date ASC")
    List<ScheduleEntry> getAllSchedulesSync(int characterId);

    @Query("DELETE FROM schedule_entries WHERE characterId = :characterId")
    void deleteByCharacterId(int characterId);
}
