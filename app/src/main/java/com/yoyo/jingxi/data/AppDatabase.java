package com.yoyo.jingxi.data;

import android.content.Context;

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

@Database(entities = {Character.class, Message.class, MyPersona.class, ChatSession.class, Memory.class, WorldbookEntry.class, Memo.class, ScheduleEntry.class, EmojiEntry.class, CallRecord.class, CallMessage.class, RelationshipNode.class, RelationshipEdge.class, Moment.class, MomentComment.class, MomentLike.class, MomentNotification.class}, version = 29, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
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

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "jingxi_database")
                            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
