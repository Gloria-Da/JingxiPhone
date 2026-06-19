package com.yoyo.jingxi.utils;

import android.text.TextUtils;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.EpisodicMemory;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.data.entity.UserProfileNode;
import com.yoyo.jingxi.network.OpenAiRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 记忆 Prompt 构建器。
 * 负责将记忆数据（用户画像、心绪）格式化为 AI 对话的 system prompt 上下文。
 * 从 MemoryManager 中提取，专注于上下文字符串构建。
 */
public class MemoryPromptBuilder {

    private final AppDatabase db;

    public MemoryPromptBuilder(AppDatabase db) {
        this.db = db;
    }

    /**
     * 构建用户画像上下文字符串（始终加载，让 AI 维持对用户的长期认知）。
     */
    public String getUserProfileContext(int characterId, String myPersonaName) {
        List<UserProfileNode> profiles = db.userProfileNodeDao().getAllActiveSync(characterId, myPersonaName);
        if (profiles.isEmpty()) return "";

        Map<String, List<UserProfileNode>> grouped = new LinkedHashMap<>();
        for (UserProfileNode p : profiles) {
            String cat = p.category != null ? p.category : "其他";
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(p);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("关于她，你心里知道这些：\n");
        for (Map.Entry<String, List<UserProfileNode>> entry : grouped.entrySet()) {
            for (UserProfileNode p : entry.getValue()) {
                String emo = (p.emotionTag != null && !"普通".equals(p.emotionTag)) ? p.emotionTag : "";
                String emoLabel = !emo.isEmpty() ? "（你对此感到" + emo + "）" : "";
                String certainty = p.confidence >= 8 ? "" : (p.confidence >= 6 ? "（不太确定，但）" : "（隐约记得）");
                sb.append("- ").append(p.keyItem != null ? p.keyItem : "")
                  .append("：").append(p.valueContent)
                  .append(certainty).append(emoLabel).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 构建近期加权心绪的上下文字符串（双通道记忆注入的第二通道——近期记忆）。
     * 按权重降序排列，排除已在关键词结果中出现的 episode。
     */
    public String buildRecentWeightedContext(int characterId, int limit, Set<Integer> excludeIds,
                                              List<EpisodicMemory> weightedEpisodes) {
        if (weightedEpisodes == null || weightedEpisodes.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("你脑海里的记忆片段（不需要别人提起，你自己就会想起来的事）：\n");
        long now = System.currentTimeMillis();
        int count = 0;
        for (EpisodicMemory e : weightedEpisodes) {
            if (excludeIds != null && excludeIds.contains(e.id)) continue;
            if (count >= limit) break;
            sb.append("---\n");
            if (e.emotionalTone != null && !e.emotionalTone.isEmpty()) {
                sb.append("（").append(e.emotionalTone).append("）");
            }
            if (TimeWeightCalculator.isEnabled()) {
                float retention = TimeWeightCalculator.computeEpisodicRetention(
                    e.createdAt, e.lastRecalledAt, e.recallCount, now);
                if (retention < 0.5f) {
                    sb.append("好像是很久以前的事了，细节有些模糊……");
                } else if (retention < 0.8f) {
                    sb.append("还记得，但有些地方已经不太清晰了。");
                }
            }
            sb.append("\n").append(e.title != null ? e.title : "");
            sb.append("\n").append(e.subjectiveDiary != null ? e.subjectiveDiary : "").append("\n");
            count++;
        }
        return sb.toString();
    }

    /**
     * 构建潜意识 AI 请求（沉浸模式下的记忆检索请求）。
     */
    public OpenAiRequest buildSubconsciousRequest(String persona, String userMessage,
                                                   List<Message> recentHistory, String myName,
                                                   String profileContext,
                                                   String worldbookPre, String worldbookMid,
                                                   String worldbookPost, String recentEpisodes, String model) {
        OpenAiRequest request = new OpenAiRequest();
        request.model = !TextUtils.isEmpty(model) ? model : "gpt-4o-mini";
        request.temperature = 0.3f;
        request.messages = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append("你就是").append(persona).append("。不是旁观者。你就是这个人。\n");
        sb.append("你心里关于\"谁对你重要\"的认知来自你的人设和世界书。\n");
        sb.append("你对用户的情感不是你凭空捏造的，而是由你的人设决定的。\n\n");
        if (worldbookPre != null && !worldbookPre.isEmpty())
            sb.append("世界书：\n").append(worldbookPre).append("\n");
        if (profileContext != null && !profileContext.isEmpty())
            sb.append("你对").append(myName).append("的了解：\n").append(profileContext).append("\n");
        if (recentEpisodes != null && !recentEpisodes.isEmpty())
            sb.append("你近期的感受记忆：\n").append(recentEpisodes).append("\n");
        sb.append(myName).append("刚对你说：\"").append(userMessage).append("\"\n\n");
        sb.append("感受此刻。你的阅历、你们的经历、你惦记的事、你的感受——哪些和这句话产生了共鸣？\n");
        sb.append("你心里会自然浮现哪些相关的记忆？\n\n");
        if (recentHistory != null && !recentHistory.isEmpty()) {
            sb.append("刚才的对话：\n");
            int count = 0;
            for (int i = recentHistory.size() - 1; i >= 0 && count < 4; i--) {
                Message msg = recentHistory.get(i);
                if (msg.content != null && !msg.content.trim().isEmpty()) {
                    sb.append(msg.isFromUser ? myName : "你").append(": ")
                      .append(msg.content).append("\n");
                    count++;
                }
            }
            sb.append("\n");
        }
        if (worldbookMid != null && !worldbookMid.isEmpty())
            sb.append("世界书：\n").append(worldbookMid).append("\n");
        sb.append("输出JSON：{\"keywords\":[\"可能相关的记忆关键词\"],\"mood\":\"你的心境(一句)\"}\n");
        sb.append("keywords用来在记忆库里搜索，3-5个。要具体、能触发特定回忆。");
        if (worldbookPost != null && !worldbookPost.isEmpty())
            sb.append("世界书：\n").append(worldbookPost).append("\n");

        request.messages.add(new OpenAiRequest.Message("system", sb.toString()));
        request.messages.add(new OpenAiRequest.Message("user", "请输出JSON，不要markdown。"));
        return request;
    }
}
