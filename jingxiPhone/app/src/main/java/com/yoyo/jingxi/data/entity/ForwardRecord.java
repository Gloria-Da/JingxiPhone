package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 记录聊天消息的转发操作。
 * 用于追溯转发来源、支持合并转发批次等。
 */
@Entity(tableName = "forward_records",
        foreignKeys = @ForeignKey(
                entity = Message.class,
                parentColumns = "id",
                childColumns = "sourceMessageId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("sourceMessageId"))
public class ForwardRecord {
    @PrimaryKey(autoGenerate = true)
    public int id;

    /** 原始消息ID（被转发的消息） */
    public int sourceMessageId;

    /** 目标会话ID */
    public int targetSessionId;

    /** 目标角色ID */
    public int targetCharacterId;

    /** 转发时间戳 */
    public long forwardTimestamp;

    /** 在目标会话中新建的消息ID */
    public int forwardedMessageId;

    /**
     * 转发批次ID（UUID）。
     * 同一次合并转发中的所有消息共享同一个batchId，
     * 用于后续展示"此消息来自合并转发"等。
     */
    public String forwardBatchId;
}
