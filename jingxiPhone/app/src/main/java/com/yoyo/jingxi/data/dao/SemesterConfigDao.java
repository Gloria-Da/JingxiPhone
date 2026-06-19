package com.yoyo.jingxi.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.SemesterConfig;

import java.util.List;

@Dao
public interface SemesterConfigDao {
    @Query("SELECT * FROM semester_configs ORDER BY startDate DESC")
    List<SemesterConfig> getAll();

    @Query("SELECT * FROM semester_configs WHERE isActive = 1 LIMIT 1")
    SemesterConfig getActive();

    @Query("SELECT * FROM semester_configs WHERE id = :id")
    SemesterConfig getById(int id);

    @Insert
    long insert(SemesterConfig config);

    @Update
    void update(SemesterConfig config);

    @Delete
    void delete(SemesterConfig config);

    @Query("UPDATE semester_configs SET isActive = 0")
    void deactivateAll();

    @Query("UPDATE semester_configs SET isActive = 1 WHERE id = :id")
    void setActive(int id);
}
