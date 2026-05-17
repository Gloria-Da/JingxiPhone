package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.yoyo.jingxi.data.entity.MomentLike;

import java.util.List;

@Dao
public interface MomentLikeDao {
    @Insert
    long insert(MomentLike like);

    @Delete
    void delete(MomentLike like);

    @Query("SELECT * FROM moment_likes WHERE momentId = :momentId ORDER BY timestamp ASC")
    LiveData<List<MomentLike>> getLikesForMoment(int momentId);

    @Query("SELECT * FROM moment_likes WHERE momentId = :momentId ORDER BY timestamp ASC")
    List<MomentLike> getLikesForMomentSync(int momentId);

    @Query("SELECT * FROM moment_likes WHERE momentId = :momentId AND likerId = :likerId LIMIT 1")
    MomentLike getLikeByLiker(int momentId, String likerId);
}
