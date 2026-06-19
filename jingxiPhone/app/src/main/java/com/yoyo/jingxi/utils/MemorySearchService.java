package com.yoyo.jingxi.utils;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.EpisodicMemory;
import com.yoyo.jingxi.data.entity.UserProfileNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 记忆搜索服务。
 * 负责关键词提取（经济模式）、跨表关键词搜索、潜意识 AI 响应解析。
 * 从 MemoryManager 中提取，专注于检索逻辑。
 */
public class MemorySearchService {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一",
        "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着",
        "没有", "看", "好", "自己", "这", "他", "她", "它", "们", "那", "那个",
        "这个", "怎么", "什么", "为什么", "哪", "哪些", "吗", "吧", "呢", "啊",
        "哦", "嗯", "哈", "呀", "啦", "嘛", "哇", "哼", "喂", "呵呵", "嘻嘻"
    ));

    private final AppDatabase db;
    private final Gson gson = new Gson();

    public MemorySearchService(AppDatabase db) {
        this.db = db;
    }

    public static class MemorySearchResult {
        public List<UserProfileNode> matchedProfiles = new ArrayList<>();
        public List<EpisodicMemory> matchedEpisodes = new ArrayList<>();
        public String formattedContext = "";

        public boolean isEmpty() {
            return matchedProfiles.isEmpty() && matchedEpisodes.isEmpty();
        }
    }

    /**
     * 从用户消息中提取关键词（经济模式：纯本地分词 + 停用词过滤）。
     */
    public List<String> extractKeywordsFromMessage(String message) {
        if (TextUtils.isEmpty(message)) return new ArrayList<>();

        List<String> keywords = new ArrayList<>();
        String[] tokens = message.split("[，。！？；：、\\s,.!?;:]+");

        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.length() < 2) continue;
            if (STOP_WORDS.contains(trimmed)) continue;

            if (trimmed.length() <= 5) {
                keywords.add(trimmed);
            } else {
                keywords.add(trimmed);
                for (int i = 0; i < trimmed.length() - 1; i++) {
                    String bigram = trimmed.substring(i, Math.min(i + 2, trimmed.length()));
                    if (bigram.length() >= 2 && !STOP_WORDS.contains(bigram)) {
                        keywords.add(bigram);
                    }
                }
            }
        }

        Set<String> seen = new HashSet<>();
        List<String> unique = new ArrayList<>();
        for (String kw : keywords) {
            if (!seen.contains(kw)) {
                seen.add(kw);
                unique.add(kw);
            }
        }
        return unique;
    }

    /**
     * 跨表关键词搜索：在 user_profile_nodes、episodic_memory
     * 两个表中按关键词搜索匹配条目。
     */
    public MemorySearchResult keywordSearch(int characterId, String myPersonaName, List<String> keywords) {
        MemorySearchResult result = new MemorySearchResult();
        if (keywords.isEmpty()) return result;

        Set<Integer> profileIds = new HashSet<>();
        Set<Integer> episodeIds = new HashSet<>();

        for (String kw : keywords) {
            if (kw.length() < 2) continue;

            List<UserProfileNode> profiles = db.userProfileNodeDao().keywordSearch(characterId, myPersonaName, kw);
            for (UserProfileNode p : profiles) {
                if (!profileIds.contains(p.id)) {
                    profileIds.add(p.id);
                    result.matchedProfiles.add(p);
                }
            }

            List<EpisodicMemory> episodes = db.episodicMemoryDao().keywordSearch(characterId, myPersonaName, kw);
            for (EpisodicMemory e : episodes) {
                if (!episodeIds.contains(e.id)) {
                    episodeIds.add(e.id);
                    result.matchedEpisodes.add(e);
                }
            }
        }

        // Mark matched episodes as recalled (间隔重复——被想起的记忆会记得更牢)
        long now = System.currentTimeMillis();
        for (EpisodicMemory e : result.matchedEpisodes) {
            db.episodicMemoryDao().markRecalled(e.id, now);
        }

        if (TimeWeightCalculator.isEnabled()) {
            java.util.Collections.sort(result.matchedEpisodes, (a, b) -> {
                float scoreA = TimeWeightCalculator.computeEpisodicWeight(
                    a.importanceLevel, a.createdAt, a.lastRecalledAt, a.recallCount, now);
                float scoreB = TimeWeightCalculator.computeEpisodicWeight(
                    b.importanceLevel, b.createdAt, b.lastRecalledAt, b.recallCount, now);
                return Float.compare(scoreB, scoreA);
            });
        }

        result.formattedContext = formatSearchResultContext(result);
        return result;
    }

    /**
     * 解析潜意识 AI 的 JSON 响应，提取关键词列表。
     */
    public List<String> parseSubconsciousResponse(String jsonResponse) {
        List<String> keywords = new ArrayList<>();
        if (jsonResponse == null) return keywords;

        String cleaned = jsonResponse.trim();
        if (cleaned.contains("```json")) {
            int start = cleaned.indexOf("```json") + 7;
            int end = cleaned.lastIndexOf("```");
            if (end > start) cleaned = cleaned.substring(start, end).trim();
        } else if (cleaned.contains("```")) {
            int start = cleaned.indexOf("```") + 3;
            int end = cleaned.lastIndexOf("```");
            if (end > start) cleaned = cleaned.substring(start, end).trim();
        }

        try {
            JsonObject obj = gson.fromJson(cleaned, JsonObject.class);
            if (obj.has("keywords")) {
                JsonArray arr = obj.getAsJsonArray("keywords");
                for (int i = 0; i < arr.size(); i++) {
                    String kw = arr.get(i).getAsString().trim();
                    if (!TextUtils.isEmpty(kw)) {
                        keywords.add(kw);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return keywords;
    }

    private String formatSearchResultContext(MemorySearchResult result) {
        if (result.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        if (!result.matchedProfiles.isEmpty()) {
            java.util.Map<String, java.util.List<UserProfileNode>> grouped = new java.util.LinkedHashMap<>();
            for (UserProfileNode p : result.matchedProfiles) {
                String cat = p.category != null ? p.category : "其他";
                grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(p);
            }
            sb.append("她说过这些事，你一直记得：\n");
            for (java.util.Map.Entry<String, java.util.List<UserProfileNode>> entry : grouped.entrySet()) {
                for (UserProfileNode p : entry.getValue()) {
                    String feeling = !"普通".equals(p.emotionTag) ? "（想到这个，你觉得" + p.emotionTag + "）" : "";
                    sb.append("- ").append(p.valueContent).append(feeling).append("\n");
                }
            }
            sb.append("\n");
        }

        if (!result.matchedEpisodes.isEmpty()) {
            sb.append("她的话让你想起了这些往事（把它们自然地融入对话，不用刻意说「我记得」）：\n");
            long now = System.currentTimeMillis();
            for (EpisodicMemory e : result.matchedEpisodes) {
                sb.append("---\n");
                if (e.emotionalTone != null) {
                    sb.append("想起这件事的时候，你心里有些").append(e.emotionalTone).append("。");
                }
                if (TimeWeightCalculator.isEnabled()) {
                    float retention = TimeWeightCalculator.computeEpisodicRetention(
                        e.createdAt, e.lastRecalledAt, e.recallCount, now);
                    if (retention < 0.5f) {
                        sb.append("好像是很久以前的事了，细节有些模糊……");
                    } else if (retention < 0.8f) {
                        sb.append("还记得，但一些地方已经想不太起来了。");
                    }
                }
                sb.append("\n").append(e.subjectiveDiary != null ? e.subjectiveDiary : "").append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
