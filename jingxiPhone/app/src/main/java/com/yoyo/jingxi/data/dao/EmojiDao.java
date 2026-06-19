package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.yoyo.jingxi.data.entity.EmojiEntry;

import java.util.List;

@Dao
public interface EmojiDao {
    @Insert
    void insert(EmojiEntry emojiEntry);

    @Delete
    void delete(EmojiEntry emojiEntry);

    @Query("SELECT * FROM emoji_entries")
    LiveData<List<EmojiEntry>> getAllEmojis();

    @Query("SELECT * FROM emoji_entries")
    List<EmojiEntry> getAllEmojisSync();

    @Query("DELETE FROM emoji_entries WHERE groupName = :groupName")
    void deleteByGroup(String groupName);

    @Query("SELECT DISTINCT groupName FROM emoji_entries WHERE groupName IS NOT NULL AND groupName != ''")
    List<String> getAllGroupNamesSync();

    @Query("SELECT DISTINCT groupName FROM emoji_entries WHERE groupName IS NOT NULL AND groupName != ''")
    List<String> getAllGroupsSync();

    @Query("SELECT * FROM emoji_entries WHERE groupName = :groupName")
    List<EmojiEntry> getEmojisByGroupSync(String groupName);

    @Query("SELECT * FROM emoji_entries WHERE groupName = :groupName")
    LiveData<List<EmojiEntry>> getEmojisByGroup(String groupName);

    @Query("SELECT * FROM emoji_entries WHERE name = :name")
    List<EmojiEntry> getEmojiByNameSync(String name);
}
