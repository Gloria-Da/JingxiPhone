package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.RelationshipEdge;

import java.util.List;

@Dao
public interface RelationshipEdgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RelationshipEdge edge);

    @Update
    void update(RelationshipEdge edge);

    @Delete
    void delete(RelationshipEdge edge);

    @Query("SELECT * FROM relationship_edges")
    LiveData<List<RelationshipEdge>> getAllEdges();

    @Query("SELECT * FROM relationship_edges")
    List<RelationshipEdge> getAllEdgesSync();

    @Query("SELECT * FROM relationship_edges WHERE sourceNodeId = :nodeId OR targetNodeId = :nodeId")
    List<RelationshipEdge> getEdgesForNode(String nodeId);

    @Query("DELETE FROM relationship_edges")
    void deleteAll();
    
    @Query("DELETE FROM relationship_edges WHERE sourceNodeId = :nodeId OR targetNodeId = :nodeId")
    void deleteEdgesByNodeId(String nodeId);
}
