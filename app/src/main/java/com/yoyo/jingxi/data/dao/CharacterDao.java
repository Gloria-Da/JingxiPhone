package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.Character;

import java.util.List;

@Dao
public interface CharacterDao {
    @Insert
    long insert(Character character);

    @Update
    void update(Character character);

    @Delete
    void delete(Character character);

    @Query("SELECT * FROM characters")
    LiveData<List<Character>> getAllCharacters();

    @Query("SELECT * FROM characters")
    List<Character> getAllCharactersSync();

    @Query("SELECT * FROM characters WHERE id = :id")
    Character getCharacterById(int id);
    
    @Query("SELECT * FROM characters WHERE id = :id")
    Character getCharacterByIdSync(int id);
}