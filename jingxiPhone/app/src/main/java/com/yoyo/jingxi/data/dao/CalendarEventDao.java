package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.CalendarEvent;

import java.util.List;

@Dao
public interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE eventDate = :date ORDER BY startTime ASC")
    List<CalendarEvent> getEventsByDate(String date);

    @Query("SELECT * FROM calendar_events WHERE eventDate >= :startDate AND eventDate <= :endDate ORDER BY eventDate ASC, startTime ASC")
    LiveData<List<CalendarEvent>> getEventsInRange(String startDate, String endDate);

    @Query("SELECT * FROM calendar_events WHERE eventDate >= :startDate AND eventDate <= :endDate ORDER BY eventDate ASC, startTime ASC")
    List<CalendarEvent> getEventsInRangeSync(String startDate, String endDate);

    @Query("SELECT * FROM calendar_events WHERE recurrence != 'NONE'")
    List<CalendarEvent> getRecurringEvents();

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    CalendarEvent getEventById(int id);

    @Insert
    long insert(CalendarEvent event);

    @Update
    void update(CalendarEvent event);

    @Delete
    void delete(CalendarEvent event);
}
