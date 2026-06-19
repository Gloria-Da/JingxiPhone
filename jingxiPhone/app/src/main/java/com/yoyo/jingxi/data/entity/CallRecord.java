package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "call_records")
public class CallRecord {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int sessionId; // 关联的聊天会话
    public int characterId; // 关联的角色
    public long startTime; // 开始时间
    public long endTime; // 结束时间
    public int duration; // 时长(秒)
    public String summary; // 电话总结
    public int initiator; // 0: 用户主动发起, 1: AI主动发起
    public boolean isMissed; // 是否未接通
}
