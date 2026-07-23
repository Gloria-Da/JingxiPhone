package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 存储从外部App分享到镜隙的内容元数据（如B站/小红书/抖音链接）。
 * 用于在聊天中展示富媒体链接卡片，以及让AI角色"看到"分享的内容。
 */
@Entity(tableName = "shared_contents")
public class SharedContent {
    @PrimaryKey(autoGenerate = true)
    public int id;

    /** 原始分享URL */
    public String sourceUrl;

    /** 来源站点名称，如"Bilibili"、"小红书" */
    public String siteName;

    /** 站点favicon地址 */
    public String faviconUrl;

    /** 提取的页面标题（OG title或页面title） */
    public String contentTitle;

    /** 缩略图地址（OG image） */
    public String thumbnailUrl;

    /** 页面描述（OG description或meta description） */
    public String description;

    /** 分享时间戳 */
    public long timestamp;

    /** 关联的会话ID（0表示未分配） */
    public int sessionId;

    /** 关联的角色ID（0表示未分配） */
    public int characterId;

    /** 关联的聊天消息ID（用于AI上下文中查找分享内容） */
    public int messageId;

    /** JSON 数组格式的图片 URL 列表（小红书等图文内容） */
    public String imageUrlsJson;

    /** 完整正文内容（小红书图文笔记等，不做截断） */
    public String fullText;
}
