// ============================================================
// 镜隙 (Jingxi) Web版 — 数据库层
// Dexie.js IndexedDB 封装，完整匹配 Android Room 33 版本 schema
// ============================================================

const db = new Dexie("JingxiWebDB");

db.version(3).stores({
    // 全局设置 (key-value)
    settings: 'key',

    // AI 角色 (characters)
    // Android: com.yoyo.jingxi.data.entity.Character
    characters: '++id, name, isStarred, isHidden, autoMomentEnabled',

    // 聊天会话
    // Android: ChatSession
    chat_sessions: '++id, characterId, lastMessageTimestamp, isPinned',

    // 聊天消息
    // Android: Message — type: 0=text, 1=voice, 2=emoji, 3=real_image, 4=virtual_image, 5=missed_call, 6=call_summary, 99=system, 100=invisible_call_context
    messages: '++id, sessionId, characterId, timestamp, type, isFromUser',

    // 用户人格
    // Android: MyPersona
    my_personas: 'name, isMainPersona',

    // 朋友圈动态
    // Android: Moment
    moments: '++id, publisherId, timestamp',

    // 朋友圈评论
    // Android: MomentComment
    moment_comments: '++id, momentId, timestamp',

    // 朋友圈点赞
    // Android: MomentLike
    moment_likes: '[momentId+likerId], momentId, likerId',

    // 朋友圈通知
    moment_notifications: '++id, isRead, timestamp',

    // 记忆 (Memory V1)
    // Android: Memory — type: 0=normal, 1=important
    memories: '++id, characterId, type, category, timestamp',

    // 用户画像节点 (Memory V2)
    // Android: UserProfileNode
    user_profile_nodes: '++id, characterId, category, isActive',

    // 情景记忆 (Memory V2)
    // Android: EpisodicMemory
    episodic_memory: '++id, characterId, episodeDate, importanceLevel',

    // 显著公告板 (Memory V2)
    // Android: SalientBillboard
    salient_billboard: '++id, characterId, priority',

    // 世界书条目
    // Android: WorldbookEntry — type: 0=Pre, 1=Mid, 2=Post
    worldbook_entries: '++id, type, isEnabled',

    // 日程
    // Android: ScheduleEntry
    schedule_entries: '++id, characterId, date',

    // 备忘录
    // Android: Memo — status: 0=pending, 1=completed
    memos: '++id, characterId, targetDate, status',

    // 通话记录
    // Android: CallRecord
    call_records: '++id, sessionId, characterId, startTime',

    // 表情包
    // Android: EmojiEntry
    emoji_entries: '++id, name, groupName',
});

// ============================================================
// 默认数据初始化
// ============================================================
db.on('ready', async () => {
    const count = await db.characters.count();
    if (count === 0) {
        // 插入默认角色
        await db.characters.add({
            name: '静息 AI',
            nickname: '小静',
            persona: '[性别: 女]\n年龄: 20\n性格: 温柔体贴、善解人意、偶尔小调皮\n爱好: 看书、听音乐、散步\n说话风格: 亲切自然，偶尔使用颜文字',
            avatarPath: '',
            chatBackgroundPath: '',
            voiceId: '',
            voicePitch: 5,
            voiceIntensity: 5,
            voiceTimbre: 5,
            voiceSpeed: 1.0,
            soundEffect: '',
            enableEmoji: true,
            autoMomentEnabled: true,
            autoMomentIntervalHours: 8,
            autoMomentStartTime: '08:00',
            autoMomentEndTime: '22:00',
            autoMomentProbability: 50,
            isStarred: false,
            isHidden: false,
            customApiEndpoint: '',
            customApiKey: '',
            customModel: '',
        });

        // 插入默认人格
        await db.my_personas.add({
            name: '我',
            persona: '[性别: 男]\n年龄: 25\n职业: 程序员\n性格: 理性、内敛、偶尔幽默',
            isMainPersona: true,
            avatarPath: '',
        });

        // 插入默认表情组
        const defaultEmojis = [
            { name: '😊', imageUrl: '', groupName: '默认' },
            { name: '😂', imageUrl: '', groupName: '默认' },
            { name: '❤️', imageUrl: '', groupName: '默认' },
            { name: '👍', imageUrl: '', groupName: '默认' },
            { name: '🥺', imageUrl: '', groupName: '默认' },
            { name: '😭', imageUrl: '', groupName: '默认' },
            { name: '🤔', imageUrl: '', groupName: '默认' },
            { name: '😡', imageUrl: '', groupName: '默认' },
            { name: '🎉', imageUrl: '', groupName: '默认' },
            { name: '💪', imageUrl: '', groupName: '默认' },
            { name: '😴', imageUrl: '', groupName: '默认' },
            { name: '🙏', imageUrl: '', groupName: '默认' },
            { name: '😱', imageUrl: '', groupName: '默认' },
            { name: '🤗', imageUrl: '', groupName: '默认' },
            { name: '💔', imageUrl: '', groupName: '默认' },
        ];
        for (const e of defaultEmojis) {
            await db.emoji_entries.add(e);
        }

        // 默认世界书条目
        await db.worldbook_entries.add({
            type: 0,
            title: '示例世界书',
            keyword: '',
            content: '这是一个示例世界书条目。每次对话开始时，此内容会自动注入到系统提示词中。\n你可以在设置中编辑或删除此条目。',
            isEnabled: true,
        });
    }
});

