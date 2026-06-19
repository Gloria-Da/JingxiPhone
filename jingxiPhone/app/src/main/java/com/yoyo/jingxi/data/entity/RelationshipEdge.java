package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "relationship_edges")
public class RelationshipEdge {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String sourceNodeId;
    public String targetNodeId;
    public String relation; // 关系描述
    public int intimacy = 50; // 亲密度，0-100
    public double interactionProbability = 0.5; // 互动概率，0.0-1.0
}
