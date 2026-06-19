package com.yoyo.jingxi.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "inner_voices",
        indices = {@Index("messageId"), @Index("sessionId"), @Index("characterId")})
public class InnerVoice {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public int messageId;      // 关联的消息 ID（一对一）
    public int sessionId;      // 冗余，方便按会话查询
    public int characterId;    // 冗余，方便按角色查询
    public String content;     // 心声文本内容
    public String emotion;     // 情绪标签（如 "纠结"、"暗喜"、"不安"），可选
    public long timestamp;     // 创建时间
    @ColumnInfo(defaultValue = "0")
    public boolean isRead;     // 用户是否已查看，默认 false
}