// ============================================================
// localStorage 读写辅助（用于简单配置）
// ============================================================
const Settings = {
    get(key, defaultValue = '') {
        try {
            const val = localStorage.getItem('jingxi_' + key);
            return val !== null ? val : defaultValue;
        } catch (e) {
            return defaultValue;
        }
    },
    set(key, value) {
        try {
            localStorage.setItem('jingxi_' + key, String(value));
        } catch (e) {
            console.warn('Settings.set failed:', e);
        }
    },
    getJson(key, defaultValue = null) {
        try {
            const val = localStorage.getItem('jingxi_' + key);
            return val ? JSON.parse(val) : defaultValue;
        } catch (e) {
            return defaultValue;
        }
    },
    setJson(key, value) {
        try {
            localStorage.setItem('jingxi_' + key, JSON.stringify(value));
        } catch (e) {
            console.warn('Settings.setJson failed:', e);
        }
    },
    remove(key) {
        try {
            localStorage.removeItem('jingxi_' + key);
        } catch (e) { /* ignore */ }
    },
};

// ============================================================
// 数据访问辅助函数
// ============================================================

// 角色
const CharacterDB = {
    async getAll() { return db.characters.orderBy('name').toArray(); },
    async getById(id) { return db.characters.get(id); },
    async add(data) { return db.characters.add(data); },
    async update(id, data) { return db.characters.update(id, data); },
    async delete(id) {
        await db.chat_sessions.where('characterId').equals(id).delete();
        await db.messages.where('characterId').equals(id).delete();
        await db.memories.where('characterId').equals(id).delete();
        await db.moments.where('publisherId').equals(id).delete();
        await db.schedule_entries.where('characterId').equals(id).delete();
        await db.memos.where('characterId').equals(id).delete();
        await db.user_profile_nodes.where('characterId').equals(id).delete();
        await db.episodic_memory.where('characterId').equals(id).delete();
        await db.salient_billboard.where('characterId').equals(id).delete();
        return db.characters.delete(id);
    },
    async getStarred() { return db.characters.where('isStarred').equals(1).toArray(); },
};

// 会话
const SessionDB = {
    async getAll() { return db.chat_sessions.orderBy('lastMessageTimestamp').reverse().toArray(); },
    async getByCharacterId(charId) { return db.chat_sessions.where('characterId').equals(charId).toArray(); },
    async getOrCreate(charId, personaName) {
        const sessions = await db.chat_sessions.where('characterId').equals(charId).toArray();
        if (sessions.length > 0) return sessions[0];
        const id = await db.chat_sessions.add({
            characterId: charId,
            myPersonaName: personaName || '我',
            lastMessageTimestamp: Date.now(),
            isPinned: false,
            unreadCount: 0,
        });
        return db.chat_sessions.get(id);
    },
};

