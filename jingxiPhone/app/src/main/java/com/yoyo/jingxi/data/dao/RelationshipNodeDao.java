package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.yoyo.jingxi.data.entity.RelationshipEdge;
import com.yoyo.jingxi.data.entity.RelationshipNode;

import java.util.List;

@Dao
public interface RelationshipNodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RelationshipNode node);

    @Update
    void update(RelationshipNode node);

    @Delete
    void delete(RelationshipNode node);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertEdge(RelationshipEdge edge);

    @Query("SELECT * FROM relationship_nodes")
    LiveData<List<RelationshipNode>> getAllNodes();

    @Query("SELECT * FROM relationship_nodes WHERE id = :id")
    RelationshipNode getNodeById(String id);
    
    @Query("DELETE FROM relationship_nodes WHERE id = :id")
    void deleteNodeById(String id);
    
    @Query("SELECT * FROM relationship_nodes WHERE type = :type AND referenceId = :referenceId LIMIT 1")
    RelationshipNode getNodeByReference(int type, String referenceId);

    @Query("SELECT * FROM relationship_nodes WHERE name = :name LIMIT 1")
    RelationshipNode getNodeByName(String name);

    @Query("SELECT * FROM relationship_nodes")
    List<RelationshipNode> getAllNodesSync();

    @Query("DELETE FROM relationship_nodes")
    void deleteAll();
}
