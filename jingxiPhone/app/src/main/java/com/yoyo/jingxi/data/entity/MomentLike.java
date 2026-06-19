package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "moment_likes")
public class MomentLike {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public int momentId;           // 关联的动态ID
    
    // 点赞者信息
    public int likerType;          // 0 = MyPersona, 1 = Character, 2 = 虚拟人物
    public String likerId;
    public String likerName;
    
    public long timestamp;
}
