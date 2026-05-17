package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class Message {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int sessionId;   // 关联的会话ID
    public int characterId; // 冗余保存或用于特定查询
    public String content;  // 消息内容
    public int type;        // 0: 文本, 1: 语音, 3: 真实图片, 4: 虚拟图片, 5: 未接来电, 6: 通话总结
    public boolean isFromUser; // true为用户发送，false为AI发送
    public long timestamp;  // 时间戳
    public String voiceUrl; // 语音地址
    public int quoteMessageId; // 引用的消息ID，-1表示未引用
    public String imageUrl; // 真实图片地址或Base64
    public String imageDesc; // 虚拟图片的描述
}
