package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.EpisodicMemory;

import java.util.List;

@Dao
public interface EpisodicMemoryDao {
    @Insert
    long insert(EpisodicMemory memory);

    @Update
    void update(EpisodicMemory memory);

    @Delete
    void delete(EpisodicMemory memory);

    // === 带人设过滤的查询（对话流程使用） ===

    @Query("SELECT * FROM episodic_memory WHERE characterId = :characterId AND myPersonaName = :myPersonaName ORDER BY createdAt DESC LIMIT 300")
    LiveData<List<EpisodicMemory>> getByCharacterId(int characterId, String myPersonaName);

    @Query("SELECT * FROM episodic_memory WHERE characterId = :characterId AND myPersonaName = :myPersonaName ORDER BY createdAt DESC LIMIT 300")
    List<EpisodicMemory> getByCharacterIdSync(int characterId, String myPersonaName);

    @Query("SELECT * FROM episodic_memory WHERE characterId = :characterId AND myPersonaName = :myPersonaName ORDER BY createdAt DESC LIMIT :limit")
    List<EpisodicMemory> getRecentSync(int characterId, String myPersonaName, int limit);

    @Query("SELECT * FROM episodic_memory WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND episodeDate = :date ORDER BY createdAt DESC LIMIT 200")
    List<EpisodicMemory> getByDateSync(int characterId, String myPersonaName, String date);

    @Query("SELECT * FROM episodic_memory WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND importanceLevel >= :minLevel ORDER BY importanceLevel DESC, createdAt DESC LIMIT 200")
    List<EpisodicMemory> getByImportanceSync(int characterId, String myPersonaName, int minLevel);

    @Query("SELECT * FROM episodic_memory WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND (keywords LIKE '%' || :keyword || '%' OR title LIKE '%' || :keyword || '%' OR subjectiveDiary LIKE '%' || :keyword || '%') LIMIT 200")
    List<EpisodicMemory> keywordSearch(int characterId, String myPersonaName, String keyword);

    @Query("SELECT * FROM episodic_memory WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND importanceLevel >= 3 AND isRecalled = 0 ORDER BY lastRecalledAt ASC LIMIT :limit")
    List<EpisodicMemory> getRecallCandidates(int characterId, String myPersonaName, int limit);

    @Query("SELECT * FROM episodic_memory WHERE characterId = :characterId AND myPersonaName = :myPersonaName ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    List<EpisodicMemory> getByCharacterIdPaged(int characterId, String myPersonaName, int limit, int offset);

    @Query("SELECT COUNT(*) FROM episodic_memory WHERE characterId = :characterId AND myPersonaName = :myPersonaName")
    int getCount(int characterId, String myPersonaName);

    @Query("SELECT * FROM episodic_memory WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND title = :title LIMIT 1")
    EpisodicMemory findByCharacterIdAndTitle(int characterId, String myPersonaName, String title);

    // === 不带人设过滤的查询（MemoryActivity「全部人设」模式使用） ===

    @Query("SELECT * FROM episodic_memory WHERE characterId = :characterId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    List<EpisodicMemory> getByCharacterIdPagedAll(int characterId, int limit, int offset);

    @Query("SELECT COUNT(*) FROM episodic_memory WHERE characterId = :characterId")
    int getCountAll(int characterId);

    // === 通用操作 ===

    @Query("SELECT * FROM episodic_memory WHERE id = :id")
    EpisodicMemory getByIdSync(int id);

    @Query("UPDATE episodic_memory SET isRecalled = 1, lastRecalledAt = :timestamp, recallCount = recallCount + 1 WHERE id = :id")
    void markRecalled(int id, long timestamp);
}
