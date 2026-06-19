package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "call_messages")
public class CallMessage {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int callId; // 关联的 CallRecord 的 id
    public boolean isFromUser; // 是否为用户发送
    public String content; // 消息内容（转写或打字内容，AI则是生成的回复）
    public String voiceUrl; // 本地保存的语音地址
    public long timestamp; // 时间戳
}