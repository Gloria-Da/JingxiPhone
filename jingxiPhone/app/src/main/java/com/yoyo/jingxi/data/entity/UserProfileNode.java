package com.yoyo.jingxi.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_profile_nodes",
    indices = {@Index(value = {"characterId", "myPersonaName", "category", "keyItem"}, unique = true, name = "index_user_profile_nodes_unique")})
public class UserProfileNode {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int characterId;
    @ColumnInfo(defaultValue = "")
    public String myPersonaName;  // 关联的用户人设名
    public String category;       // 大分类: 档案/作息/饮食/防线/人际/目标
    public String keyItem;        // 子项键: sleep, comfort_food, phobia
    public String valueContent;   // 客观事实内容
    @ColumnInfo(defaultValue = "普通")
    public String emotionTag;     // 情感滤镜: 宠溺/警惕/心疼/无奈/关切/普通
    @ColumnInfo(defaultValue = "5")
    public int confidence;        // 确信度 1-10
    @ColumnInfo(defaultValue = "1")
    public boolean isActive;
    @ColumnInfo(defaultValue = "0")
    public boolean isCustom;      // 是否为预设列表外的自定义项
    public long lastUpdated;

    public UserProfileNode() {
        this.isActive = true;
        this.confidence = 5;
        this.emotionTag = "普通";
        this.lastUpdated = System.currentTimeMillis();
    }
}
