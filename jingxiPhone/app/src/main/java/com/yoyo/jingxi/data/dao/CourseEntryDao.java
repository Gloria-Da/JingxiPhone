package com.yoyo.jingxi.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.CourseEntry;

import java.util.List;

@Dao
public interface CourseEntryDao {
    @Query("SELECT * FROM course_entries WHERE semesterId = :semesterId AND dayOfWeek = :dayOfWeek ORDER BY startPeriod ASC")
    List<CourseEntry> getByDayOfWeek(int semesterId, int dayOfWeek);

    @Query("SELECT * FROM course_entries WHERE semesterId = :semesterId ORDER BY dayOfWeek ASC, startPeriod ASC")
    List<CourseEntry> getAllBySemester(int semesterId);

    @Query("SELECT * FROM course_entries WHERE id = :id")
    CourseEntry getById(int id);

    @Insert
    long insert(CourseEntry entry);

    @Update
    void update(CourseEntry entry);

    @Delete
    void delete(CourseEntry entry);

    @Query("DELETE FROM course_entries WHERE semesterId = :semesterId")
    void deleteBySemester(int semesterId);
}
