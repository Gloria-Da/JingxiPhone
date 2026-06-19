package com.yoyo.jingxi.utils;

import android.text.TextUtils;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.DailySchedule;
import com.yoyo.jingxi.data.entity.EpisodicMemory;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.data.entity.UserProfileNode;
import com.yoyo.jingxi.network.OpenAiRequest;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 记忆管理器（CRUD 协调层）。
 * 负责 UserProfileNode、EpisodicMemory 的增删改查。
 * 搜索和 Prompt 构建已委托给 MemorySearchService 和 MemoryPromptBuilder。
 */
public class MemoryManager {
    private static volatile MemoryManager INSTANCE;
    private AppDatabase db;
    private MemorySearchService searchService;
    private MemoryPromptBuilder promptBuilder;

    private MemoryManager() {}

    public static MemoryManager getInstance() {
        if (INSTANCE == null) {
            synchronized (MemoryManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MemoryManager();
                }
            }
        }
        return INSTANCE;
    }

    public void init(AppDatabase database) {
        this.db = database;
        this.searchService = new MemorySearchService(database);
        this.promptBuilder = new MemoryPromptBuilder(database);
    }

    private AppDatabase db() {
        if (db == null) {
            db = AppDatabase.getDatabase(com.yoyo.jingxi.JingxiApplication.getInstance());
            this.searchService = new MemorySearchService(db);
            this.promptBuilder = new MemoryPromptBuilder(db);
        }
        return db;
    }

    private MemorySearchService search() {
        if (searchService == null) searchService = new MemorySearchService(db());
        return searchService;
    }

    private MemoryPromptBuilder prompt() {
        if (promptBuilder == null) promptBuilder = new MemoryPromptBuilder(db());
        return promptBuilder;
    }

    // ==================== Utility ====================

    public static String genKeyItem(String content) {
        if (content == null || content.isEmpty()) return "item";
        String cleaned = content.replaceAll("\\s+", "").replaceAll("[，。！？、；：\"'（）《》\\[\\]]", "");
        if (cleaned.length() <= 10) return cleaned;
        return cleaned.substring(0, 10);
    }

    // ==================== UserProfileNode CRUD ====================

    public long addUserProfileNode(int characterId, String myPersonaName, String category, String keyItem,
                                   String valueContent, String emotionTag, int confidence) {
        UserProfileNode node = new UserProfileNode();
        node.characterId = characterId;
        node.myPersonaName = myPersonaName != null ? myPersonaName : "";
        node.category = category != null ? category : "其他";
        node.keyItem = keyItem != null ? keyItem : "";
        node.valueContent = valueContent;
        node.emotionTag = emotionTag != null ? emotionTag : "普通";
        node.confidence = Math.max(1, Math.min(10, confidence));
        node.isCustom = !MemoryPresetKeys.isPresetKeyItem(node.category, node.keyItem);

        // Use indexed exact lookup instead of O(n) scan
        UserProfileNode existing = db().userProfileNodeDao()
            .findByCharacterIdCategoryAndKeyItem(characterId, node.myPersonaName, node.category, node.keyItem);
        if (existing != null) {
            existing.valueContent = node.valueContent;
            existing.emotionTag = node.emotionTag;
            existing.confidence = node.confidence;
            existing.lastUpdated = System.currentTimeMillis();
            existing.isCustom = node.isCustom;
            existing.isActive = true;
            db().userProfileNodeDao().update(existing);
            return existing.id;
        }
        return db().userProfileNodeDao().insert(node);
    }

    public void deactivateUserProfileNode(int id) {
        db().userProfileNodeDao().deactivate(id);
    }

    public List<UserProfileNode> getAllActiveUserProfiles(int characterId, String myPersonaName) {
        return db().userProfileNodeDao().getAllActiveSync(characterId, myPersonaName);
    }

    /** 按角色加载全部画像（不限人设），用于 MemoryActivity「全部人设」模式。 */
    public List<UserProfileNode> getAllActiveUserProfilesByCharacter(int characterId) {
        return db().userProfileNodeDao().getAllActiveSyncByCharacter(characterId);
    }

    public boolean confirmProfileNode(int characterId, String myPersonaName, String category, String keyItem) {
        UserProfileNode node = db().userProfileNodeDao()
            .findByCharacterIdCategoryAndKeyItem(characterId, myPersonaName, category, keyItem);
        if (node != null) {
            node.confidence = Math.min(10, node.confidence + 2);
            node.lastUpdated = System.currentTimeMillis();
            db().userProfileNodeDao().update(node);
            return true;
        }
        return false;
    }

    // ==================== EpisodicMemory CRUD ====================

    public long addEpisodicMemory(int characterId, String myPersonaName, String episodeDate, String title,
                                  String subjectiveDiary, String keywords, String emotionalTone,
                                  int importanceLevel, String participants) {
        EpisodicMemory mem = new EpisodicMemory();
        mem.characterId = characterId;
        mem.myPersonaName = myPersonaName != null ? myPersonaName : "";
        mem.episodeDate = episodeDate != null ? episodeDate : new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        mem.title = title;
        mem.subjectiveDiary = subjectiveDiary;
        mem.keywords = keywords;
        mem.emotionalTone = emotionalTone != null ? emotionalTone : "平静";
        mem.importanceLevel = Math.max(1, Math.min(5, importanceLevel));
        mem.participants = participants;
        return db().episodicMemoryDao().insert(mem);
    }

    public List<EpisodicMemory> getRecentEpisodes(int characterId, String myPersonaName, int limit) {
        if (limit <= 0) {
            return db().episodicMemoryDao().getByCharacterIdSync(characterId, myPersonaName);
        }
        return db().episodicMemoryDao().getRecentSync(characterId, myPersonaName, limit);
    }

    public List<EpisodicMemory> getEpisodesPaged(int characterId, String myPersonaName, int limit, int offset) {
        return db().episodicMemoryDao().getByCharacterIdPaged(characterId, myPersonaName, limit, offset);
    }

    /** 不分人设的分页查询，用于 MemoryActivity「全部人设」模式。 */
    public List<EpisodicMemory> getEpisodesPagedAll(int characterId, int limit, int offset) {
        return db().episodicMemoryDao().getByCharacterIdPagedAll(characterId, limit, offset);
    }

    public int getEpisodeCount(int characterId, String myPersonaName) {
        return db().episodicMemoryDao().getCount(characterId, myPersonaName);
    }

    /** 不分人设的计数，用于 MemoryActivity「全部人设」模式。 */
    public int getEpisodeCountAll(int characterId) {
        return db().episodicMemoryDao().getCountAll(characterId);
    }

    public List<EpisodicMemory> getRecentEpisodesWeighted(int characterId, String myPersonaName, int limit) {
        List<EpisodicMemory> episodes;
        if (limit <= 0) {
            episodes = db().episodicMemoryDao().getByCharacterIdSync(characterId, myPersonaName);
        } else {
            episodes = db().episodicMemoryDao().getRecentSync(characterId, myPersonaName, limit);
        }
        if (TimeWeightCalculator.isEnabled()) {
            long now = System.currentTimeMillis();
            java.util.Collections.sort(episodes, (a, b) -> {
                float scoreA = TimeWeightCalculator.computeEpisodicWeight(
                    a.importanceLevel, a.createdAt, a.lastRecalledAt, a.recallCount, now);
                float scoreB = TimeWeightCalculator.computeEpisodicWeight(
                    b.importanceLevel, b.createdAt, b.lastRecalledAt, b.recallCount, now);
                return Float.compare(scoreB, scoreA);
            });
        }
        return episodes;
    }

    public List<EpisodicMemory> getImportantEpisodes(int characterId, String myPersonaName, int minImportance) {
        return db().episodicMemoryDao().getByImportanceSync(characterId, myPersonaName, minImportance);
    }

    public EpisodicMemory getNextRecallCandidate(int characterId, String myPersonaName) {
        List<EpisodicMemory> candidates = db().episodicMemoryDao().getRecallCandidates(characterId, myPersonaName, 1);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public void markRecalled(int id) {
        db().episodicMemoryDao().markRecalled(id, System.currentTimeMillis());
    }

    // ==================== Delegated: Search & Prompt Building ====================

    public List<String> extractKeywordsFromMessage(String message) {
        return search().extractKeywordsFromMessage(message);
    }

    public MemorySearchService.MemorySearchResult keywordSearch(int characterId, String myPersonaName, List<String> keywords) {
        return search().keywordSearch(characterId, myPersonaName, keywords);
    }

    public String getUserProfileContext(int characterId, String myPersonaName) {
        return prompt().getUserProfileContext(characterId, myPersonaName);
    }

    public String buildRecentWeightedContext(int characterId, String myPersonaName, int limit, Set<Integer> excludeIds) {
        List<EpisodicMemory> episodes = getRecentEpisodesWeighted(characterId, myPersonaName, 0);
        return prompt().buildRecentWeightedContext(characterId, limit, excludeIds, episodes);
    }

    public OpenAiRequest buildSubconsciousRequest(String persona, String userMessage,
                                                   List<Message> recentHistory, String myName,
                                                   String profileContext,
                                                   String worldbookPre, String worldbookMid,
                                                   String worldbookPost, String recentEpisodes, String model) {
        return prompt().buildSubconsciousRequest(persona, userMessage, recentHistory, myName,
            profileContext, worldbookPre, worldbookMid, worldbookPost,
            recentEpisodes, model);
    }

    public List<String> parseSubconsciousResponse(String jsonResponse) {
        return search().parseSubconsciousResponse(jsonResponse);
    }

    // ==================== Delegated: API Config (backward compat) ====================

    /**
     * @deprecated Use {@link MemoryApiConfig#getCuratorApiConfig()} instead.
     */
    @Deprecated
    public static MemoryApiConfig.ApiConfig getCuratorApiConfig() {
        return MemoryApiConfig.getCuratorApiConfig();
    }

    /**
     * @deprecated Use {@link MemoryApiConfig#getSubconsciousApiConfig()} instead.
     */
    @Deprecated
    public static MemoryApiConfig.ApiConfig getSubconsciousApiConfig() {
        return MemoryApiConfig.getSubconsciousApiConfig();
    }

    /**
     * @deprecated Use {@link MemoryApiConfig.ApiConfig} instead.
     */
    @Deprecated
    public static class SubconsciousApiConfig extends MemoryApiConfig.ApiConfig {}

    /**
     * Backward-compatible type alias for MemorySearchResult.
     * @deprecated Use {@link MemorySearchService.MemorySearchResult} directly.
     */
    @Deprecated
    public static class MemorySearchResult extends MemorySearchService.MemorySearchResult {}
}
