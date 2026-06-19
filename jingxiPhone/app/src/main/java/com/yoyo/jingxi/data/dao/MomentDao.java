package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.Moment;

import java.util.List;

@Dao
public interface MomentDao {
    @Insert
    long insert(Moment moment);

    @Update
    void update(Moment moment);

    @Delete
    void delete(Moment moment);

    @Query("SELECT * FROM moments ORDER BY timestamp DESC LIMIT 500")
    LiveData<List<Moment>> getAllMoments();

    @Query("SELECT * FROM moments ORDER BY timestamp DESC LIMIT :limit")
    List<Moment> getRecentMomentsSync(int limit);

    @Query("SELECT * FROM moments ORDER BY timestamp DESC LIMIT 500")
    LiveData<List<Moment>> getVisibleMoments();

    @Query("SELECT * FROM moments WHERE timestamp <= :currentTimeMillis ORDER BY timestamp DESC LIMIT 500")
    List<Moment> getVisibleMomentsSync(long currentTimeMillis);
    
    @Query("SELECT * FROM moments WHERE id = :id LIMIT 1")
    Moment getMomentById(int id);

    @Query("SELECT * FROM moments WHERE id = :id LIMIT 1")
    Moment getMomentByIdSync(int id);

    @Query("SELECT * FROM moments WHERE publisherType = 1 AND publisherId = :publisherId LIMIT 500")
    List<Moment> getMomentsByCharacterPublisher(String publisherId);

    @Query("DELETE FROM moments WHERE publisherType = 1 AND publisherId = :publisherId")
    void deleteByCharacterPublisher(String publisherId);
}
