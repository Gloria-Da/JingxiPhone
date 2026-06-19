package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.UserProfileNode;

import java.util.List;

@Dao
public interface UserProfileNodeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(UserProfileNode node);

    @Update
    void update(UserProfileNode node);

    @Delete
    void delete(UserProfileNode node);

    // === 带人设过滤的查询（对话流程使用） ===

    @Query("SELECT * FROM user_profile_nodes WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND isActive = 1 ORDER BY category ASC, lastUpdated DESC")
    LiveData<List<UserProfileNode>> getByCharacterId(int characterId, String myPersonaName);

    @Query("SELECT * FROM user_profile_nodes WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND isActive = 1 ORDER BY category ASC, lastUpdated DESC")
    List<UserProfileNode> getByCharacterIdSync(int characterId, String myPersonaName);

    @Query("SELECT * FROM user_profile_nodes WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND isActive = 1 AND category = :category ORDER BY lastUpdated DESC")
    List<UserProfileNode> getByCategorySync(int characterId, String myPersonaName, String category);

    @Query("SELECT * FROM user_profile_nodes WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND isActive = 1 AND (valueContent LIKE '%' || :keyword || '%' OR keyItem LIKE '%' || :keyword || '%')")
    List<UserProfileNode> keywordSearch(int characterId, String myPersonaName, String keyword);

    @Query("SELECT * FROM user_profile_nodes WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND isActive = 1")
    List<UserProfileNode> getAllActiveSync(int characterId, String myPersonaName);

    @Query("SELECT * FROM user_profile_nodes WHERE characterId = :characterId AND myPersonaName = :myPersonaName AND category = :category AND keyItem = :keyItem AND isActive = 1 LIMIT 1")
    UserProfileNode findByCharacterIdCategoryAndKeyItem(int characterId, String myPersonaName, String category, String keyItem);

    // === 不带人设过滤的查询（MemoryActivity「全部人设」模式使用） ===

    @Query("SELECT * FROM user_profile_nodes WHERE characterId = :characterId AND isActive = 1 ORDER BY category ASC, lastUpdated DESC")
    List<UserProfileNode> getAllActiveSyncByCharacter(int characterId);

    // === 通用操作 ===

    @Query("SELECT * FROM user_profile_nodes WHERE id = :id")
    UserProfileNode getByIdSync(int id);

    @Query("UPDATE user_profile_nodes SET isActive = 0 WHERE id = :id")
    void deactivate(int id);
}
