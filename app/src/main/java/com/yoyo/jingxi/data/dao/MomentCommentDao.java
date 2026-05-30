package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.yoyo.jingxi.data.entity.MomentComment;

import java.util.List;

@Dao
public interface MomentCommentDao {
    @Insert
    long insert(MomentComment comment);

    @Delete
    void delete(MomentComment comment);

    @Query("SELECT * FROM moment_comments WHERE momentId = :momentId ORDER BY timestamp ASC")
    LiveData<List<MomentComment>> getCommentsForMoment(int momentId);

    @Query("SELECT * FROM moment_comments WHERE momentId = :momentId ORDER BY timestamp ASC")
    List<MomentComment> getCommentsForMomentSync(int momentId);
}
