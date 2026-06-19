package com.yoyo.jingxi.data;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.yoyo.jingxi.data.dao.CharacterDao;
import com.yoyo.jingxi.data.dao.MessageDao;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.Message;

import com.yoyo.jingxi.data.dao.ChatSessionDao;
import com.yoyo.jingxi.data.dao.MyPersonaDao;
import com.yoyo.jingxi.data.dao.SessionWithLastMessageDao;
import com.yoyo.jingxi.data.dao.MemoryDao;
import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.MyPersona;
import com.yoyo.jingxi.data.entity.Memory;
import com.yoyo.jingxi.data.dao.WorldbookDao;
import com.yoyo.jingxi.data.entity.WorldbookEntry;
import com.yoyo.jingxi.data.dao.MemoDao;
import com.yoyo.jingxi.data.entity.Memo;
import com.yoyo.jingxi.data.dao.ScheduleDao;
import com.yoyo.jingxi.data.entity.ScheduleEntry;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.Executors;

import com.yoyo.jingxi.utils.SpUtils;

import com.yoyo.jingxi.data.dao.EmojiDao;
import com.yoyo.jingxi.data.entity.EmojiEntry;
import com.yoyo.jingxi.data.dao.CallRecordDao;
import com.yoyo.jingxi.data.entity.CallRecord;
import com.yoyo.jingxi.data.dao.CallMessageDao;
import com.yoyo.jingxi.data.entity.CallMessage;
import com.yoyo.jingxi.data.dao.RelationshipNodeDao;
import com.yoyo.jingxi.data.dao.RelationshipEdgeDao;
import com.yoyo.jingxi.data.entity.RelationshipNode;
import com.yoyo.jingxi.data.entity.RelationshipEdge;
import com.yoyo.jingxi.data.entity.Moment;
import com.yoyo.jingxi.data.entity.MomentComment;
import com.yoyo.jingxi.data.entity.MomentLike;
import com.yoyo.jingxi.data.dao.MomentDao;
import com.yoyo.jingxi.data.dao.MomentCommentDao;
import com.yoyo.jingxi.data.dao.MomentLikeDao;
import com.yoyo.jingxi.data.dao.MomentNotificationDao;
import com.yoyo.jingxi.data.entity.MomentNotification;
import com.yoyo.jingxi.data.dao.UserProfileNodeDao;
import com.yoyo.jingxi.data.entity.UserProfileNode;
import com.yoyo.jingxi.data.dao.EpisodicMemoryDao;
import com.yoyo.jingxi.data.entity.EpisodicMemory;
import com.yoyo.jingxi.data.dao.InnerVoiceDao;
import com.yoyo.jingxi.data.entity.InnerVoice;
import com.yoyo.jingxi.data.dao.CalendarEventDao;
import com.yoyo.jingxi.data.entity.CalendarEvent;
import com.yoyo.jingxi.data.dao.CycleRecordDao;
import com.yoyo.jingxi.data.entity.CycleRecord;
import com.yoyo.jingxi.data.dao.HolidayCacheDao;
import com.yoyo.jingxi.data.entity.HolidayCache;
import com.yoyo.jingxi.data.dao.SemesterConfigDao;
import com.yoyo.jingxi.data.entity.SemesterConfig;
import com.yoyo.jingxi.data.dao.CourseEntryDao;
import com.yoyo.jingxi.data.entity.CourseEntry;

