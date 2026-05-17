package com.yoyo.jingxi.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "relationship_nodes")
public class RelationshipNode {
    @PrimaryKey
    @NonNull
    public String id = ""; // UUID

    public String name;
    
    // 0 = 已有AI角色 (Character), 1 = 用户人设 (MyPersona), 2 = 纯虚拟背景人物 (Virtual)
    public int type;
    
    // 关联的主键 (Character 的 id 或 MyPersona 的 name)
    public String referenceId;
    
    public String description;
    
    public String avatarPath;
}