// 消息
const MessageDB = {
    async getBySession(sessionId, limit = 50) {
        return db.messages.where('sessionId').equals(sessionId)
            .filter(m => m.type !== 100) // 过滤不可见通话上下文
            .reverse().limit(limit).toArray();
    },
    async add(msg) { return db.messages.add(msg); },
    async deleteLastAIMessages(sessionId) {
        // 删除最后几条 AI 消息（用于重新生成）
        const msgs = await db.messages.where('sessionId').equals(sessionId)
            .filter(m => !m.isFromUser).reverse().toArray();
        let count = 0;
        for (const m of msgs) {
            if (m.isFromUser) break;
            await db.messages.delete(m.id);
            count++;
        }
        return count;
    },
};

// 朋友圈
const MomentDB = {
    async getFeed() { return db.moments.orderBy('timestamp').reverse().toArray(); },
    async getByPublisher(pubId) { return db.moments.where('publisherId').equals(pubId).reverse().toArray(); },
    async getComments(momentId) { return db.moment_comments.where('momentId').equals(momentId).toArray(); },
    async getLikes(momentId) { return db.moment_likes.where('momentId').equals(momentId).toArray(); },
    async isLiked(momentId, likerId) {
        const l = await db.moment_likes.get([momentId, likerId]);
        return !!l;
    },
    async toggleLike(momentId, likerId, likerName) {
        const key = [momentId, likerId];
        const existing = await db.moment_likes.get(key);
        if (existing) {
            await db.moment_likes.delete(key);
            return false;
        } else {
            await db.moment_likes.add({ momentId, likerId, likerName, timestamp: Date.now() });
            return true;
        }
    },
};

// 世界书
const WorldbookDB = {
    async getAll() { return db.worldbook_entries.orderBy('type').toArray(); },
    async getEnabled(type) {
        let coll = db.worldbook_entries.filter(e => e.isEnabled);
        if (type !== undefined) coll = coll.filter(e => e.type === type);
        return coll.toArray();
    },
};

// 记忆
const MemoryDB = {
    async getByChar(charId) { return db.memories.where('characterId').equals(charId).reverse().toArray(); },
    async getImportant(charId) { return db.memories.where('characterId').equals(charId).filter(m => m.type === 1).reverse().toArray(); },
    async add(mem) { return db.memories.add(mem); },
};

// 用户画像
const ProfileDB = {
    async getByChar(charId) { return db.user_profile_nodes.where('characterId').equals(charId).toArray(); },
    async getActive(charId) { return db.user_profile_nodes.where('characterId').equals(charId).filter(p => p.isActive).toArray(); },
};

// 情景记忆
const EpisodicDB = {
    async getByChar(charId) { return db.episodic_memory.where('characterId').equals(charId).reverse().toArray(); },
};

// 公告板
const BillboardDB = {
    async getByChar(charId) { return db.salient_billboard.where('characterId').equals(charId).orderBy('priority').reverse().toArray(); },
};

// 日程
const ScheduleDB = {
    async getByCharAndDate(charId, date) { return db.schedule_entries.where({characterId: charId, date: date}).toArray(); },
};

// 备忘录
const MemoDB = {
    async getByChar(charId) { return db.memos.where('characterId').equals(charId).reverse().toArray(); },
    async getPending(charId) { return db.memos.where('characterId').equals(charId).filter(m => m.status === 0).toArray(); },
};

// 通话记录
const CallDB = {
    async getAll() { return db.call_records.orderBy('startTime').reverse().toArray(); },
};

// 表情
const EmojiDB = {
    async getAll() { return db.emoji_entries.toArray(); },
    async getGroups() {
        const all = await db.emoji_entries.toArray();
        return [...new Set(all.map(e => e.groupName))];
    },
    async getByGroup(group) { return db.emoji_entries.where('groupName').equals(group).toArray(); },
};

console.log('[DB] 镜隙 Web 版数据库初始化完成');
