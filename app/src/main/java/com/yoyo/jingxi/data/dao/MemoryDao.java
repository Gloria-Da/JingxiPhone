package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.Memory;

import java.util.List;

@Dao
public interface MemoryDao {
    @Insert
    long insert(Memory memory);

    @Update
    void update(Memory memory);

    @Delete
    void delete(Memory memory);

    @Query("SELECT * FROM memories WHERE characterId = :characterId ORDER BY timestamp DESC")
    LiveData<List<Memory>> getMemoriesByCharacterId(int characterId);

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND type = 1 ORDER BY category ASC, starLevel DESC, timestamp DESC")
    List<Memory> getImportantMemoriesSync(int characterId);

    @Query("SELECT * FROM memories WHERE id = :id")
    Memory getMemoryByIdSync(int id);

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND type = 0 ORDER BY timestamp DESC LIMIT :limit")
    List<Memory> getNormalMemoriesSync(int characterId, int limit);
    
    @Query("SELECT * FROM memories WHERE characterId = :characterId AND type = 0 ORDER BY timestamp DESC")
    List<Memory> getAllNormalMemoriesSync(int characterId);

    @Query("SELECT * FROM memories WHERE characterId = :characterId ORDER BY timestamp DESC")
    List<Memory> getMemoriesByCharacterIdSync(int characterId);
}
