package com.yoyo.jingxi.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.yoyo.jingxi.data.entity.HolidayCache;

import java.util.List;

@Dao
public interface HolidayCacheDao {
    @Query("SELECT * FROM holiday_cache WHERE date >= :startDate AND date <= :endDate")
    List<HolidayCache> getHolidaysInRange(String startDate, String endDate);

    @Query("SELECT * FROM holiday_cache WHERE date = :date LIMIT 1")
    HolidayCache getHolidayByDate(String date);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<HolidayCache> holidays);

    @Query("DELETE FROM holiday_cache WHERE date < :beforeDate")
    void deleteOlderThan(String beforeDate);

    @Query("SELECT COUNT(*) FROM holiday_cache WHERE date >= :startDate AND date <= :endDate")
    int countInRange(String startDate, String endDate);
}
