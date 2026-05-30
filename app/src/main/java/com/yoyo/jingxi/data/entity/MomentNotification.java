package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "moment_notifications")
public class MomentNotification {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public int momentId;           // 关联的动态ID，可能为 -1（如果仅是@但没有具体动态，或者还没生成好）
    public int type;               // 0: 点赞, 1: 评论, 2: 提及(@)
    
    // 发起互动者的信息（谁点了赞，谁评了论，谁@了你）
    public int triggerType;        // 0: MyPersona, 1: Character, 2: 虚拟人物
    public String triggerId;
    public String triggerName;
    public String triggerAvatar;
    
    // 通知接收者的信息（发给谁的通知）
    public int receiverType;       // 0: MyPersona, 1: Character
    public String receiverId;
    
    // 附带信息（如评论内容）
    public String content;
    
    // 通知时间
    public long timestamp;
    
    // 是否已读
    public boolean isRead;
}