@Database(entities = {Character.class, Message.class, MyPersona.class, ChatSession.class, Memory.class, WorldbookEntry.class, Memo.class, ScheduleEntry.class, EmojiEntry.class, CallRecord.class, CallMessage.class, RelationshipNode.class, RelationshipEdge.class, Moment.class, MomentComment.class, MomentLike.class, MomentNotification.class, UserProfileNode.class, EpisodicMemory.class, InnerVoice.class, CalendarEvent.class, CycleRecord.class, HolidayCache.class, SemesterConfig.class, CourseEntry.class}, version = 49, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    /** Current database version. Must match the version in @Database annotation. */
    public static final int DB_VERSION = 49;
    /** Minimum database version that has a valid Room migration path. */
    public static final int MIN_DB_VERSION = 3;

    private static volatile AppDatabase INSTANCE;

    public abstract CharacterDao characterDao();
    public abstract MessageDao messageDao();
    public abstract MyPersonaDao myPersonaDao();
    public abstract ChatSessionDao chatSessionDao();
    public abstract SessionWithLastMessageDao sessionWithLastMessageDao();
    public abstract MemoryDao memoryDao();
    public abstract WorldbookDao worldbookDao();
    public abstract MemoDao memoDao();
    public abstract ScheduleDao scheduleDao();
    public abstract EmojiDao emojiDao();
    public abstract CallRecordDao callRecordDao();
    public abstract CallMessageDao callMessageDao();
    public abstract RelationshipNodeDao relationshipNodeDao();
    public abstract RelationshipEdgeDao relationshipEdgeDao();
    public abstract MomentDao momentDao();
    public abstract MomentCommentDao momentCommentDao();
    public abstract MomentLikeDao momentLikeDao();
    public abstract MomentNotificationDao momentNotificationDao();
    public abstract UserProfileNodeDao userProfileNodeDao();
    public abstract EpisodicMemoryDao episodicMemoryDao();
    public abstract InnerVoiceDao innerVoiceDao();
    public abstract CalendarEventDao calendarEventDao();
    public abstract CycleRecordDao cycleRecordDao();
    public abstract HolidayCacheDao holidayCacheDao();
    public abstract SemesterConfigDao semesterConfigDao();
    public abstract CourseEntryDao courseEntryDao();

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add columns to messages table
            database.execSQL("ALTER TABLE messages ADD COLUMN imageUrl TEXT");
            database.execSQL("ALTER TABLE messages ADD COLUMN imageDesc TEXT");
            // Create memories table
            database.execSQL("CREATE TABLE IF NOT EXISTS `memories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `characterId` INTEGER NOT NULL, `type` INTEGER NOT NULL, `content` TEXT, `starLevel` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `worldbook_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `keyword` TEXT, `content` TEXT, `isEnabled` INTEGER NOT NULL)");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE worldbook_entries ADD COLUMN title TEXT");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `memos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `characterId` INTEGER NOT NULL, `content` TEXT, `targetDate` TEXT, `status` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `schedule_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `characterId` INTEGER NOT NULL, `date` TEXT, `contentJson` TEXT, `timestamp` INTEGER NOT NULL)");
        }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE my_personas ADD COLUMN avatarPath TEXT");
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
        // Memos migration handled by destructive migration since it crashed on a specific column
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE characters ADD COLUMN voiceId TEXT");
        }
    };

    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `emoji_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `imageUrl` TEXT)");
        }
    };

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE emoji_entries ADD COLUMN groupName TEXT");
        }
    };

    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE characters ADD COLUMN enableEmoji INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `call_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `characterId` INTEGER NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `summary` TEXT, `initiator` INTEGER NOT NULL, `isMissed` INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `call_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `callId` INTEGER NOT NULL, `isFromUser` INTEGER NOT NULL, `content` TEXT, `voiceUrl` TEXT, `timestamp` INTEGER NOT NULL)");
        }
    };

    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE characters ADD COLUMN voicePitch INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE characters ADD COLUMN voiceIntensity INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE characters ADD COLUMN voiceTimbre INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE characters ADD COLUMN soundEffect TEXT");
        }
    };

    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE characters ADD COLUMN voiceSpeed REAL NOT NULL DEFAULT 1.0");
        }
    };

    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Remove voiceSpeed column by fallback to destructive migration or creating new table
            // SQLite does not support drop column before 3.35, and Android's SQLite version varies.
            // Since fallbackToDestructiveMigration() is enabled, we can let it destroy and recreate if needed,
            // or just leave the column unused. Here we do nothing and let the code handle the missing field.
        }
    };

    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `relationship_nodes` (`id` TEXT NOT NULL, `name` TEXT, `type` INTEGER NOT NULL, `referenceId` TEXT, `description` TEXT, `avatarPath` TEXT, PRIMARY KEY(`id`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `relationship_edges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceNodeId` TEXT, `targetNodeId` TEXT, `relation` TEXT)");
        }
    };

    static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `moments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `publisherType` INTEGER NOT NULL, `publisherId` TEXT, `publisherName` TEXT, `publisherAvatar` TEXT, `content` TEXT, `imageUrl` TEXT, `timestamp` INTEGER NOT NULL, `associatedScheduleId` TEXT, `associatedMemoryId` TEXT)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `moment_comments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `momentId` INTEGER NOT NULL, `authorType` INTEGER NOT NULL, `authorId` TEXT, `authorName` TEXT, `replyToType` INTEGER NOT NULL, `replyToId` TEXT, `replyToName` TEXT, `content` TEXT, `timestamp` INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `moment_likes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `momentId` INTEGER NOT NULL, `likerType` INTEGER NOT NULL, `likerId` TEXT, `likerName` TEXT, `timestamp` INTEGER NOT NULL)");
        }
    };

    static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Rename relationship_edges fields from sourceId/targetId to sourceNodeId/targetNodeId to match entity
            database.execSQL("CREATE TABLE IF NOT EXISTS `relationship_edges_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceNodeId` TEXT, `targetNodeId` TEXT, `relation` TEXT)");
            try {
                // If old table exists with sourceId/targetId, copy data
                database.execSQL("INSERT INTO `relationship_edges_new` (`id`, `sourceNodeId`, `targetNodeId`, `relation`) SELECT `id`, `sourceId`, `targetId`, `relation` FROM `relationship_edges`");
                database.execSQL("DROP TABLE `relationship_edges`");
            } catch (Exception e) {
                // Ignore if it was already migrated or old table doesn't have those columns
                database.execSQL("DROP TABLE IF EXISTS `relationship_edges`");
            }
            database.execSQL("ALTER TABLE `relationship_edges_new` RENAME TO `relationship_edges`");
        }
    };

    static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Recreate memos table with correct schema
            database.execSQL("CREATE TABLE IF NOT EXISTS `memos_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `characterId` INTEGER NOT NULL, `content` TEXT, `targetDate` TEXT, `status` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)");
            try {
                database.execSQL("DROP TABLE IF EXISTS `memos`");
            } catch (Exception e) {
                // Ignore
            }
            database.execSQL("ALTER TABLE `memos_new` RENAME TO `memos`");
        }
    };

    static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE relationship_edges ADD COLUMN intimacy INTEGER NOT NULL DEFAULT 50");
            database.execSQL("ALTER TABLE relationship_edges ADD COLUMN interactionProbability REAL NOT NULL DEFAULT 0.5");
        }
    };

    static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `moment_notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `momentId` INTEGER NOT NULL, `type` INTEGER NOT NULL, `triggerType` INTEGER NOT NULL, `triggerId` TEXT, `triggerName` TEXT, `triggerAvatar` TEXT, `receiverType` INTEGER NOT NULL, `receiverId` TEXT, `content` TEXT, `timestamp` INTEGER NOT NULL, `isRead` INTEGER NOT NULL)");
        }
    };

    static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Empty migration to trigger schema hash update
        }
    };

    static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE chat_sessions ADD COLUMN unreadCount INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE memories ADD COLUMN category TEXT");
        }
    };

    static final Migration MIGRATION_27_28 = new Migration(27, 28) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `characters` ADD COLUMN `autoMomentIntervalHours` REAL NOT NULL DEFAULT 8.0");
            database.execSQL("ALTER TABLE `characters` ADD COLUMN `autoMomentStartTime` TEXT DEFAULT '08:00'");
            database.execSQL("ALTER TABLE `characters` ADD COLUMN `autoMomentEndTime` TEXT DEFAULT '22:00'");
            database.execSQL("ALTER TABLE `characters` ADD COLUMN `autoMomentProbability` INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_28_29 = new Migration(28, 29) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Empty migration to trigger schema hash update
        }
    };

    static final Migration MIGRATION_29_30 = new Migration(29, 30) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `user_profile_nodes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`characterId` INTEGER NOT NULL, " +
                "`category` TEXT, " +
                "`factContent` TEXT, " +
                "`emotionTag` TEXT DEFAULT '普通', " +
                "`confidence` INTEGER NOT NULL DEFAULT 5, " +
                "`keywords` TEXT, " +
                "`lastUpdated` INTEGER NOT NULL, " +
                "`isActive` INTEGER NOT NULL DEFAULT 1)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `episodic_memory` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`characterId` INTEGER NOT NULL, " +
                "`episodeDate` TEXT, " +
                "`title` TEXT, " +
                "`keywords` TEXT, " +
                "`subjectiveDiary` TEXT, " +
                "`emotionalTone` TEXT, " +
                "`importanceLevel` INTEGER NOT NULL DEFAULT 1, " +
                "`participants` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`isRecalled` INTEGER NOT NULL DEFAULT 0, " +
                "`lastRecalledAt` INTEGER NOT NULL DEFAULT 0, " +
                "`recallCount` INTEGER NOT NULL DEFAULT 0)");
        }
    };

    static final Migration MIGRATION_30_31 = new Migration(30, 31) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Recreate the two new tables with correct column defaults to match Entity @ColumnInfo annotations
            database.execSQL("DROP TABLE IF EXISTS `user_profile_nodes`");
            database.execSQL("DROP TABLE IF EXISTS `episodic_memory`");

            database.execSQL("CREATE TABLE IF NOT EXISTS `user_profile_nodes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`characterId` INTEGER NOT NULL, " +
                "`category` TEXT, " +
                "`factContent` TEXT, " +
                "`emotionTag` TEXT DEFAULT '普通', " +
                "`confidence` INTEGER NOT NULL DEFAULT 5, " +
                "`keywords` TEXT, " +
                "`lastUpdated` INTEGER NOT NULL, " +
                "`isActive` INTEGER NOT NULL DEFAULT 1)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `episodic_memory` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`characterId` INTEGER NOT NULL, " +
                "`episodeDate` TEXT, " +
                "`title` TEXT, " +
                "`keywords` TEXT, " +
                "`subjectiveDiary` TEXT, " +
                "`emotionalTone` TEXT, " +
                "`importanceLevel` INTEGER NOT NULL DEFAULT 1, " +
                "`participants` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`isRecalled` INTEGER NOT NULL DEFAULT 0, " +
                "`lastRecalledAt` INTEGER NOT NULL DEFAULT 0, " +
                "`recallCount` INTEGER NOT NULL DEFAULT 0)");
        }
    };

    static final Migration MIGRATION_31_32 = new Migration(31, 32) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Recreate user_profile_nodes with new schema: add keyItem, valueContent; remove factContent, keywords
            database.execSQL("CREATE TABLE IF NOT EXISTS `user_profile_nodes_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`characterId` INTEGER NOT NULL, " +
                "`category` TEXT, " +
                "`keyItem` TEXT, " +
                "`valueContent` TEXT, " +
                "`emotionTag` TEXT DEFAULT '普通', " +
                "`confidence` INTEGER NOT NULL DEFAULT 5, " +
                "`lastUpdated` INTEGER NOT NULL, " +
                "`isActive` INTEGER NOT NULL DEFAULT 1)");

            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_profile_nodes_unique` ON `user_profile_nodes_new` (`characterId`, `category`, `keyItem`)");

            // Migrate existing data: generate keyItem from factContent
            database.execSQL("INSERT INTO `user_profile_nodes_new` (`id`, `characterId`, `category`, `keyItem`, `valueContent`, `emotionTag`, `confidence`, `lastUpdated`, `isActive`) " +
                "SELECT `id`, `characterId`, `category`, " +
                "SUBSTR(REPLACE(REPLACE(`factContent`,' ',''),'，',''),1,20) || '_' || `id`, " +
                "`factContent`, `emotionTag`, `confidence`, `lastUpdated`, `isActive` " +
                "FROM `user_profile_nodes`");

            database.execSQL("DROP TABLE `user_profile_nodes`");
            database.execSQL("ALTER TABLE `user_profile_nodes_new` RENAME TO `user_profile_nodes`");
        }
    };

    static final Migration MIGRATION_32_33 = new Migration(32, 33) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `user_profile_nodes` ADD COLUMN `isCustom` INTEGER NOT NULL DEFAULT 0");
        }
    };

    // 33→34: 空迁移，安全兜底——移除 fallbackToDestructiveMigration 后，
    // 如果未来出现迁移缺失，Room 会崩溃并给出明确错误而非静默销毁数据。
    static final Migration MIGRATION_33_34 = new Migration(33, 34) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // No schema changes — only ensures migration chain continuity
        }
    };

    // 34→35: 为所有记忆相关表添加缺失索引
    static final Migration MIGRATION_34_35 = new Migration(34, 35) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // memories: 每次查询都按 characterId 过滤 + 按 type 过滤
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_memories_characterId` ON `memories` (`characterId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_memories_type` ON `memories` (`type`)");
            // episodic_memory: characterId + importanceLevel + isRecalled
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_episodic_characterId` ON `episodic_memory` (`characterId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_episodic_importance` ON `episodic_memory` (`importanceLevel`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_episodic_recalled` ON `episodic_memory` (`isRecalled`)");
            // memos: characterId + targetDate + status
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_memos_characterId` ON `memos` (`characterId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_memos_targetDate` ON `memos` (`targetDate`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_memos_status` ON `memos` (`status`)");
        }
    };

    // 35→36: 修复 Moment 表中 associatedMemoryId / associatedScheduleId 类型 String→int
    static final Migration MIGRATION_35_36 = new Migration(35, 36) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // SQLite 不支持直接修改列类型，需要重建表
            database.execSQL("CREATE TABLE IF NOT EXISTS `moments_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`publisherType` INTEGER NOT NULL, " +
                "`publisherId` TEXT, " +
                "`publisherName` TEXT, " +
                "`publisherAvatar` TEXT, " +
                "`content` TEXT, " +
                "`imageUrl` TEXT, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`associatedScheduleId` INTEGER NOT NULL DEFAULT 0, " +
                "`associatedMemoryId` INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("INSERT INTO `moments_new` (`id`, `publisherType`, `publisherId`, `publisherName`, `publisherAvatar`, `content`, `imageUrl`, `timestamp`, `associatedScheduleId`, `associatedMemoryId`) " +
                "SELECT `id`, `publisherType`, `publisherId`, `publisherName`, `publisherAvatar`, `content`, `imageUrl`, `timestamp`, " +
                "CAST(COALESCE(NULLIF(`associatedScheduleId`,''),'0') AS INTEGER), " +
                "CAST(COALESCE(NULLIF(`associatedMemoryId`,''),'0') AS INTEGER) " +
                "FROM `moments`");
            database.execSQL("DROP TABLE `moments`");
            database.execSQL("ALTER TABLE `moments_new` RENAME TO `moments`");
        }
    };

    // 36→37: 清理 Memo 表中冗余的 timestamp 字段（保留 createdAt）
    static final Migration MIGRATION_36_37 = new Migration(36, 37) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `memos_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`characterId` INTEGER NOT NULL, " +
                "`content` TEXT, " +
                "`targetDate` TEXT, " +
                "`status` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)");
            database.execSQL("INSERT INTO `memos_new` (`id`, `characterId`, `content`, `targetDate`, `status`, `createdAt`) " +
                "SELECT `id`, `characterId`, `content`, `targetDate`, `status`, " +
                "CASE WHEN `createdAt` > 0 THEN `createdAt` ELSE `timestamp` END " +
                "FROM `memos`");
            database.execSQL("DROP TABLE `memos`");
            database.execSQL("ALTER TABLE `memos_new` RENAME TO `memos`");
            // Recreate indexes dropped by table rename
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_memos_characterId` ON `memos` (`characterId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_memos_targetDate` ON `memos` (`targetDate`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_memos_status` ON `memos` (`status`)");
        }
    };

    // 37→38: 添加 nationality 和 location 字段到 characters 表，用于AI文化背景定位
    static final Migration MIGRATION_37_38 = new Migration(37, 38) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE characters ADD COLUMN nationality TEXT");
            database.execSQL("ALTER TABLE characters ADD COLUMN location TEXT");
        }
    };

    // 38→39: 移除 SalientBillboard（关注板）表
    static final Migration MIGRATION_38_39 = new Migration(38, 39) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS `salient_billboard`");
        }
    };

    // 39→40: 新增心声（InnerVoice）表
    static final Migration MIGRATION_39_40 = new Migration(39, 40) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `inner_voices` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`messageId` INTEGER NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`characterId` INTEGER NOT NULL, " +
                "`content` TEXT, " +
                "`emotion` TEXT, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`isRead` INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_inner_voices_messageId` ON `inner_voices` (`messageId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_inner_voices_sessionId` ON `inner_voices` (`sessionId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_inner_voices_characterId` ON `inner_voices` (`characterId`)");
        }
    };

    // 40→41: 修复 inner_voices 表（索引名从 idx_ 修正为 index_，isRead 去掉 DEFAULT 0）
    static final Migration MIGRATION_40_41 = new Migration(40, 41) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 删除旧的错误索引
            database.execSQL("DROP INDEX IF EXISTS `idx_inner_voices_messageId`");
            database.execSQL("DROP INDEX IF EXISTS `idx_inner_voices_sessionId`");
            database.execSQL("DROP INDEX IF EXISTS `idx_inner_voices_characterId`");
            // 重建表以去掉 isRead 的 DEFAULT 0
            database.execSQL("CREATE TABLE IF NOT EXISTS `inner_voices_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`messageId` INTEGER NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`characterId` INTEGER NOT NULL, " +
                "`content` TEXT, " +
                "`emotion` TEXT, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`isRead` INTEGER NOT NULL)");
            database.execSQL("INSERT INTO `inner_voices_new` (`id`,`messageId`,`sessionId`,`characterId`,`content`,`emotion`,`timestamp`,`isRead`) " +
                "SELECT `id`,`messageId`,`sessionId`,`characterId`,`content`,`emotion`,`timestamp`,`isRead` FROM `inner_voices`");
            database.execSQL("DROP TABLE `inner_voices`");
            database.execSQL("ALTER TABLE `inner_voices_new` RENAME TO `inner_voices`");
            // 用正确的索引名重建
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_inner_voices_messageId` ON `inner_voices` (`messageId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_inner_voices_sessionId` ON `inner_voices` (`sessionId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_inner_voices_characterId` ON `inner_voices` (`characterId`)");
        }
    };

    // 41→42: 记忆系统关联用户人设——给三张记忆表加 myPersonaName，给 my_personas 加 enableProfileExtraction
    static final Migration MIGRATION_41_42 = new Migration(41, 42) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `user_profile_nodes` ADD COLUMN `myPersonaName` TEXT DEFAULT ''");
            database.execSQL("ALTER TABLE `episodic_memory` ADD COLUMN `myPersonaName` TEXT DEFAULT ''");
            database.execSQL("ALTER TABLE `memories` ADD COLUMN `myPersonaName` TEXT DEFAULT ''");
            database.execSQL("ALTER TABLE `my_personas` ADD COLUMN `enableProfileExtraction` INTEGER NOT NULL DEFAULT 0");
        }
    };

    // 42→43: 填充 myPersonaName + 重建 user_profile_nodes 唯一索引
    static final Migration MIGRATION_42_43 = new Migration(42, 43) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 查找主人设名，旧记忆统一归属主人设
            String mainPersona = "我"; // 兜底
            android.database.Cursor c = database.query("SELECT `name` FROM `my_personas` WHERE `isMainPersona` = 1 LIMIT 1");
            if (c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) mainPersona = n;
            }
            c.close();

            // 先填充 myPersonaName（在重建唯一索引之前）
            database.execSQL("UPDATE `user_profile_nodes` SET `myPersonaName` = ? WHERE `myPersonaName` = '' OR `myPersonaName` IS NULL",
                new Object[]{mainPersona});
            database.execSQL("UPDATE `episodic_memory` SET `myPersonaName` = ? WHERE `myPersonaName` = '' OR `myPersonaName` IS NULL",
                new Object[]{mainPersona});
            database.execSQL("UPDATE `memories` SET `myPersonaName` = ? WHERE `myPersonaName` = '' OR `myPersonaName` IS NULL",
                new Object[]{mainPersona});

            // 重建唯一索引，纳入 myPersonaName
            database.execSQL("DROP INDEX IF EXISTS `index_user_profile_nodes_unique`");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_profile_nodes_unique` ON `user_profile_nodes` (`characterId`, `myPersonaName`, `category`, `keyItem`)");
        }
    };

    // 43→44: 主人设开启画像提取
    // 43→44: 统一 inner_voices 表 schema（确保 isRead 有 DEFAULT 0 匹配 @ColumnInfo）+ 主人设开启画像提取
    static final Migration MIGRATION_43_44 = new Migration(43, 44) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 1. 重建 inner_voices 表，确保 isRead 带 DEFAULT 0（兼容有/无默认值的两种旧 schema）
            database.execSQL("CREATE TABLE IF NOT EXISTS `inner_voices_new2` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`messageId` INTEGER NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`characterId` INTEGER NOT NULL, " +
                "`content` TEXT, " +
                "`emotion` TEXT, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`isRead` INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("INSERT INTO `inner_voices_new2` (`id`,`messageId`,`sessionId`,`characterId`,`content`,`emotion`,`timestamp`,`isRead`) " +
                "SELECT `id`,`messageId`,`sessionId`,`characterId`,`content`,`emotion`,`timestamp`,`isRead` FROM `inner_voices`");
            database.execSQL("DROP TABLE `inner_voices`");
            database.execSQL("ALTER TABLE `inner_voices_new2` RENAME TO `inner_voices`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_inner_voices_messageId` ON `inner_voices` (`messageId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_inner_voices_sessionId` ON `inner_voices` (`sessionId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_inner_voices_characterId` ON `inner_voices` (`characterId`)");

            // 2. 主人设开启画像提取
            database.execSQL("UPDATE `my_personas` SET `enableProfileExtraction` = 1 WHERE `isMainPersona` = 1");
        }
    };

    // 44→45: 新增日历功能三张表——日程事件、经期记录、节假日缓存
    static final Migration MIGRATION_44_45 = new Migration(44, 45) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `calendar_events` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT, " +
                "`notes` TEXT, " +
                "`eventDate` TEXT, " +
                "`startTime` INTEGER NOT NULL, " +
                "`endTime` INTEGER NOT NULL DEFAULT 0, " +
                "`allDay` INTEGER NOT NULL DEFAULT 0, " +
                "`recurrence` TEXT DEFAULT 'NONE', " +
                "`createdAt` INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_calendar_events_date` ON `calendar_events` (`eventDate`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_calendar_events_start` ON `calendar_events` (`startTime`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `cycle_records` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startDate` TEXT, " +
                "`endDate` TEXT, " +
                "`flowLevel` INTEGER NOT NULL DEFAULT 2, " +
                "`symptoms` TEXT, " +
                "`notes` TEXT, " +
                "`createdAt` INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_cycle_records_start` ON `cycle_records` (`startDate`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_cycle_records_end` ON `cycle_records` (`endDate`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `holiday_cache` (" +
                "`date` TEXT NOT NULL, " +
                "`name` TEXT, " +
                "`isOffDay` INTEGER NOT NULL, " +
                "`fetchedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`))");
        }
    };

    // 45→46: 课程表功能两张表
    static final Migration MIGRATION_45_46 = new Migration(45, 46) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `semester_configs` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT, " +
                "`startDate` TEXT, " +
                "`totalWeeks` INTEGER NOT NULL DEFAULT 18, " +
                "`periodDuration` INTEGER NOT NULL DEFAULT 45, " +
                "`periodBreak` INTEGER NOT NULL DEFAULT 10, " +
                "`firstPeriodStart` TEXT DEFAULT '08:00', " +
                "`periodsPerDay` INTEGER NOT NULL DEFAULT 12, " +
                "`isActive` INTEGER NOT NULL DEFAULT 0)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `course_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`semesterId` INTEGER NOT NULL, " +
                "`name` TEXT, " +
                "`teacher` TEXT, " +
                "`location` TEXT, " +
                "`dayOfWeek` INTEGER NOT NULL, " +
                "`startPeriod` INTEGER NOT NULL, " +
                "`periodCount` INTEGER NOT NULL DEFAULT 1, " +
                "`weekPattern` TEXT DEFAULT 'EVERY', " +
                "`color` INTEGER NOT NULL, " +
                "`notes` TEXT, " +
                "`createdAt` INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_course_semester` ON `course_entries` (`semesterId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `idx_course_day` ON `course_entries` (`dayOfWeek`)");
        }
    };

    static final Migration MIGRATION_46_47 = new Migration(46, 47) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `semester_configs` ADD COLUMN `customPeriods` TEXT");
        }
    };

    static final Migration MIGRATION_47_48 = new Migration(47, 48) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `semester_configs` ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 0");
        }
    };

    // 48→49: isDefault field removed from entity; extra column in DB is harmless, Room ignores it
    static final Migration MIGRATION_48_49 = new Migration(48, 49) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            // No-op: isDefault column stays in DB but entity no longer uses it
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "jingxi_database")
                            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43, MIGRATION_43_44, MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48, MIGRATION_48_49)
                            .build();
                    // 启动后台迁移：将旧的 Base64 图片数据迁移为文件存储，避免 CursorWindow 溢出
                    final Context appContext = context.getApplicationContext();
                    Executors.newSingleThreadExecutor().execute(() -> migrateImagesToFiles(INSTANCE, appContext));
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 将旧的 Base64 图片数据从数据库迁移到文件存储。
     * 避免大量 Base64 字符串导致 CursorWindow NO_MEMORY 错误。
     */
    private static void migrateImagesToFiles(AppDatabase db, Context context) {
        try {
            // 检查是否已迁移过
            if (SpUtils.getBoolean("MIGRATION_IMAGES_TO_FILES_DONE", false)) return;

            List<Message> messages = db.messageDao().getMessagesWithBase64ImagesSync();
            if (messages == null || messages.isEmpty()) {
                SpUtils.putBoolean("MIGRATION_IMAGES_TO_FILES_DONE", true);
                return;
            }

            File imageDir = new File(context.getExternalFilesDir(null), "images");
            if (!imageDir.exists()) imageDir.mkdirs();

            int migratedCount = 0;
            for (Message msg : messages) {
                if (msg.imageUrl == null || !msg.imageUrl.startsWith("data:image")) continue;
                try {
                    String base64 = msg.imageUrl.substring(msg.imageUrl.indexOf(",") + 1);
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);

                    String fileName = "msg_img_migrated_" + msg.id + ".jpg";
                    File imageFile = new File(imageDir, fileName);
                    FileOutputStream fos = new FileOutputStream(imageFile);
                    fos.write(bytes);
                    fos.close();

                    msg.imageUrl = imageFile.getAbsolutePath();
                    db.messageDao().update(msg);
                    migratedCount++;
                } catch (Exception e) {
                    Log.e("AppDatabase", "Failed to migrate image for message " + msg.id, e);
                }
            }

            if (migratedCount > 0) {
                Log.i("AppDatabase", "Migrated " + migratedCount + " images from Base64 to file storage");
            }
            SpUtils.putBoolean("MIGRATION_IMAGES_TO_FILES_DONE", true);
        } catch (Exception e) {
            Log.e("AppDatabase", "Image migration failed", e);
        }
    }

    /**
     * Reset the database singleton instance. Call this before replacing the database file
     * (e.g., during backup import) so that the next call to {@link #getDatabase(Context)}
     * creates a fresh Room instance that opens the new database file.
     */
    public static void resetInstance() {
        synchronized (AppDatabase.class) {
            if (INSTANCE != null) {
                if (INSTANCE.isOpen()) {
                    INSTANCE.close();
                }
                INSTANCE = null;
            }
        }
    }
}
