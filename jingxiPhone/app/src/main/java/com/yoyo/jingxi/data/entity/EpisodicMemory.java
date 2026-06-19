package com.yoyo.jingxi.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "episodic_memory",
    indices = {
        @Index(value = "characterId", name = "idx_episodic_characterId"),
        @Index(value = "importanceLevel", name = "idx_episodic_importance"),
        @Index(value = "isRecalled", name = "idx_episodic_recalled")
    })
public class EpisodicMemory {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int characterId;
    @ColumnInfo(defaultValue = "")
    public String myPersonaName;  // 关联的用户人设名
    public String episodeDate;
    public String title;
    public String keywords;
    public String subjectiveDiary;
    public String emotionalTone;
    @ColumnInfo(defaultValue = "1")
    public int importanceLevel;
    public String participants;
    public long createdAt;
    @ColumnInfo(defaultValue = "0")
    public boolean isRecalled;
    @ColumnInfo(defaultValue = "0")
    public long lastRecalledAt;
    @ColumnInfo(defaultValue = "0")
    public int recallCount;

    public EpisodicMemory() {
        this.importanceLevel = 1;
        this.emotionalTone = "平静";
        this.isRecalled = false;
        this.lastRecalledAt = 0;
        this.recallCount = 0;
        this.createdAt = System.currentTimeMillis();
    }
}
