package com.yoyo.jingxi.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.Memo;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.yoyo.jingxi.data.entity.ScheduleEntry;

public class PlanExtractionManager {
    private static final String TAG = "PlanExtractionManager";
    private static final Gson gson = new Gson();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final OpenAIManager aiManager = new OpenAIManager();

    public interface ExtractionCallback {
        void onComplete();
    }

    public static void extractPlansFromRecentChats(AppDatabase db, Character character, ExtractionCallback callback) {
        if (character == null) {
            if (callback != null) callback.onComplete();
            return;
        }

        new Thread(() -> {
            // Also attempt relationship extraction
            // We removed extractRelationshipsFromChat method to make relationship extraction explicit and standalone
            // RelationshipExtractionManager.extractRelationshipsFromChat(character, null);
            
            // 尝试直接检查并生成今日日程（避免没点进日程页面就不生成的情况）
            ScheduleManager.checkAndAutoGenerate(character, db, null);

            long lastExtractionTime = SpUtils.getLong("LAST_PLAN_EXTRACTION_TIME_" + character.id, 0);
            long currentTime = System.currentTimeMillis();

            // Get messages since last extraction (max 50 to avoid huge context)
            List<Message> recentMessages = db.messageDao().getMessagesSince(character.id, lastExtractionTime);
            if (recentMessages.size() > 50) {
                recentMessages = recentMessages.subList(recentMessages.size() - 50, recentMessages.size());
            }

            if (recentMessages.isEmpty()) {
                // No new messages to process
                if (callback != null) mainHandler.post(callback::onComplete);
                return;
            }

            StringBuilder chatLog = new StringBuilder();
            for (Message msg : recentMessages) {
                String sender = msg.isFromUser ? "我" : character.name;
                chatLog.append(sender).append("：").append(msg.content).append("\n");
            }

            // 无论后续提取成功与否，只要处理了这批消息，就把时间更新，防止一直重复处理
            SpUtils.putLong("LAST_PLAN_EXTRACTION_TIME_" + character.id, currentTime);

            String currentDateTimeStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
            String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
            String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");

            if (TextUtils.isEmpty(apiKey)) {
                if (callback != null) mainHandler.post(callback::onComplete);
                return;
            }
            if (!endpoint.endsWith("/")) endpoint += "/";
            String finalUrl = endpoint + "v1/chat/completions";

            String prompt = "现在的真实系统日期是：" + currentDateTimeStr + "。\n" +
                    "请分析以下聊天记录，提取出其中提到的**属于对方（" + character.name + "）未来的明确计划、安排或约定**。\n" +
                    "聊天记录：\n" + chatLog.toString() + "\n\n" +
                    "要求：\n" +
                    "1. 只提取对方的日程安排或我们之间的约定，不要提取过去的事件。\n" +
                    "2. 必须推算出准确的具体日期（格式必须为 yyyy-MM-dd）。\n" +
                    "3. 如果没有发现任何未来的计划，请返回空的 JSON 数组 `[]`。\n" +
                    "4. 请严格遵守以下 JSON 数组格式，不要带任何 Markdown 标记：\n" +
                    "[\n" +
                    "  {\n" +
                    "    \"date\": \"2024-04-25\",\n" +
                    "    \"content\": \"下午和玩家一起去看画展\"\n" +
                    "  }\n" +
                    "]";

        OpenAiRequest request = new OpenAiRequest();
        request.model = model;
        request.temperature = SpUtils.getFloat("API_TEMPERATURE", 0.8f);
        request.messages = new ArrayList<>();
        request.messages.add(new OpenAiRequest.Message("user", prompt));

        try {
            Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(finalUrl, "Bearer " + apiKey, request).execute();
                if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty() && response.body().choices.get(0).message != null && response.body().choices.get(0).message.content != null) {
                    String jsonResponse = response.body().choices.get(0).message.content.trim();
                    jsonResponse = cleanJsonResponse(jsonResponse);
                    
                    try {
                        JsonArray plansArray = gson.fromJson(jsonResponse, JsonArray.class);
                        for (JsonElement element : plansArray) {
                            JsonObject planObj = element.getAsJsonObject();
                            if (planObj.has("date") && planObj.has("content")) {
                                String date = planObj.get("date").getAsString();
                                String content = planObj.get("content").getAsString();
                                
                                Memo memo = new Memo();
                                memo.characterId = character.id;
                                memo.targetDate = date;
                              memo.timestamp = System.currentTimeMillis();
                                memo.createdAt = System.currentTimeMillis();
                                
                                db.memoDao().insert(memo);
                                Log.d(TAG, "Extracted plan: " + content + " for " + date);
                            }
                        }
                        
                    } catch (JsonSyntaxException e) {
                        Log.e(TAG, "Failed to parse plans JSON", e);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Network error during plan extraction", e);
            }

            // 第三阶段: 同时尝试从聊天记录生成动态
            // 解耦：无论日程提取是否成功，都尝试分析聊天记录判断是否发动态
            generateMomentFromChat(db, character, chatLog.toString());

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        }).start();
    }

    private static void generateMomentFromChat(AppDatabase db, Character character, String chatLog) {
        String prompt = "请分析以下聊天记录，判断角色（" + character.name + "）当前是否有发朋友圈（动态）的欲望。\n" +
                "聊天记录：\n" + chatLog + "\n\n" +
                "角色人设：\n" + character.persona + "\n\n" +
                "【绝对人设优先】：你所有的表达必须完全受限于你的核心人设。不要表现出“AI式热情”，高冷人设务必保持高冷、字数极简。\n" +
                "要求：\n" +
                "1. 如果玩家在聊天中明确要求角色发动态，或者角色在聊天中有强烈的表达欲或分享欲（如经历了情绪波动：吐槽、惊喜、抱怨，或发生了值得分享的事情），should_post 设为 true，并在 reason 中写明判断理由。\n" +
                "2. 如果只是普通闲聊，没有明显情绪起伏或分享欲，设为 false。\n" +
                "3. 明确身份：这是你在个人朋友圈的公开广播，**绝对不能**像在和玩家一对一说话，不能在动态里直接对玩家喊话或提问。\n" +
                "4. 【现场感与活人感】：如果发动态，内容必须是第一人称现场感描述。要求极简、无头无尾、想到哪写哪。**绝对不要像写日记一样交代前因后果**，不要生硬的总结报告！允许不用标点或用空格断句。避免使用“今天我…”、“刚刚我…”来汇报事情，而是直接抛出当下的情绪或状态（例如：“困死我了”、“这排队排到哪年”、“什么鬼天气”）。\n" +
                "5. 大部分情况下的动态不需要@任何人，非极特殊情况坚决不用@，不要把@当作普通称呼。不要为了@而@，如果必须@，请使用“@名字 ”的格式。\n" +
                "6. 如果内容适合配图，可以在 image_desc 中详细描述这些虚拟图片的内容。要求：必须非常详细，描绘出画面主体、色彩、光影、构图、背景等视觉细节，就像是一段精美的Midjourney提示词，多张图片用逗号分隔（例如：“一杯热气腾腾的黑咖啡放在木质桌面上，旁边是一本翻开的旧书，阳光透过窗户洒在桌面上，形成温暖的丁达尔光影，整体色调偏暖，电影级构图”）。要求图片风格日常，像是朋友圈配图或随手拍来分享的图。**绝对不要在描述中出现角色、人物等内容**，只描述静物、风景或环境。如果不配图，则设为空字符串。\n" +
                "7. 必须严格遵守以下 JSON 格式，不要包含 Markdown 标记：\n" +
                "{\n" +
                "  \"should_post\": true/false,\n" +
                "  \"reason\": \"判断发或不发动态的原因分析\",\n" +
                "  \"content\": \"想发的内容（如果不发则为空）\",\n" +
                "  \"image_desc\": \"配图描述（如果不配图则为空）\"\n" +
                "}\n";

        OpenAiRequest request = new OpenAiRequest();
        request.model = SpUtils.getString("API_MODEL", "gpt-4o-mini");
        request.temperature = SpUtils.getFloat("API_TEMPERATURE", 0.8f);
        request.messages = new ArrayList<>();
        request.messages.add(new OpenAiRequest.Message("user", prompt));

        try {
            String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
            String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
            if (!endpoint.endsWith("/")) endpoint += "/";
            String finalUrl = endpoint + "v1/chat/completions";

            Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(finalUrl, "Bearer " + apiKey, request).execute();
            if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty() && response.body().choices.get(0).message != null && response.body().choices.get(0).message.content != null) {
                String jsonResponse = response.body().choices.get(0).message.content.trim();
                Log.d(TAG, "Moment generation API raw response: " + jsonResponse);
                jsonResponse = cleanJsonResponse(jsonResponse);
                
                try {
                    JsonObject resultObj = gson.fromJson(jsonResponse, JsonObject.class);
                    boolean shouldPost = resultObj.has("should_post") && resultObj.get("should_post").getAsBoolean();
                    String reason = resultObj.has("reason") ? resultObj.get("reason").getAsString() : "";
                    String content = resultObj.has("content") ? resultObj.get("content").getAsString() : "";
                    String imageDesc = resultObj.has("image_desc") ? resultObj.get("image_desc").getAsString() : "";
                    Log.d(TAG, "Parsed moment decision: shouldPost=" + shouldPost + ", reason=" + reason + ", content=" + content + ", imageDesc=" + imageDesc);
                    
                    if (shouldPost && !TextUtils.isEmpty(content)) {
                        com.yoyo.jingxi.data.entity.Moment moment = new com.yoyo.jingxi.data.entity.Moment();
                        moment.publisherType = 1; // 1 for Character
                        moment.publisherId = String.valueOf(character.id);
                        moment.publisherName = character.name;
                        moment.publisherAvatar = character.avatarPath;
                        moment.content = content;
                        
                        if (!TextUtils.isEmpty(imageDesc)) {
                            List<String> imageUrls = new ArrayList<>();
                            // 构造完整的 json 作为描述，以兼容 ImageViewHolder 的解析逻辑
                            JsonObject descJson = new JsonObject();
                            descJson.addProperty("desc", imageDesc);
                            imageUrls.add("virtual://" + android.net.Uri.encode(descJson.toString()));
                            moment.imageUrl = String.join(",", imageUrls); // 使用逗号分隔，保持与 AddMomentActivity 一致
                        } else {
                            moment.imageUrl = "";
                        }
                        // 直接使用当前时间减去极少误差，不使用较长的随机延迟，以确保不过滤
                        moment.timestamp = System.currentTimeMillis() - 1000;
                        long rowId = db.momentDao().insert(moment);
                        moment.id = (int) rowId;
                        Log.d(TAG, "Generated moment from chat: " + content + " (ID: " + moment.id + ")");
                        
                        // 解析动态内容中是否提到了某人 (触发 @ 逻辑)
                        if (content.contains("@")) {
                            com.yoyo.jingxi.utils.MomentNotificationManager.processMentions(
                                db, moment.id, 1, String.valueOf(character.id), character.name, character.avatarPath, content
                            );
                        }

                        // 模拟互动
                        com.yoyo.jingxi.utils.MomentSimulator.simulateInteraction(db, moment);
                com.yoyo.jingxi.utils.ImageGenerationManager.getInstance().checkAndGenerateImages(moment);
                        
                        // 发送广播通知刷新
                        mainHandler.postDelayed(() -> {
                            try {
                                // JingxiApplication doesn't exist yet, we will broadcast via a known context if we can get it,
                                // but for now, rely on MomentsFragment observer, which will pick it up automatically 
                                // since we're inserting into Room DB. Let's just log here.
                                Log.d(TAG, "Moment generated and saved to DB");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, 500); // 给模拟器一点点时间先插入点赞和评论
                    }
                    
                    // 尝试判断是否要点赞/评论别人的动态
                    evaluateInteractionWithOthers(db, character, chatLog, apiKey, endpoint);

                } catch (JsonSyntaxException e) {
                    Log.e(TAG, "Failed to parse moment generation JSON", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating moment", e);
        }
    }

    private static void evaluateInteractionWithOthers(AppDatabase db, Character character, String chatLog, String apiKey, String endpoint) {
        // Find recent moments not published by this character
        List<com.yoyo.jingxi.data.entity.Moment> recentMoments = db.momentDao().getVisibleMomentsSync(System.currentTimeMillis() + 60000);
        if (recentMoments == null || recentMoments.isEmpty()) return;

        List<com.yoyo.jingxi.data.entity.Moment> othersMoments = new ArrayList<>();
        for (com.yoyo.jingxi.data.entity.Moment m : recentMoments) {
            if (m.publisherType != 1 || !m.publisherId.equals(String.valueOf(character.id))) {
                othersMoments.add(m);
            }
        }
        
        if (othersMoments.isEmpty()) return;
        
        // Take the latest one
        com.yoyo.jingxi.data.entity.Moment latestMoment = othersMoments.get(0);
        
        // Already liked?
        com.yoyo.jingxi.data.entity.MomentLike existingLike = db.momentLikeDao().getLikeByLiker(latestMoment.id, character.name);
        if (existingLike != null) return; // For simplicity, if liked, skip further interaction processing

        String prompt = "请分析以下聊天记录，判断角色（" + character.name + "）看到下面这条朋友圈动态后，是否会去点赞或评论。\n" +
                "聊天记录（可能有相关暗示）：\n" + chatLog + "\n\n" +
                "角色人设：\n" + character.persona + "\n\n" +
                "【重要关系设定】：除非角色设定或聊天记录中明确说明，否则默认你与动态发布者仅为**普通的网络好友（网友）**，绝对不会在现实中互相约出门、一起游玩，也不会在动态里互相调侃或显得很熟络。\n\n" +
                "朋友圈动态：\n发布者：" + latestMoment.publisherName + "\n内容：" + latestMoment.content + "\n\n" +
                "要求：\n" +
                "1. 【绝对人设优先】：明确当前扮演的人设，不可错认角色。所有的表达必须完全受限于你的核心人设。高冷的人绝不发长句，活泼的人才可以发梗。\n" +
                "2. [断句规则：一口气说得完的话不加逗号。主谓之间、动宾之间不无故断开。\"的地得\"和介词前不断句。紧张时短句用句号而非逗号。日常对话允许长句一气呵成。断句的疏密跟随情绪，不做机械等距切割。]\n" +
                "3. 如果聊天中玩家明确暗示（比如“快去给我点赞”），或者动态内容极其符合该角色的兴趣/人设，导致他有互动欲望，should_like / should_comment 设为 true。\n" +
                "4. 这是一个真实的社交网络，如果你决定评论，必须像活人刷朋友圈一样。极简，不要像客服一样长篇大论。高冷的人可以只回一个“？”或“嗯”，熟人可以互怼。严禁说教和过度关心。绝不是对发布者的私聊。\n" +
                "5. 大部分情况下的评论不需要@任何人，非极特殊情况坚决不用@，不要把@当作普通称呼。不要为了@而@，如果必须@，请使用“@名字 ”的格式。\n" +
                "6. 如果只是一般的动态，没有特别的触动，请保持克制，不要频繁互动，should_like / should_comment 设为 false。\n" +
                "7. 必须严格遵守以下 JSON 格式，不要包含 Markdown 标记：\n" +
                "{\n" +
                "  \"should_like\": true/false,\n" +
                "  \"should_comment\": true/false,\n" +
                "  \"comment_content\": \"想评论的内容（如果不评论则为空）\"\n" +
                "}\n";

        OpenAiRequest request = new OpenAiRequest();
        request.model = SpUtils.getString("API_MODEL", "gpt-4o-mini");
        request.messages = new ArrayList<>();
        request.messages.add(new OpenAiRequest.Message("user", prompt));
        
        try {
            Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(endpoint + "v1/chat/completions", "Bearer " + apiKey, request).execute();
            if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty()) {
                String jsonResponse = response.body().choices.get(0).message.content.trim();
                jsonResponse = cleanJsonResponse(jsonResponse);
                
                JsonObject resultObj = gson.fromJson(jsonResponse, JsonObject.class);
                boolean shouldLike = resultObj.has("should_like") && resultObj.get("should_like").getAsBoolean();
                boolean shouldComment = resultObj.has("should_comment") && resultObj.get("should_comment").getAsBoolean();
                String commentContent = resultObj.has("comment_content") ? resultObj.get("comment_content").getAsString() : "";
                
                if (shouldLike) {
                    com.yoyo.jingxi.data.entity.MomentLike like = new com.yoyo.jingxi.data.entity.MomentLike();
                    like.momentId = latestMoment.id;
                    like.likerType = 1;
                    like.likerId = String.valueOf(character.id);
                    like.likerName = character.name;
                    like.timestamp = System.currentTimeMillis();
                    db.momentLikeDao().insert(like);
                }
                
                if (shouldComment && !TextUtils.isEmpty(commentContent)) {
                    com.yoyo.jingxi.data.entity.MomentComment comment = new com.yoyo.jingxi.data.entity.MomentComment();
                    comment.momentId = latestMoment.id;
                    comment.authorType = 1;
                    comment.authorId = String.valueOf(character.id);
                    comment.authorName = character.name;
                    comment.content = commentContent;
                    comment.timestamp = System.currentTimeMillis();
                    comment.replyToType = -1;
                    db.momentCommentDao().insert(comment);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error evaluating interaction", e);
        }
    }

    private static String cleanJsonResponse(String jsonResponse) {
        if (jsonResponse.contains("```json")) {
            int start = jsonResponse.indexOf("```json") + 7;
            int end = jsonResponse.lastIndexOf("```");
            if (end > start) {
                jsonResponse = jsonResponse.substring(start, end);
            }
        } else if (jsonResponse.contains("```")) {
            int start = jsonResponse.indexOf("```") + 3;
            int end = jsonResponse.lastIndexOf("```");
            if (end > start) {
                jsonResponse = jsonResponse.substring(start, end);
            }
        }
        return jsonResponse.trim();
    }
}