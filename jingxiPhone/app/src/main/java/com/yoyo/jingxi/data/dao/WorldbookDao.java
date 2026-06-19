package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.WorldbookEntry;

import java.util.List;

@Dao
public interface WorldbookDao {
    @Query("SELECT * FROM worldbook_entries ORDER BY type ASC, id DESC")
    LiveData<List<WorldbookEntry>> getAllEntries();

    @Query("SELECT * FROM worldbook_entries WHERE isEnabled = 1")
    List<WorldbookEntry> getAllEnabledEntriesSync();

    @Insert
    void insert(WorldbookEntry entry);

    @Update
    void update(WorldbookEntry entry);

    @Delete
    void delete(WorldbookEntry entry);
}
