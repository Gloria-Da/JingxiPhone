package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "moments")
public class Moment {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    // 0 = MyPersona (用户), 1 = Character (AI角色), 2 = 虚拟人物(纯文本)
    public int publisherType;
    public String publisherId;     // 根据 publisherType 存储对应的 ID (比如 MyPersona.name, Character.id, 或虚拟人物的纯文本名称)
    public String publisherName;
    public String publisherAvatar;
    
    public String content;         // 帖子正文
    public String imageUrl;        // 如果有配图（可以是用逗号分隔的多张图片，为简单起见暂时设计为一个String，也可以按需扩充）
    public long timestamp;         // 发布时间
    
    // 以下为可能的联动字段
    public String associatedScheduleId; // 如果这条动态是由于某个日程触发的，可以记录下来
    public String associatedMemoryId;   // 如果是基于某条记忆触发的
}
