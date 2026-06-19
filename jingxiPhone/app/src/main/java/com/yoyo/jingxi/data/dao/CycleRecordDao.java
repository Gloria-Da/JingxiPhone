package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.CycleRecord;

import java.util.List;

@Dao
public interface CycleRecordDao {
    @Query("SELECT * FROM cycle_records ORDER BY startDate DESC")
    LiveData<List<CycleRecord>> getAllRecords();

    @Query("SELECT * FROM cycle_records ORDER BY startDate DESC")
    List<CycleRecord> getAllRecordsSync();

    @Query("SELECT * FROM cycle_records WHERE startDate <= :date AND endDate >= :date LIMIT 1")
    CycleRecord getPeriodOnDate(String date);

    @Query("SELECT * FROM cycle_records WHERE startDate = :date LIMIT 1")
    CycleRecord getRecordByStartDate(String date);

    @Query("SELECT * FROM cycle_records ORDER BY startDate DESC LIMIT 12")
    List<CycleRecord> getRecentRecords();

    @Query("SELECT * FROM cycle_records WHERE id = :id")
    CycleRecord getRecordById(int id);

    /** 取最近一条未结束的经期（endDate等于startDate），用于「经期结束了」快速标记 */
    @Query("SELECT * FROM cycle_records WHERE endDate = startDate ORDER BY startDate DESC LIMIT 1")
    CycleRecord getOpenEndedRecord();

    @Insert
    long insert(CycleRecord record);

    @Update
    void update(CycleRecord record);

    @Delete
    void delete(CycleRecord record);
}
