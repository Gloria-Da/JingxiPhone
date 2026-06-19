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

    // === 带人设过滤的查询（对话流程使用） ===

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND myPersonaName = :myPersonaName ORDER BY timestamp DESC LIMIT 500")
    LiveData<List<Memory>> getMemoriesByCharacterId(int characterId, String myPersonaName);

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND type = 1 ORDER BY category ASC, starLevel DESC, timestamp DESC LIMIT 200")
    List<Memory> getImportantMemoriesSync(int characterId, String myPersonaName);

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND type = 0 ORDER BY timestamp DESC LIMIT :limit")
    List<Memory> getNormalMemoriesSync(int characterId, String myPersonaName, int limit);

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND type = 0 ORDER BY timestamp DESC LIMIT 800")
    List<Memory> getAllNormalMemoriesSync(int characterId, String myPersonaName);

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND myPersonaName = :myPersonaName ORDER BY timestamp DESC LIMIT 800")
    List<Memory> getMemoriesByCharacterIdSync(int characterId, String myPersonaName);

    // === 不带人设过滤的查询（MemoryActivity / 后台 Worker 兼容） ===

    @Query("SELECT * FROM memories WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT 500")
    LiveData<List<Memory>> getMemoriesByCharacterIdAll(int characterId);

    @Query("SELECT * FROM memories WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT 800")
    List<Memory> getMemoriesByCharacterIdSyncAll(int characterId);

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND type = 1 ORDER BY category ASC, starLevel DESC, timestamp DESC LIMIT 200")
    List<Memory> getImportantMemoriesSyncAll(int characterId);

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND type = 0 ORDER BY timestamp DESC LIMIT :limit")
    List<Memory> getNormalMemoriesSyncAll(int characterId, int limit);

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND type = 0 ORDER BY timestamp DESC LIMIT 800")
    List<Memory> getAllNormalMemoriesSyncAll(int characterId);

    // === 通用操作 ===

    @Query("SELECT * FROM memories WHERE id = :id")
    Memory getMemoryByIdSync(int id);

    @Query("DELETE FROM memories WHERE characterId = :characterId")
    void deleteByCharacterId(int characterId);
}
