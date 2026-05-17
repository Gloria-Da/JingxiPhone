package com.yoyo.jingxi.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.Moment;
import com.yoyo.jingxi.data.entity.RelationshipEdge;
import com.yoyo.jingxi.data.entity.RelationshipNode;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;
import com.yoyo.jingxi.utils.ImageGenerationManager;
import com.yoyo.jingxi.utils.MomentNotificationManager;
import com.yoyo.jingxi.utils.MomentSimulator;
import com.yoyo.jingxi.utils.SpUtils;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import retrofit2.Response;

public class AutoMomentWorker extends Worker {

    private static final String TAG = "AutoMomentWorker";

    public AutoMomentWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "doWork: Checking if AI should send an auto moment...");

        if (!SpUtils.getBoolean("RELATIONSHIP_NETWORK_ENABLED", true)) {
            Log.d(TAG, "Relationship network disabled, skipping auto moment.");
            return Result.success();
        }

        boolean autoMomentEnabled = SpUtils.getBoolean("AUTO_MOMENT_ENABLED", false);
        if (!autoMomentEnabled) {
            Log.d(TAG, "Auto moment is disabled globally.");
            return Result.success();
        }

        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        List<Character> allCharacters = db.characterDao().getAllCharactersSync();
        if (allCharacters == null || allCharacters.isEmpty()) {
            return Result.success();
        }

        List<Character> mutableCharacters = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (Character character : allCharacters) {
            boolean isAutoMomentEnabled = SpUtils.getBoolean("AUTO_MOMENT_ENABLED_" + character.id, false);
            if (!isAutoMomentEnabled) continue;
            
            // 如果总开关被关闭，但由于某些原因worker还在运行，确保不再发动态
            if (!autoMomentEnabled) continue;

            float intervalHours = SpUtils.getFloat("AUTO_MOMENT_INTERVAL_" + character.id, 8.0f);
            long intervalMillis = (long) (intervalHours * 60 * 60 * 1000L);
            
            String startTimeStr = SpUtils.getString("AUTO_MOMENT_START_" + character.id, "08:00");
            String endTimeStr = SpUtils.getString("AUTO_MOMENT_END_" + character.id, "22:00");
            
            if (!isTimeInRange(startTimeStr, endTimeStr)) {
                Log.d(TAG, "Current time is outside the allowed auto moment time window for character " + character.id + ". Window: " + startTimeStr + " - " + endTimeStr);
                continue;
            }

            // 检查该角色距离上次发动态的时间
            long lastMomentTime = SpUtils.getLong("LAST_AUTO_MOMENT_TIME_" + character.id, 0L);
            if (currentTime - lastMomentTime < intervalMillis) {
                Log.d(TAG, "Too soon for another auto moment for character " + character.id + ". Needs " + (intervalMillis/1000/60) + " mins, passed " + ((currentTime - lastMomentTime)/1000/60) + " mins.");
                continue;
            }

            mutableCharacters.add(character);
        }

        if (mutableCharacters.isEmpty()) {
            return Result.success();
        }

        OpenAIManager aiManager = new OpenAIManager();
        String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
        String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
        String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");

        if (apiKey.isEmpty()) return Result.failure();
        if (!endpoint.endsWith("/")) endpoint += "/";

        // 获取关于人际关系
        List<RelationshipNode> nodes = db.relationshipNodeDao().getAllNodesSync();
        List<RelationshipEdge> edges = db.relationshipEdgeDao().getAllEdgesSync();

        StringBuilder relBuilder = new StringBuilder();
        if (nodes != null && edges != null) {
            for (RelationshipEdge edge : edges) {
                RelationshipNode source = null;
                RelationshipNode target = null;
                for (RelationshipNode n : nodes) {
                    if (n.id.equals(edge.sourceNodeId)) source = n;
                    if (n.id.equals(edge.targetNodeId)) target = n;
                }
                if (source != null && target != null) {
                    relBuilder.append("- ").append(source.name).append(" -> ").append(target.name).append(" : ").append(edge.relation).append("\n");
                }
            }
        }
        String relationshipContent = relBuilder.toString();
        
        // 最近朋友圈
        List<Moment> recentMoments = db.momentDao().getRecentMomentsSync(10);
        StringBuilder momentsContext = new StringBuilder();
        if (recentMoments != null && !recentMoments.isEmpty()) {
            momentsContext.append("最近大家发的朋友圈（供参考）：\n");
            for (Moment m : recentMoments) {
                momentsContext.append(m.publisherName).append(": ").append(m.content).append("\n");
            }
        }
        
        // 预先获取聊天记录 (如果有最近一次聊天的 session)
        List<com.yoyo.jingxi.data.entity.ChatSession> sessions = db.chatSessionDao().getAllSessionsSync();

        for (Character character : mutableCharacters) {
        // 我们不再像以前一样直接发，而是先请求 AI 的意愿
        try {
            // 获取各种上下文信息
            String scheduleContent = SpUtils.getString("SCHEDULE_CONTENT_" + character.id, "");
            
            // 记忆
            int memoryCallCount = SpUtils.getInt("SETTING_MEMORY_CALL_COUNT", 20);
            List<com.yoyo.jingxi.data.entity.Memory> importantMemories = db.memoryDao().getImportantMemoriesSync(character.id);
            List<com.yoyo.jingxi.data.entity.Memory> normalMemories = memoryCallCount > 0 ? 
                db.memoryDao().getNormalMemoriesSync(character.id, memoryCallCount) : 
                db.memoryDao().getAllNormalMemoriesSync(character.id);
                
            StringBuilder memoryContext = new StringBuilder();
            if (importantMemories != null && !importantMemories.isEmpty()) {
                memoryContext.append("重要记忆：\n");
                for (com.yoyo.jingxi.data.entity.Memory mem : importantMemories) memoryContext.append("- ").append(mem.content).append("\n");
            }
            if (normalMemories != null && !normalMemories.isEmpty()) {
                memoryContext.append("普通记忆：\n");
                for (com.yoyo.jingxi.data.entity.Memory mem : normalMemories) memoryContext.append("- ").append(mem.content).append("\n");
            }

            // 聊天记录
            StringBuilder chatContext = new StringBuilder();
            if (sessions != null) {
                for (com.yoyo.jingxi.data.entity.ChatSession s : sessions) {
                    if (s.characterId == character.id) {
                        int historyRounds = SpUtils.getInt("SETTING_HISTORY_ROUNDS", 80);
                        List<com.yoyo.jingxi.data.entity.Message> history = db.messageDao().getRecentMessagesBySessionIdSync(s.id, historyRounds * 2);
                        java.util.Collections.reverse(history);
                        if (history != null && !history.isEmpty()) {
                            chatContext.append("最近跟 ").append(s.myPersonaName).append(" 的聊天记录：\n");
                            for (com.yoyo.jingxi.data.entity.Message msg : history) {
                                if (msg.type == 99 || msg.type == 100) continue;
                                String sender = msg.isFromUser ? s.myPersonaName : character.name;
                                chatContext.append(sender).append(": ").append(msg.content).append("\n");
                            }
                        }
                        break;
                    }
                }
            }

            OpenAiRequest request = new OpenAiRequest();
            request.model = model;
            request.messages = new ArrayList<>();

            StringBuilder systemPrompt = new StringBuilder();
            systemPrompt.append("你现在正在扮演: ").append(character.name).append("\n");
            systemPrompt.append("你的人设: ").append(character.persona).append("\n\n");
            
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            systemPrompt.append("当前时间是: ").append(sdf.format(new java.util.Date())).append("\n\n");

            if (!scheduleContent.isEmpty()) systemPrompt.append("你今天的日程/状态：\n").append(scheduleContent).append("\n\n");
            if (memoryContext.length() > 0) systemPrompt.append(memoryContext).append("\n");
            if (chatContext.length() > 0) systemPrompt.append(chatContext).append("\n");
            if (momentsContext.length() > 0) systemPrompt.append(momentsContext).append("\n");

            if (!relationshipContent.isEmpty()) {
                systemPrompt.append("你的人际关系: \n").append(relationshipContent).append("\n\n");
            }
            systemPrompt.append("【重要关系设定】：如果没有在上述人际关系中明确说明你与其他角色的关系，则默认你们仅为**普通的网络好友（网友）**。\n\n");

            systemPrompt.append("请根据上述所有背景信息（人设、日程、记忆、最近的聊天记录、大家最近的朋友圈等），决定此刻你是否**想**发一条朋友圈。\n")
                    .append("如果是你在忙碌、睡觉的时间，或者当前没有任何你想分享的感悟、吐槽和趣事，请选择不发。只有在自然的情况下，符合你的性格，你才选择发。\n")
                    .append("【绝对人设优先】：你所有的表达必须完全受限于你的核心人设。高冷的人绝不发大段文字或滥用表情，活泼的人才会带有网感。\n")
                    .append("【生活现场感】：分享微小、普通的瞬间，使用第一人称现场感描述，坚决不要事后汇报。\n")
                    .append("【内容方向】：日常吐槽、碎碎念、分享瞬间或发疯文学，拒绝正能量说教、拒绝流水账。\n")
                    .append("【极度关键】：动态的文本内容（content）**绝对不能**包含任何提示图片的字眼。描述图片的东西，**全部且只能**放在 `image_desc` 字段中。\n")
                    .append("如果这条动态适合配图（0-9张），请在 `image_desc` 字段中详细描述这些虚拟图片的内容（像Midjourney提示词），多张图片用逗号分隔。**绝对不要在描述中出现角色、人物等内容**，只描述静物、风景或环境。如果不配图，该字段留空。\n\n")
                    .append("你必须严格返回以下 JSON 格式：\n")
                    .append("{\n")
                    .append("  \"should_send\": true/false,\n")
                    .append("  \"reason\": \"你决定发或不发的心理活动/原因\",\n")
                    .append("  \"content\": \"你发布的朋友圈内容(如果should_send为false则留空)\",\n")
                    .append("  \"image_desc\": \"图片描述，多张图片用逗号分隔。没有图片请填空字符串\"\n")
                    .append("}\n");

            request.messages.add(new OpenAiRequest.Message("user", systemPrompt.toString()));

            Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(endpoint + "v1/chat/completions", "Bearer " + apiKey, request).execute();

            if (response.isSuccessful() && response.body() != null && !response.body().choices.isEmpty()) {
                String content = response.body().choices.get(0).message.content;

                // 清理 markdown
                if (content.startsWith("```json")) content = content.substring(7);
                else if (content.startsWith("```")) content = content.substring(3);
                if (content.endsWith("```")) content = content.substring(0, content.length() - 3);
                content = content.trim();

                try {
                    JSONObject json = new JSONObject(content);
                    boolean shouldSend = json.optBoolean("should_send", false);
                    String momentContent = json.optString("content", "");
                    String imageDesc = json.optString("image_desc", "");

                    if (shouldSend && !momentContent.isEmpty()) {
                        SpUtils.putLong("LAST_AUTO_MOMENT_TIME_" + character.id, currentTime); // 只有真发了才更新时间

                        Moment aiMoment = new Moment();
                        aiMoment.publisherType = 1;
                        aiMoment.publisherId = String.valueOf(character.id);
                        aiMoment.publisherName = character.name;
                        aiMoment.content = momentContent;

                        if (imageDesc != null && !imageDesc.trim().isEmpty()) {
                            StringBuilder urls = new StringBuilder();
                            String[] descs = imageDesc.split(",");
                            for (String desc : descs) {
                                desc = desc.trim();
                                if (!desc.isEmpty()) {
                                    if (urls.length() > 0) urls.append(",");
                                    // 处理可能的转义字符问题
                                    String safeDesc = desc.replace("\"", "\\\"").replace("\n", " ").replace("\\", "\\\\");
                                    urls.append("virtual://{\"desc\":\"").append(safeDesc).append("\"}");
                                }
                            }
                            if (urls.length() > 0) {
                                aiMoment.imageUrl = urls.toString();
                            }
                        }

                        // 稍微减去一点时间
                        long randomDelay = (long) (Math.random() * 5 * 1000) + 1000;
                        aiMoment.timestamp = System.currentTimeMillis() - randomDelay;

                        long momentId = db.momentDao().insert(aiMoment);
                        aiMoment.id = (int) momentId;

                        if (momentContent.contains("@")) {
                            MomentNotificationManager.processMentions(
                                    db, aiMoment.id, 1, String.valueOf(character.id), character.name, character.avatarPath, momentContent);
                        }

                        // 自动模拟点赞和评论
                        MomentSimulator.simulateInteraction(getApplicationContext(), aiMoment);

                        // 直接调用，避免在 Android 12+ 后台启动前台服务引发 ForegroundServiceStartNotAllowedException
                        ImageGenerationManager.getInstance().checkAndGenerateImages(aiMoment);

                        // 通知 UI 更新
                        android.content.Intent intent = new android.content.Intent("com.yoyo.jingxi.ACTION_MOMENT_UPDATED");
                        getApplicationContext().sendBroadcast(intent);

                        Log.d(TAG, "Successfully generated AI moment via worker: " + momentContent);
                    } else {
                        Log.d(TAG, "AI decided NOT to send moment. Reason: " + json.optString("reason", ""));
                        // 这里可以考虑将时间往后延，比如半小时后再问一次，而不是等待一个完整的interval，或者什么都不做等15分钟Worker再次执行
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing AI moment JSON", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in AutoMomentWorker", e);
        }
        } // end for
        return Result.success();
    }
    
    private boolean isTimeInRange(String startTime, String endTime) {
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            int currentMinute = cal.get(java.util.Calendar.MINUTE);
            int currentMins = currentHour * 60 + currentMinute;
            
            String[] startParts = startTime.split(":");
            int startMins = Integer.parseInt(startParts[0]) * 60 + Integer.parseInt(startParts[1]);
            
            String[] endParts = endTime.split(":");
            int endMins = Integer.parseInt(endParts[0]) * 60 + Integer.parseInt(endParts[1]);
            
            if (startMins <= endMins) {
                return currentMins >= startMins && currentMins <= endMins;
            } else {
                // 跨天情况 (比如 22:00 到 08:00)
                return currentMins >= startMins || currentMins <= endMins;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return true; // 解析失败默认放行
        }
    }
}
