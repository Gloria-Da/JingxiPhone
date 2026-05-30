package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "moment_comments")
public class MomentComment {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public int momentId;           // 关联的动态ID
    
    // 评论者信息
    public int authorType;         // 0 = MyPersona, 1 = Character, 2 = 虚拟人物
    public String authorId;
    public String authorName;
    
    // 回复对象信息（可选，如果为空表示直接评论帖子）
    public int replyToType;
    public String replyToId;
    public String replyToName;
    
    public String content;
    public long timestamp;
}
