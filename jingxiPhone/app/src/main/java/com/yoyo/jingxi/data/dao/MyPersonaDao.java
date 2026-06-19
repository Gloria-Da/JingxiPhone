package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.MyPersona;

import java.util.List;

@Dao
public interface MyPersonaDao {
    @Insert
    void insert(MyPersona myPersona);

    @Update
    void update(MyPersona myPersona);

    @Delete
    void delete(MyPersona myPersona);

    @Query("SELECT * FROM my_personas")
    LiveData<List<MyPersona>> getAllMyPersonas();

    @Query("SELECT * FROM my_personas")
    List<MyPersona> getAllPersonasSync();

    @Query("SELECT * FROM my_personas WHERE name = :name LIMIT 1")
    MyPersona getMyPersonaByName(String name);

    @Query("SELECT * FROM my_personas WHERE isMainPersona = 1 LIMIT 1")
    MyPersona getMainPersona();

    @Query("UPDATE my_personas SET isMainPersona = 0")
    void clearAllMainStatus();
}