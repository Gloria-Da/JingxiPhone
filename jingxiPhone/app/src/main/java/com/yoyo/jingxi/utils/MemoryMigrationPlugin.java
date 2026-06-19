package com.yoyo.jingxi.utils;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.EpisodicMemory;
import com.yoyo.jingxi.data.entity.Memory;
import com.yoyo.jingxi.data.entity.UserProfileNode;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class MemoryMigrationPlugin {
    private static final Gson gson = new Gson();
    private static final String TAG = "MemoryMigration";

    public interface MigrationCallback {
        void onProgress(int current, int total, String currentItem);
        void onComplete(int profilesCreated, int episodesCreated, int failedCount);
        void onError(String message);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isRunning = false;
    private volatile boolean isCancelled = false;

    public MemoryMigrationPlugin(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isRunning() { return isRunning; }
    public void cancel() { isCancelled = true; }

    public void runMigration(int characterId, MigrationCallback callback) {
        if (isRunning) {
            if (callback != null) callback.onError("迁移正在进行中");
            return;
        }

        isRunning = true;
        isCancelled = false;

        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);
            OpenAIManager aiManager = new OpenAIManager();
            MemoryManager memMgr = MemoryManager.getInstance();
            memMgr.init(db);

            // Load character for persona-aware migration
            Character character = db.characterDao().getCharacterById(characterId);
            String charName = character != null ? character.name : "角色";
            String charPersona = character != null && character.persona != null ? character.persona : "";

            // 获取主人设名，迁移的记忆统一归属主人设
            com.yoyo.jingxi.data.entity.MyPersona mainPersona = db.myPersonaDao().getMainPersona();
            String mainPersonaName = mainPersona != null ? mainPersona.name : "我";

            // Build relationship and worldbook context (same as current curators)
            String relInfo = AiReplyHelper.buildRelationshipInfo(charPersona, db);
            String wbCtx = AiReplyHelper.buildWorldbookContextForCurator(db);

            String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
            String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
            if (!endpoint.endsWith("/")) endpoint += "/";
            String url = endpoint + "v1/chat/completions";
            String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");

            if (apiKey.isEmpty()) {
                isRunning = false;
                if (callback != null) callback.onError("请先配置 API KEY");
                return;
            }

            List<Memory> allMemories = db.memoryDao().getMemoriesByCharacterIdSyncAll(characterId);
            int total = allMemories != null ? allMemories.size() : 0;
            int profilesCreated = 0;
            int episodesCreated = 0;
            int failedCount = 0;

            int lastMigratedId = SpUtils.getInt("MEMORY_MIGRATION_LAST_ID_" + characterId, -1);

            for (int i = 0; i < total; i++) {
                if (isCancelled) {
                    isRunning = false;
                    if (callback != null) callback.onError("迁移已取消");
                    return;
                }

                Memory mem = allMemories.get(i);
                if (mem.id <= lastMigratedId) continue;

                boolean success = false;
                try {
                    if (mem.type == 1) {
                        String profileJson = migrateImportantMemory(aiManager, url, apiKey, model, mem, charName, charPersona, relInfo, wbCtx);
                        if (profileJson != null) {
                            JsonObject json = gson.fromJson(profileJson, JsonObject.class);
                            String cat = json.has("category") ? json.get("category").getAsString() : "其他";
                            String fact = json.has("content") ? json.get("content").getAsString() : mem.content;
                            String emo = json.has("emotionTag") ? json.get("emotionTag").getAsString() : "普通";
                            int conf = json.has("confidence") ? json.get("confidence").getAsInt() : 7;
                            String ki = json.has("keyItem") && !json.get("keyItem").getAsString().isEmpty()
                                ? json.get("keyItem").getAsString()
                                : MemoryManager.genKeyItem(fact);
                            long nodeId = memMgr.addUserProfileNode(mem.characterId, mainPersonaName, cat, ki, fact, emo, conf);
                            UserProfileNode created = db.userProfileNodeDao().getByIdSync((int) nodeId);
                            if (created != null) {
                                created.lastUpdated = mem.timestamp;
                                db.userProfileNodeDao().update(created);
                            }
                            profilesCreated++;
                            success = true;
                        } else {
                            failedCount++;
                        }
                    } else {
                        String episodesJson = migrateNormalMemory(aiManager, url, apiKey, model, mem, charName, charPersona, relInfo, wbCtx);
                        if (episodesJson != null) {
                            JsonObject root = gson.fromJson(episodesJson, JsonObject.class);
                            JsonArray items;
                            if (root.has("memories")) {
                                items = root.getAsJsonArray("memories");
                            } else {
                                // Single entry fallback
                                items = new JsonArray();
                                items.add(root);
                            }
                            String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                .format(new java.util.Date(mem.timestamp));
                            for (JsonElement el : items) {
                                JsonObject item = el.getAsJsonObject();
                                String title = item.has("title") ? item.get("title").getAsString() : "";
                                String recall = item.has("content") ? item.get("content").getAsString() : mem.content;
                                String emo = item.has("emotionalTone") ? item.get("emotionalTone").getAsString() : "平静";
                                int imp = item.has("importanceLevel") ? item.get("importanceLevel").getAsInt() : 2;
                                String tags = item.has("keywords") ? item.get("keywords").getAsString() : "";
                                long epId = memMgr.addEpisodicMemory(mem.characterId, mainPersonaName, dateStr, title, recall, tags, emo, imp, "");
                                EpisodicMemory createdEp = db.episodicMemoryDao().getByIdSync((int) epId);
                                if (createdEp != null) {
                                    createdEp.createdAt = mem.timestamp;
                                    db.episodicMemoryDao().update(createdEp);
                                }
                                episodesCreated++;
                            }
                            success = true;
                        } else {
                            failedCount++;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    failedCount++;
                }

                // Only advance progress cursor on success
                if (success) {
                    SpUtils.putInt("MEMORY_MIGRATION_LAST_ID_" + characterId, mem.id);
                }

                if (callback != null) {
                    String itemDesc = mem.content != null && mem.content.length() > 30
                        ? mem.content.substring(0, 30) + "..." : (mem.content != null ? mem.content : "");
                    callback.onProgress(i + 1, total, itemDesc);
                }

                if ((i + 1) % 5 == 0 && i < total - 1) {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
            }

            if (failedCount == 0) {
                SpUtils.putBoolean("MEMORY_V2_MIGRATED", true);
                SpUtils.putInt("MEMORY_MIGRATION_LAST_ID_" + characterId, -1);
            }
            isRunning = false;

            if (callback != null) {
                callback.onComplete(profilesCreated, episodesCreated, failedCount);
            }
        });
    }

    private String migrateImportantMemory(OpenAIManager aiManager, String url, String apiKey,
                                           String model, Memory memory, String charName, String charPersona,
                                           String relationshipInfo, String worldbookContext) throws Exception {
        OpenAiRequest request = new OpenAiRequest();
        request.model = model;
        request.temperature = 0.3f;
        request.messages = new java.util.ArrayList<>();

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是").append(charName).append("。");
        if (!charPersona.isEmpty()) {
            prompt.append("你的人设是：").append(charPersona).append("。");
        }
        prompt.append("\n");
        if (worldbookContext != null && !worldbookContext.isEmpty()) {
            prompt.append("\n关于你的世界的设定：\n").append(worldbookContext).append("\n");
        }
        if (relationshipInfo != null && !relationshipInfo.isEmpty()) {
            prompt.append("\n你的人际关系：\n").append(relationshipInfo).append("\n");
        }
        prompt.append("\n")
             .append("下面是一条关于用户的信息。请你站在自己的角度，将其整理为一条结构化的用户认知：\n")
             .append("原始分类: ").append(memory.category != null ? memory.category : "未知").append("\n")
             .append("原始内容: ").append(memory.content).append("\n\n")
             .append("请返回纯JSON（不要markdown标记）：\n")
             .append("{\n")
             .append("  \"category\": \"分类（档案/作息/饮食/健康/防线/人际/目标/日常）\",\n")
             .append("  \"keyItem\": \"画像条目的简短名称（如：喜欢的食物、熬夜频率、她的职业）\",\n")
             .append("  \"content\": \"你对用户此事的认知描述\",\n")
             .append("  \"emotionTag\": \"你对此事的情感（宠溺/心疼/无奈/好奇/普通/开心/担心）\",\n")
             .append("  \"confidence\": 确信度1-10,\n")
             .append("  \"keywords\": \"逗号分隔的搜索关键词\"\n")
             .append("}");

        request.messages.add(new OpenAiRequest.Message("user", prompt.toString()));

        Response<OpenAiResponse> response = aiManager.getApi()
            .createChatCompletion(url, "Bearer " + apiKey, request).execute();

        if (response.isSuccessful() && response.body() != null
            && response.body().choices != null && !response.body().choices.isEmpty()) {
            return cleanJsonResponse(response.body().choices.get(0).message.content.trim());
        }
        return null;
    }

    private String migrateNormalMemory(OpenAIManager aiManager, String url, String apiKey,
                                        String model, Memory memory, String charName, String charPersona,
                                        String relationshipInfo, String worldbookContext) throws Exception {
        OpenAiRequest request = new OpenAiRequest();
        request.model = model;
        request.temperature = 0.3f;
        request.messages = new java.util.ArrayList<>();

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是").append(charName).append("。");
        if (!charPersona.isEmpty()) {
            prompt.append("你的人设是：").append(charPersona).append("。");
        }
        prompt.append("\n");
        if (worldbookContext != null && !worldbookContext.isEmpty()) {
            prompt.append("\n关于你的世界的设定：\n").append(worldbookContext).append("\n");
        }
        if (relationshipInfo != null && !relationshipInfo.isEmpty()) {
            prompt.append("\n你的人际关系：\n").append(relationshipInfo).append("\n");
        }
        prompt.append("\n")
             .append("下面是一段你的过往经历摘要。请你用自己的视角，像人回忆往事一样，\n")
             .append("写出你对这段经历中若干具体事件的回忆。\n\n")
             .append("原始内容: ").append(memory.content).append("\n\n")
             .append("要求：\n")
             .append("1. 用第一人称（\"我\"），像在脑海里翻找记忆碎片一样自然地叙述\n")
             .append("2. 每条回忆只写一件事或一个印象，聚焦你的感受和当时的细节\n")
             .append("3. 不要写成完整的日记流水账，而是片段式的、有情绪温度的追忆\n")
             .append("4. 根据原始内容中涉及的事件数量，决定输出几条回忆（少则1条，多则5条）\n")
             .append("5. 每条回忆不超过150字，语气应符合你的人设\n\n")
             .append("请返回纯JSON（不要markdown标记）：\n")
             .append("{\n")
             .append("  \"memories\": [\n")
             .append("    {\n")
             .append("      \"title\": \"简短标题（10字内）\",\n")
             .append("      \"content\": \"像回忆片段一样的第一人称叙述（150字内）\",\n")
             .append("      \"keywords\": \"逗号分隔的关键词\",\n")
             .append("      \"emotionalTone\": \"心疼/开心/感动/难过/生气/担心/温暖/愧疚/好奇/平静\",\n")
             .append("      \"importanceLevel\": 重要性1-5\n")
             .append("    }\n")
             .append("  ]\n")
             .append("}");

        request.messages.add(new OpenAiRequest.Message("user", prompt.toString()));

        Response<OpenAiResponse> response = aiManager.getApi()
            .createChatCompletion(url, "Bearer " + apiKey, request).execute();

        if (response.isSuccessful() && response.body() != null
            && response.body().choices != null && !response.body().choices.isEmpty()) {
            return cleanJsonResponse(response.body().choices.get(0).message.content.trim());
        }
        return null;
    }

    private String cleanJsonResponse(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim();
        if (cleaned.contains("```json")) {
            int start = cleaned.indexOf("```json") + 7;
            int end = cleaned.lastIndexOf("```");
            if (end > start) cleaned = cleaned.substring(start, end).trim();
        } else if (cleaned.contains("```")) {
            int start = cleaned.indexOf("```") + 3;
            int end = cleaned.lastIndexOf("```");
            if (end > start) cleaned = cleaned.substring(start, end).trim();
        }
        return cleaned;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
