package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "characters")
public class Character {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public String nickname;
    public String persona; // 人设，包含年龄等信息
    public String avatarPath;
    public String chatBackgroundPath;
    public String voiceId; // MiniMax音色ID
    public boolean enableEmoji;
    public int voicePitch = 0;
    public int voiceIntensity = 0;
    public int voiceTimbre = 0;
    public String soundEffect;
    public float voiceSpeed = 1.0f;
    
    // 主动发动态配置
    @androidx.room.ColumnInfo(defaultValue = "8.0")
    public float autoMomentIntervalHours = 8.0f;

    @androidx.room.ColumnInfo(defaultValue = "08:00")
    public String autoMomentStartTime = "08:00";

    @androidx.room.ColumnInfo(defaultValue = "22:00")
    public String autoMomentEndTime = "22:00";

    @androidx.room.ColumnInfo(defaultValue = "0")
    public int autoMomentProbability = 0;
}
