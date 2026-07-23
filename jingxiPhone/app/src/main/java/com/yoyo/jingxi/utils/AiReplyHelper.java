package com.yoyo.jingxi.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.ui.activity.ChatMainActivity;

import java.io.IOException;
import java.net.SocketTimeoutException;

public class AiReplyHelper {

    public static void requestAiReplySynchronous(Context context, int sessionId, int characterId, String autoReason) {
        requestAiReplySynchronous(context, sessionId, characterId, autoReason, 0);
    }

    public static void requestAiReplySynchronous(Context context, int sessionId, int characterId, String autoReason, int sharedContentId) {
        AppDatabase db = AppDatabase.getDatabase(context);
        OpenAIManager aiManager = new OpenAIManager();

        if (!isNetworkAvailable(context)) {
            insertErrorAndNotify(db, context, sessionId, characterId, "[系统提示: 当前无网络连接，请检查网络设置。]");
            broadcastReplyStatus(context, false);
            return;
        }

        try {
            Character character = db.characterDao().getCharacterById(characterId);
            
            if (character != null) {
                // Check and generate schedule before AI reply
                updateNotification(context, "正在为 " + character.name + " 规划今日日程...");
                try {
                    if (!com.yoyo.jingxi.utils.ScheduleManager.isScheduleGeneratedToday(character.id) &&
                        com.yoyo.jingxi.utils.SpUtils.getBoolean("SCHEDULE_ENABLED_" + character.id, false)) {
                        com.yoyo.jingxi.utils.ScheduleManager.generateScheduleSync(db, character);
                    }
                } catch (Exception e) {
                    e.printStackTrace(); // Log but don't block AI reply if schedule generation fails
                }
                updateNotification(context, "AI 正在思考回复...");
            }
            com.yoyo.jingxi.data.entity.ChatSession session = db.chatSessionDao().getSessionById(sessionId);
            if (character == null || session == null) {
                broadcastReplyStatus(context, false);
                return;
            }

            String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
            String tempEndpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
            String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");

            if (android.text.TextUtils.isEmpty(apiKey)) {
                return;
            }
            
            if (!tempEndpoint.endsWith("/")) {
                tempEndpoint += "/";
            }
            String finalUrl = tempEndpoint + "v1/chat/completions";
            
            int historyRounds = SpUtils.getInt("SETTING_HISTORY_ROUNDS", 80);
            java.util.List<com.yoyo.jingxi.data.entity.Message> history = db.messageDao().getRecentMessagesBySessionIdSync(sessionId, historyRounds * 2);
            java.util.Collections.reverse(history);

            // 多模态内容映射：messageId → (String or List<ContentPart>)
            java.util.Map<Integer, Object> richContentMap = new java.util.HashMap<>();

            // 预处理分享内容消息（type=7）：加载SharedContent并格式化内容为AI可理解的文本
            for (com.yoyo.jingxi.data.entity.Message msg : history) {
                if (msg.type == 7) {
                    com.yoyo.jingxi.data.entity.SharedContent sc = db.sharedContentDao().getByMessageId(msg.id);
                    if (sc == null) {
                        // 兜底：SharedContent 可能还未创建（时序问题），现场提取
                        String url = extractUrlFromText(msg.content);
                        if (url != null) {
                            com.yoyo.jingxi.utils.LinkMetadataExtractor.LinkMetadata meta =
                                    com.yoyo.jingxi.utils.LinkMetadataExtractor.extract(url);
                            sc = new com.yoyo.jingxi.data.entity.SharedContent();
                            sc.sourceUrl = url;
                            sc.siteName = meta.siteName;
                            sc.faviconUrl = meta.faviconUrl;
                            sc.contentTitle = meta.title;
                            sc.thumbnailUrl = meta.imageUrl;
                            sc.description = meta.description;
                            sc.timestamp = System.currentTimeMillis();
                            sc.sessionId = sessionId;
                            sc.characterId = characterId;
                            sc.messageId = msg.id;
                            try { sc.id = (int) db.sharedContentDao().insert(sc); } catch (Exception e) {}
                        }
                    }
                    if (sc != null) {
                        // 小红书占位数据兜底：当场爬取
                        boolean isXhsPlaceholder = (sc.fullText == null || sc.fullText.isEmpty())
                                && (sc.sourceUrl != null)
                                && (sc.sourceUrl.contains("xhslink.com")
                                    || sc.sourceUrl.contains("xhslink.cn")
                                    || sc.sourceUrl.contains("xiaohongshu.com"));
                        if (isXhsPlaceholder) {
                            com.yoyo.jingxi.utils.LinkMetadataExtractor.LinkMetadata xhsMeta =
                                    com.yoyo.jingxi.utils.XiaohongshuCrawler.doCrawl(sc.sourceUrl);
                            if (xhsMeta.title != null) sc.contentTitle = xhsMeta.title;
                            if (xhsMeta.fullText != null) sc.fullText = xhsMeta.fullText;
                            if (xhsMeta.description != null) sc.description = xhsMeta.description;
                            if (xhsMeta.imageUrls != null && !xhsMeta.imageUrls.isEmpty()) {
                                sc.imageUrlsJson = new com.google.gson.Gson().toJson(xhsMeta.imageUrls);
                            }
                            try { db.sharedContentDao().update(sc); } catch (Exception e) {}
                        }

                        java.util.List<String> imgUrls = parseImageUrls(sc.imageUrlsJson);
                        boolean hasFullContent = (sc.fullText != null && !sc.fullText.isEmpty())
                                || (imgUrls != null && !imgUrls.isEmpty());

                        if (hasFullContent) {
                            java.util.List<com.yoyo.jingxi.network.OpenAiRequest.ContentPart> parts =
                                    new java.util.ArrayList<>();

                            StringBuilder textPart = new StringBuilder();
                            textPart.append("[用户分享了来自")
                                    .append(sc.siteName != null ? sc.siteName : "网页")
                                    .append("的链接]\n");
                            textPart.append("标题: ")
                                    .append(sc.contentTitle != null ? sc.contentTitle : "无标题")
                                    .append("\n");
                            if (sc.fullText != null && !sc.fullText.isEmpty()) {
                                textPart.append("正文内容:\n").append(sc.fullText).append("\n");
                            } else if (sc.description != null && !sc.description.isEmpty()) {
                                textPart.append("描述: ").append(sc.description).append("\n");
                            }
                            textPart.append("链接: ").append(sc.sourceUrl).append("\n");
                            if (imgUrls != null && !imgUrls.isEmpty()) {
                                textPart.append("[此链接包含 ").append(imgUrls.size())
                                        .append(" 张图片，见下方]");
                            }
                            parts.add(com.yoyo.jingxi.network.OpenAiRequest.ContentPart
                                    .text(textPart.toString()));

                            if (imgUrls != null) {
                                int maxImgs = Math.min(imgUrls.size(), 9);
                                for (int i = 0; i < maxImgs; i++) {
                                    String b64 = downloadImageAsBase64(imgUrls.get(i));
                                    if (b64 != null) {
                                        parts.add(com.yoyo.jingxi.network.OpenAiRequest.ContentPart
                                                .imageUrl(b64));
                                    }
                                }
                            }

                            richContentMap.put(msg.id, parts);
                            // 同时保留纯文本版本给非多模态场景
                            msg.content = textPart.toString();
                        } else {
                            StringBuilder sb = new StringBuilder();
                            sb.append("[用户分享了来自")
                                    .append(sc.siteName != null ? sc.siteName : "网页")
                                    .append("的链接]\n");
                            sb.append("标题: ")
                                    .append(sc.contentTitle != null ? sc.contentTitle : "无标题")
                                    .append("\n");
                            if (sc.description != null && !sc.description.isEmpty()) {
                                sb.append("描述: ").append(sc.description).append("\n");
                            }
                            sb.append("链接: ").append(sc.sourceUrl).append("\n");
                            sb.append("[请基于以上信息回复用户关于此链接的讨论，你可以假设自己已查看了该链接的内容]");
                            msg.content = sb.toString();
                        }
                    }
                }
            }

            com.yoyo.jingxi.data.entity.MyPersona myPersona = db.myPersonaDao().getMyPersonaByName(session.myPersonaName);
            String myName = myPersona != null ? myPersona.name : SpUtils.getString("MY_NAME", "我");
            String myPersonaDesc = myPersona != null ? myPersona.persona : SpUtils.getString("MY_PERSONA", "普通人");
            String currentPersonaName = session.myPersonaName != null ? session.myPersonaName : "";

            int memoryCallCount = SpUtils.getInt("SETTING_MEMORY_CALL_COUNT", 20);
            java.util.List<com.yoyo.jingxi.data.entity.Memory> importantMemories = db.memoryDao().getImportantMemoriesSync(characterId, currentPersonaName);
            java.util.List<com.yoyo.jingxi.data.entity.Memory> normalMemories = memoryCallCount > 0 ? 
                db.memoryDao().getNormalMemoriesSync(characterId, currentPersonaName, memoryCallCount) : 
                db.memoryDao().getAllNormalMemoriesSync(characterId, currentPersonaName);

            // 备忘录逻辑已统一由 OpenAIManager 处理
            String scheduleContent = SpUtils.getString("SCHEDULE_CONTENT_" + characterId, "");
            
            java.util.List<com.yoyo.jingxi.data.entity.WorldbookEntry> allEnabled = db.worldbookDao().getAllEnabledEntriesSync();
            String unselectedStr = SpUtils.getString("CHAT_WORLDBOOK_UNSELECTED_" + sessionId, "");
            java.util.List<String> unselectedList = java.util.Arrays.asList(unselectedStr.split(","));
            java.util.List<com.yoyo.jingxi.data.entity.WorldbookEntry> worldbookEntries = new java.util.ArrayList<>();
            for (com.yoyo.jingxi.data.entity.WorldbookEntry entry : allEnabled) {
                if (!unselectedList.contains(String.valueOf(entry.id))) worldbookEntries.add(entry);
            }

            String relationshipContent = "";
            if (SpUtils.getBoolean("RELATIONSHIP_NETWORK_ENABLED", true)) {
                java.util.List<com.yoyo.jingxi.data.entity.RelationshipNode> nodes = db.relationshipNodeDao().getAllNodesSync();
                java.util.List<com.yoyo.jingxi.data.entity.RelationshipEdge> edges = db.relationshipEdgeDao().getAllEdgesSync();
                if (nodes != null && !nodes.isEmpty() && edges != null && !edges.isEmpty()) {
                    StringBuilder relBuilder = new StringBuilder();
                    relBuilder.append("人物图鉴:\n");
                    for (com.yoyo.jingxi.data.entity.RelationshipNode node : nodes) {
                        relBuilder.append("- ").append(node.name).append("\n");
                    }
                    relationshipContent = relBuilder.toString();
                }
            }
            
            String momentsContent = "";
            java.util.List<com.yoyo.jingxi.data.entity.EmojiEntry> emojiEntries = db.emojiDao().getAllEmojisSync();
            int maxAiMessages = SpUtils.getInt("CHAT_MAX_AI_MESSAGES_" + sessionId, 5);

            // 构建天气+便签背景上下文
            String weatherContext = buildWeatherAndNoteContext(characterId);

            // 用户个人数据上下文（日历/课程/经期/节假日）
            String latestUserMsg = extractLatestUserMessage(history);
            String userContext = UserContextBuilder.buildUserContext(db, characterId, currentPersonaName, latestUserMsg);
            if (!userContext.isEmpty()) {
                weatherContext = weatherContext + "\n" + userContext;
            }

            // 记忆2.0 上下文
            String memoryV2Context = "";
            if (SpUtils.getBoolean("MEMORY_V2_ENABLED", true)) {
                MemoryManager memMgr = MemoryManager.getInstance();
                memMgr.init(db);
                // 用户画像始终加载，不依赖关键词匹配
                String profileContext = memMgr.getUserProfileContext(characterId, currentPersonaName);

                String memoryMode = SpUtils.getString("MEMORY_V2_MODE", "economy");
                String userMessage = extractLatestUserMessage(history);

                // 收集关键词匹配到的 episode ID，用于近期记忆去重
                java.util.Set<Integer> matchedEpisodeIds = new java.util.HashSet<>();

                if (!userMessage.isEmpty()) {
                    if ("immersive".equals(memoryMode)) {
                        // 沉浸模式：潜意识AI调用 → 关键词搜索
                        MemoryApiConfig.ApiConfig subConfig = MemoryApiConfig.getSubconsciousApiConfig();
                        if (subConfig.model.isEmpty()) {
                            // 未配置潜意识模型，降级为经济模式
                            java.util.List<String> keywords = memMgr.extractKeywordsFromMessage(userMessage);
                            MemorySearchService.MemorySearchResult result = memMgr.keywordSearch(characterId, currentPersonaName, keywords);
                            for (com.yoyo.jingxi.data.entity.EpisodicMemory ep : result.matchedEpisodes) matchedEpisodeIds.add(ep.id);
                            memoryV2Context = profileContext + result.formattedContext;
                        } else {
                        // Worldbook pre/mid/post
                        StringBuilder wbPre = new StringBuilder(), wbMid = new StringBuilder(), wbPost = new StringBuilder();
                        if (worldbookEntries != null) {
                            for (com.yoyo.jingxi.data.entity.WorldbookEntry wb : worldbookEntries) {
                                if (wb.type == 0) wbPre.append(wb.content).append("\n");
                                else if (wb.type == 1) {
                                    if (userMessage != null && wb.keyword != null) {
                                        for (String kw : wb.keyword.split(","))
                                            if (userMessage.contains(kw.trim())) { wbMid.append(wb.content).append("\n"); break; }
                                    }
                                } else if (wb.type == 2) wbPost.append(wb.content).append("\n");
                            }
                        }
                        // Recent episodes
                        int epCount = Math.min(SpUtils.getInt("SETTING_MEMORY_CALL_COUNT", 20), 5);
                        java.util.List<com.yoyo.jingxi.data.entity.EpisodicMemory> recentEps = memMgr.getRecentEpisodes(characterId, currentPersonaName, epCount);
                        StringBuilder epSummary = new StringBuilder();
                        if (recentEps != null) for (com.yoyo.jingxi.data.entity.EpisodicMemory ep : recentEps)
                            epSummary.append("- ").append(ep.title != null ? ep.title : "").append("\n");

                        com.yoyo.jingxi.network.OpenAiRequest subRequest = memMgr.buildSubconsciousRequest(
                            character.persona, userMessage, history, myName,
                            profileContext,
                            wbPre.length() > 0 ? wbPre.toString() : null,
                            wbMid.length() > 0 ? wbMid.toString() : null,
                            wbPost.length() > 0 ? wbPost.toString() : null,
                            epSummary.length() > 0 ? epSummary.toString() : null,
                            subConfig.model);
                        try {
                            String subUrl = subConfig.endpoint + "v1/chat/completions";
                            retrofit2.Response<com.yoyo.jingxi.network.OpenAiResponse> subResponse =
                                aiManager.getApi().createChatCompletion(subUrl, "Bearer " + subConfig.apiKey, subRequest).execute();
                            if (subResponse.isSuccessful() && subResponse.body() != null
                                && subResponse.body().choices != null && !subResponse.body().choices.isEmpty()) {
                                String subJson = subResponse.body().choices.get(0).message.content;
                                java.util.List<String> aiKeywords = memMgr.parseSubconsciousResponse(subJson);
                                MemorySearchService.MemorySearchResult result = memMgr.keywordSearch(characterId, currentPersonaName, aiKeywords);
                                for (com.yoyo.jingxi.data.entity.EpisodicMemory ep : result.matchedEpisodes) matchedEpisodeIds.add(ep.id);
                            memoryV2Context = profileContext + result.formattedContext;
                            } else {
                                // 降级为经济模式
                                java.util.List<String> localKw = memMgr.extractKeywordsFromMessage(userMessage);
                                MemorySearchService.MemorySearchResult result = memMgr.keywordSearch(characterId, currentPersonaName, localKw);
                                for (com.yoyo.jingxi.data.entity.EpisodicMemory ep : result.matchedEpisodes) matchedEpisodeIds.add(ep.id);
                            memoryV2Context = profileContext + result.formattedContext;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            // 降级为经济模式
                            java.util.List<String> localKw = memMgr.extractKeywordsFromMessage(userMessage);
                            MemorySearchService.MemorySearchResult result = memMgr.keywordSearch(characterId, currentPersonaName, localKw);
                            for (com.yoyo.jingxi.data.entity.EpisodicMemory ep : result.matchedEpisodes) matchedEpisodeIds.add(ep.id);
                            memoryV2Context = profileContext + result.formattedContext;
                        }
                        }
                    } else {
                        // 经济模式：本地关键词提取 + LIKE搜索
                        java.util.List<String> keywords = memMgr.extractKeywordsFromMessage(userMessage);
                        MemorySearchService.MemorySearchResult result = memMgr.keywordSearch(characterId, currentPersonaName, keywords);
                        memoryV2Context = profileContext + result.formattedContext;
                    }
                }

                // 近期记忆：始终注入 top 10 权重最高的心绪（双通道第二通道，不依赖关键词）
                int recentLimit = Math.min(SpUtils.getInt("SETTING_MEMORY_CALL_COUNT", 20), 10);
                String recentCtx = memMgr.buildRecentWeightedContext(characterId, currentPersonaName, recentLimit, matchedEpisodeIds);
                if (!recentCtx.isEmpty()) {
                    memoryV2Context = memoryV2Context + "\n" + recentCtx;
                }
            }

            com.yoyo.jingxi.network.OpenAiRequest request = aiManager.buildRequestWithReason(
                character.persona, history, myName, myPersonaDesc, model,
                importantMemories, normalMemories, new java.util.ArrayList<>(), scheduleContent, worldbookEntries,
                emojiEntries, false, relationshipContent, maxAiMessages, momentsContent, autoReason, weatherContext, memoryV2Context,
                character.nationality, character.location, richContentMap);

            // 网络二次检测（构建请求耗时较长，网络状态可能已变化）
            if (!isNetworkAvailable(context)) {
                insertErrorAndNotify(db, context, sessionId, characterId, "[系统提示: 当前无网络连接，请检查网络设置。]");
                broadcastReplyStatus(context, false);
                return;
            }

            // 带重试的网络请求（最多重试2次，间隔2秒、4秒）
            retrofit2.Response<com.yoyo.jingxi.network.OpenAiResponse> response = null;
            Exception lastException = null;
            int maxRetries = 2;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    response = aiManager.getApi().createChatCompletion(finalUrl, "Bearer " + apiKey, request).execute();
                    break;
                } catch (IOException e) {
                    lastException = e;
                    if (attempt < maxRetries) {
                        android.util.Log.w("AiReplyHelper", "AI request failed (attempt " + (attempt + 1) + "/" + (maxRetries + 1) + "), retrying...: " + e.getClass().getSimpleName());
                        try { Thread.sleep((attempt + 1) * 2000L); } catch (InterruptedException ie) { }
                    }
                }
            }

            if (response == null && lastException != null) {
                String errorMsg;
                if (lastException instanceof SocketTimeoutException) {
                    errorMsg = "[系统提示: AI请求超时，已重试" + maxRetries + "次仍失败，请稍后再试。]";
                } else {
                    errorMsg = "[系统提示: 网络请求异常，AI回复失败。(" + lastException.getClass().getSimpleName() + ")]";
                }
                insertErrorAndNotify(db, context, sessionId, characterId, errorMsg);
                broadcastReplyStatus(context, false);
                return;
            }

            if (response != null && response.isSuccessful() && response.body() != null && response.body().choices != null
                && !response.body().choices.isEmpty()) {
                String rawContent = response.body().choices.get(0).message.content;

                handleAiReplies(context, aiManager, db, sessionId, character, rawContent, currentPersonaName);

                broadcastReplyStatus(context, false);
            } else {
                broadcastReplyStatus(context, false);
            }

        } catch (Exception e) {
            e.printStackTrace();
            insertErrorAndNotify(db, context, sessionId, characterId,
                "[系统提示: 网络请求异常，AI回复失败。(" + e.getClass().getSimpleName() + ")]");
            broadcastReplyStatus(context, false);
        }
    }

    public static void broadcastReplyStatus(Context context, boolean isReplying) {
        Intent intent = new Intent("com.yoyo.jingxi.ACTION_AI_REPLY_STATUS");
        intent.setPackage(context.getPackageName()); // 显式广播，防止被系统限制
        intent.putExtra("is_replying", isReplying);
        context.sendBroadcast(intent);
    }

    private static void insertErrorAndNotify(AppDatabase db, Context context, int sessionId, int characterId, String errorContent) {
        com.yoyo.jingxi.data.entity.Message errorMsg = new com.yoyo.jingxi.data.entity.Message();
        errorMsg.sessionId = sessionId;
        errorMsg.characterId = characterId;
        errorMsg.content = errorContent;
        errorMsg.isFromUser = false;
        errorMsg.type = 0;
        errorMsg.timestamp = System.currentTimeMillis();
        db.messageDao().insert(errorMsg);

        Intent updateIntent = new Intent("com.yoyo.jingxi.ACTION_MESSAGE_UPDATED");
        updateIntent.setPackage(context.getPackageName());
        updateIntent.putExtra("session_id", sessionId);
        context.sendBroadcast(updateIntent);
    }

    private static void handleAiReplies(Context context, OpenAIManager aiManager, AppDatabase db, int sessionId, Character character, String rawContent, String myPersonaName) {
        java.util.List<com.yoyo.jingxi.network.OpenAIManager.ReplyItem> replies = aiManager.parseMultiReplies(rawContent);
        
        long baseTime = System.currentTimeMillis();
        boolean hasNewMessage = false;
        int newMessageCount = 0;
        String latestTextContent = "";
        java.util.List<com.yoyo.jingxi.network.OpenAIManager.ReplyItem> calendarActions = new java.util.ArrayList<>();

        for (int i = 0; i < replies.size(); i++) {
            com.yoyo.jingxi.network.OpenAIManager.ReplyItem item = replies.get(i);

            // 内联记忆已移除——所有记忆由后台 Curator 独立管理。
            // 如果 AI 意外返回了这些旧类型（兼容旧版客户端），静默跳过。
            if ("important_memory".equalsIgnoreCase(item.type)
                || "episodic_memory".equalsIgnoreCase(item.type)) {
                continue;
            }

            // 处理 AI 主动拨打电话（包括用户要求"给我打电话"和 AI 主动决定打电话）
            if ("call".equalsIgnoreCase(item.type)) {
                // 防止重复启动通话
                if (com.yoyo.jingxi.ui.activity.CallActivity.instance != null
                    && !com.yoyo.jingxi.ui.activity.CallActivity.instance.isCallEnded()) {
                    continue; // 已有正在进行的通话
                }
                String callContent = item.content != null ? item.content : "";

                // 检查用户是否正在该角色的聊天窗口中
                int currentActiveSessionId = SpUtils.getInt("CURRENT_CHAT_SESSION_ID", -1);
                if (currentActiveSessionId == sessionId) {
                    // 用户在该聊天 → 直接全屏启动 CallActivity
                    Intent callIntent = new Intent(context,
                        com.yoyo.jingxi.ui.activity.CallActivity.class);
                    callIntent.putExtra("session_id", sessionId);
                    callIntent.putExtra("is_incoming", true);       // AI 打给用户
                    callIntent.putExtra("initial_message", callContent);
                    callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(callIntent);
                } else {
                    // 用户不在该聊天 → 发送横幅通知 + 铃声震动
                    int characterId = character.id;
                    String characterName = character.name;
                    CallIncomingNotificationHelper.sendIncomingCallNotification(
                        context, sessionId, characterId, characterName, callContent);
                }
                continue;
            }

            if (item.revoke_id != null ||
                "memory_note".equalsIgnoreCase(item.type) ||
                "memo".equalsIgnoreCase(item.type) ||
                "calendar_event".equalsIgnoreCase(item.type) ||
                "cycle_record".equalsIgnoreCase(item.type) ||
                "moment".equalsIgnoreCase(item.type) || "moment_interaction".equalsIgnoreCase(item.type)) {

                // 收集 calendar_event 用于后续处理
                if ("calendar_event".equalsIgnoreCase(item.type)) {
                    calendarActions.add(item);
                }
                // 立即处理 cycle_record
                if ("cycle_record".equalsIgnoreCase(item.type)) {
                    processCycleAction(db, item, character.id, myPersonaName);
                }
                continue;
            }
            
            if (("text".equalsIgnoreCase(item.type) || item.type == null) && (item.content == null || item.content.trim().isEmpty())) {
                continue; // 过滤掉空消息，防止出现空闲气泡
            }

            // 防御：如果 content 看起来像 virtual_image 的描述 JSON，自动修正类型（纵深兜底）
            if (("text".equalsIgnoreCase(item.type) || item.type == null)
                && item.content != null
                && item.content.trim().startsWith("{")
                && item.content.contains("\"desc\"")
                && item.content.contains("\"size\"")) {
                item.type = "virtual_image";
            }

            com.yoyo.jingxi.data.entity.Message msg = new com.yoyo.jingxi.data.entity.Message();
            msg.sessionId = sessionId;
            msg.characterId = character.id;
            msg.content = item.content;
            msg.isFromUser = false;
            msg.timestamp = baseTime + i * 1000L;
            
            if ("emoji".equalsIgnoreCase(item.type)) {
                msg.type = 2;
                msg.content = item.content;
            } else if ("voice".equalsIgnoreCase(item.type)) {
                msg.type = 1;  // 语音消息类型
                msg.content = item.content;
            } else if ("virtual_image".equalsIgnoreCase(item.type)) {
                msg.type = 4;
                String rawDesc = item.content;
                if (rawDesc != null) {
                    rawDesc = rawDesc.trim();
                    // 校验并修复 imageDesc JSON，防止AI返回的格式错误导致显示异常
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(rawDesc);
                        if (!json.has("desc")) json.put("desc", rawDesc);
                        if (!json.has("size")) json.put("size", "1024x1792");
                        String sz = json.optString("size", "1024x1792");
                        if (!sz.equals("1024x1024") && !sz.equals("1024x1792") && !sz.equals("1792x1024")) {
                            json.put("size", "1024x1792");
                        }
                        rawDesc = json.toString();
                    } catch (Exception e) {
                        android.util.Log.w("AiReplyHelper", "virtual_image content is not valid JSON, wrapping: "
                            + rawDesc.substring(0, Math.min(100, rawDesc.length())));
                        try {
                            org.json.JSONObject json = new org.json.JSONObject();
                            json.put("desc", rawDesc);
                            json.put("size", "1024x1792");
                            rawDesc = json.toString();
                        } catch (Exception ex) {
                            rawDesc = "{\"desc\":\"一张图片\",\"size\":\"1024x1792\"}";
                        }
                    }
                }
                msg.imageDesc = rawDesc;
                msg.content = "[虚拟图片]";
            } else {
                msg.type = 0;
            }

            // 将 AI 回复中的 quote_id 存储到消息上，使其在 UI 和后续历史中生效
            msg.quoteMessageId = item.quote_id != null ? item.quote_id : -1;

            long msgId = db.messageDao().insert(msg);
            msg.id = (int) msgId;

            // 保存心声（如果有）
            if (item.innerVoice != null && !item.innerVoice.trim().isEmpty()) {
                com.yoyo.jingxi.data.entity.InnerVoice iv = new com.yoyo.jingxi.data.entity.InnerVoice();
                iv.messageId = (int) msgId;
                iv.sessionId = sessionId;
                iv.characterId = character.id;
                iv.content = item.innerVoice.trim();
                iv.emotion = item.innerVoiceEmotion != null ? item.innerVoiceEmotion.trim() : null;
                iv.timestamp = msg.timestamp;
                iv.isRead = false;
                db.innerVoiceDao().insert(iv);
            }

            if (msg.type == 4) {
                com.yoyo.jingxi.utils.ImageGenerationManager.getInstance().checkAndGenerateImagesForMessage(msg);
            }
            
            hasNewMessage = true;
            newMessageCount++;
            if (msg.type == 0 || msg.type == 2) {
                latestTextContent = cleanNotificationText(msg.content);
            } else if (msg.type == 1) {
                latestTextContent = "[语音消息]";
            } else if (msg.type == 4) {
                latestTextContent = "[图片]";
            }

            int currentActiveSessionId = SpUtils.getInt("CURRENT_CHAT_SESSION_ID", -1);
            if (currentActiveSessionId != sessionId) {
                db.chatSessionDao().incrementUnreadCount(sessionId, 1);
            } else {
                db.chatSessionDao().updateUnreadCount(sessionId, 0);
            }
            
            // Notify UI for each message to allow sequential display if needed
            Intent updateIntent = new Intent("com.yoyo.jingxi.ACTION_MESSAGE_UPDATED");
            updateIntent.setPackage(context.getPackageName());
            updateIntent.putExtra("session_id", sessionId);
            context.sendBroadcast(updateIntent);
            
            if (com.yoyo.jingxi.JingxiApplication.getInstance() != null && 
                !com.yoyo.jingxi.JingxiApplication.getInstance().isAppInForeground()) {
                sendLocalNotification(context, character.name, latestTextContent, sessionId, msg.id);
            } else if (com.yoyo.jingxi.JingxiApplication.getInstance() != null &&
                       com.yoyo.jingxi.JingxiApplication.getInstance().isAppInForeground() &&
                       currentActiveSessionId != sessionId) {
                // Also send notification if app is in foreground but not in this specific chat session
                sendLocalNotification(context, character.name, latestTextContent, sessionId, msg.id);
            }
            
            // Introduce a dynamic delay between messages based on content length
            // Short messages appear faster, long messages take more "reading time"
            if (i < replies.size() - 1) {
                try {
                    long delay = computeMessageDelay(item);
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        // 处理 AI 返回的 calendar_event（创建/修改/删除日历事件）
        for (com.yoyo.jingxi.network.OpenAIManager.ReplyItem action : calendarActions) {
            processCalendarAction(db, action, character.id, myPersonaName);
        }

    }

    /**
     * 根据消息内容长度计算下一条消息之前的延迟时间。
     * 短消息快速弹出，长消息给予更多"阅读时间"，模拟自然对话节奏。
     *
     * @param item 当前已插入的消息条目
     * @return 延迟毫秒数
     */
    private static long computeMessageDelay(com.yoyo.jingxi.network.OpenAIManager.ReplyItem item) {
        String type = item.type;
        String content = item.content;

        // 非文本类型使用固定延迟
        if ("virtual_image".equalsIgnoreCase(type)) {
            return 2000; // 图片生成需要更多"等待感"
        }
        if ("emoji".equalsIgnoreCase(type)) {
            return 1000; // 表情包快速弹出
        }
        if ("voice".equalsIgnoreCase(type)) {
            // 语音：按字符数估算朗读时间（约 4 字/秒 = 250ms/字）
            int charLen = (content != null) ? content.length() : 10;
            return Math.min(4000, Math.max(1200, charLen * 250L));
        }

        // 文本消息：纯连续线性，每字 120ms，无下限 clamp
        // 1 字=620ms, 10 字=1700ms, 60 字=7700ms, 125+字=15000ms 上限
        if (content == null) return 800;
        int charCount = content.length();
        long delay = 500 + charCount * 120L;
        delay = Math.min(15000, delay);  // 最多 15 秒
        return delay;
    }

    /**
     * 处理 AI 返回的 calendar_event 类型：创建、修改或删除日历事件。
     * 支持时间段（start_time/end_time）、全天事件、重复规则。
     */
    private static void processCalendarAction(AppDatabase db, com.yoyo.jingxi.network.OpenAIManager.ReplyItem item, int characterId, String personaName) {
        // 写权限检查
        UserContextSettings settings = UserContextSettings.load();
        if (!settings.canWrite("calendar", characterId, personaName)) {
            return; // AI 无写入权限，静默跳过
        }

        com.yoyo.jingxi.data.dao.CalendarEventDao dao = db.calendarEventDao();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);

        try {
            if ("delete".equals(item.action) && item.target_id != null) {
                com.yoyo.jingxi.data.entity.CalendarEvent e = dao.getEventById(item.target_id);
                if (e != null) dao.delete(e);
            } else if ("edit".equals(item.action) && item.target_id != null) {
                com.yoyo.jingxi.data.entity.CalendarEvent e = dao.getEventById(item.target_id);
                if (e == null) return;
                if (item.title != null && !item.title.isEmpty()) e.title = item.title;
                if (item.content != null) e.notes = item.content;
                if (item.date != null && !item.date.isEmpty()) e.eventDate = item.date;
                if (item.status != null) e.allDay = item.status == 1;
                // 支持修改时间范围和重复规则
                if (item.startTime != null && !item.startTime.isEmpty()) {
                    e.startTime = combineDateAndTime(e.eventDate, item.startTime);
                }
                if (item.endTime != null && !item.endTime.isEmpty()) {
                    e.endTime = combineDateAndTime(e.eventDate, item.endTime);
                }
                if (item.recurrence != null && !item.recurrence.isEmpty()) e.recurrence = item.recurrence;
                dao.update(e);
            } else {
                // "add" (默认)
                com.yoyo.jingxi.data.entity.CalendarEvent e = new com.yoyo.jingxi.data.entity.CalendarEvent();
                e.title = item.title != null && !item.title.isEmpty() ? item.title
                    : (item.content != null && item.content.length() <= 30 ? item.content : "新事件");
                e.notes = item.content != null ? item.content : "";
                e.eventDate = item.date != null && !item.date.isEmpty() ? item.date : sdf.format(new java.util.Date());
                e.allDay = item.status != null && item.status == 1;
                e.recurrence = item.recurrence != null && !item.recurrence.isEmpty() ? item.recurrence : "NONE";
                // 处理时间段
                if (item.startTime != null && !item.startTime.isEmpty()) {
                    e.startTime = combineDateAndTime(e.eventDate, item.startTime);
                }
                if (item.endTime != null && !item.endTime.isEmpty()) {
                    e.endTime = combineDateAndTime(e.eventDate, item.endTime);
                }
                e.createdAt = System.currentTimeMillis();
                dao.insert(e);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 处理 AI 返回的 cycle_record 类型：创建、更新或结束经期记录。
     * 字段复用：status→flowLevel, content→symptoms, title→notes
     */
    private static void processCycleAction(AppDatabase db, com.yoyo.jingxi.network.OpenAIManager.ReplyItem item, int characterId, String personaName) {
        // 写权限检查
        UserContextSettings settings = UserContextSettings.load();
        if (!settings.canWrite("period", characterId, personaName)) {
            return; // AI 无写入权限，静默跳过
        }

        com.yoyo.jingxi.data.dao.CycleRecordDao dao = db.cycleRecordDao();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);

        try {
            if ("end".equals(item.action)) {
                // 找到最近一条未结束的经期记录，设置结束日期
                com.yoyo.jingxi.data.entity.CycleRecord open = dao.getOpenEndedRecord();
                if (open == null) return;
                String endDate = item.date != null && !item.date.isEmpty() ? item.date : sdf.format(new java.util.Date());
                if (endDate.compareTo(open.startDate) < 0) return; // 结束日期不能早于开始
                open.endDate = endDate;
                dao.update(open);
            } else if ("update".equals(item.action) && item.date != null) {
                // 找到覆盖该日期的经期记录并更新
                com.yoyo.jingxi.data.entity.CycleRecord r = dao.getPeriodOnDate(item.date);
                if (r == null) return;
                if (item.status != null) r.flowLevel = item.status;   // status → flowLevel
                if (item.content != null && !item.content.isEmpty()) r.symptoms = item.content; // content → symptoms
                if (item.title != null && !item.title.isEmpty()) r.notes = item.title;           // title → notes
                dao.update(r);
            } else {
                // "add" (默认) - 创建新经期记录
                com.yoyo.jingxi.data.entity.CycleRecord r = new com.yoyo.jingxi.data.entity.CycleRecord();
                r.startDate = item.date != null && !item.date.isEmpty() ? item.date : sdf.format(new java.util.Date());
                r.endDate = r.startDate; // 初始设为同一天（开放状态），等 action='end' 时再闭合
                if (item.status != null) r.flowLevel = item.status;    // status → flowLevel
                if (item.content != null && !item.content.isEmpty()) r.symptoms = item.content; // content → symptoms
                if (item.title != null && !item.title.isEmpty()) r.notes = item.title;           // title → notes
                r.createdAt = System.currentTimeMillis();
                dao.insert(r);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 将 "HH:MM" 格式的时间字符串与日期字符串组合，返回 epoch millis。
     */
    private static long combineDateAndTime(String dateStr, String timeStr) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(sdf.parse(dateStr));
            String[] parts = timeStr.trim().split(":");
            if (parts.length >= 2) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0].trim()));
                cal.set(java.util.Calendar.MINUTE, Integer.parseInt(parts[1].trim()));
            }
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    public static void runMemoryReviewFromNotes(AppDatabase db, OpenAIManager aiManager, int sessionId,
                                                  com.yoyo.jingxi.data.entity.Character character,
                                                  java.util.List<String> notes, String myPersonaName) {
        if (notes.isEmpty()) return;
        android.util.Log.d("MemoryCurator", "runMemoryReviewFromNotes started with " + notes.size() + " notes");
        String myName = myPersonaName != null && !myPersonaName.isEmpty() ? myPersonaName : SpUtils.getString("MY_NAME", "用户");
        // Gather existing context for dedup
        com.yoyo.jingxi.utils.MemoryManager memMgr = com.yoyo.jingxi.utils.MemoryManager.getInstance();
        memMgr.init(db);
        java.util.List<String> existingContext = new java.util.ArrayList<>();
        java.util.List<com.yoyo.jingxi.data.entity.EpisodicMemory> recentEps = memMgr.getRecentEpisodes(character.id, myPersonaName, 20);
        if (recentEps != null && !recentEps.isEmpty()) {
            existingContext.add("已记录的心绪(避免重复)：");
            for (com.yoyo.jingxi.data.entity.EpisodicMemory ep : recentEps)
                existingContext.add("- " + (ep.title != null ? ep.title : ""));
        }
        try {
            MemoryApiConfig.ApiConfig curatorConfig = com.yoyo.jingxi.utils.MemoryApiConfig.getCuratorApiConfig();
            String relInfo = buildRelationshipInfo(character.persona, db);
            String wbCtx = buildWorldbookContextForCurator(db);
            android.util.Log.d("MemoryCurator", "Calling curator with " + notes.size() + " notes, model=" + curatorConfig.model + " endpoint=" + curatorConfig.endpoint);
            com.yoyo.jingxi.network.OpenAiRequest curatorReq = aiManager.buildMemoryCuratorRequest(
                character.persona, myName, notes, existingContext, curatorConfig.model, relInfo, wbCtx);
            retrofit2.Response<com.yoyo.jingxi.network.OpenAiResponse> curResp =
                aiManager.getApi().createChatCompletion(curatorConfig.endpoint + "v1/chat/completions", "Bearer " + curatorConfig.apiKey, curatorReq).execute();
            if (curResp.isSuccessful() && curResp.body() != null && curResp.body().choices != null && !curResp.body().choices.isEmpty()) {
                String curJson = curResp.body().choices.get(0).message.content;
                com.yoyo.jingxi.network.OpenAIManager.CuratorResult result = aiManager.parseCuratorResponse(curJson);
                // Billboards removed — only episodes are processed below
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * 综合 Curator：提取 episodic_memory。
     * 每轮都运行。
     */
    public static void runUnifiedCurator(AppDatabase db, OpenAIManager aiManager, int sessionId,
                                          int characterId, String persona, String characterName, String myPersonaName) {
        try {
            // 轮次计数器：每 N 轮运行一次
            int interval = SpUtils.getInt("UNIFIED_CURATOR_INTERVAL", 5);
            String counterKey = "UNIFIED_CURATOR_COUNT_" + characterId;
            int count = SpUtils.getInt(counterKey, 0) + 1;
            if (count < interval) { SpUtils.putInt(counterKey, count); return; }
            SpUtils.putInt(counterKey, 0);

            // 消息级去重：跳过已处理过的消息
            int reviewRounds = Math.min(SpUtils.getInt("SETTING_HISTORY_ROUNDS", 80), 20);
            java.util.List<com.yoyo.jingxi.data.entity.Message> recentMsgs = db.messageDao().getRecentMessagesBySessionIdSync(sessionId, reviewRounds * 2);
            if (recentMsgs == null || recentMsgs.isEmpty()) return;
            String msgTrackKey = "LAST_CURATOR_MSG_ID_" + sessionId;
            int lastProcessedId = SpUtils.getInt(msgTrackKey, 0);
            int maxMsgId = 0;
            for (com.yoyo.jingxi.data.entity.Message m : recentMsgs) {
                if (m.id > maxMsgId) maxMsgId = m.id;
            }
            if (maxMsgId <= lastProcessedId) return; // 没有新消息，跳过
            java.util.Collections.reverse(recentMsgs);
            java.util.List<String> transcript = new java.util.ArrayList<>();
            String myName = myPersonaName != null && !myPersonaName.isEmpty() ? myPersonaName : SpUtils.getString("MY_NAME", "用户");
            for (com.yoyo.jingxi.data.entity.Message m : recentMsgs) {
                if (m.content != null && !m.content.trim().isEmpty() && m.type != 99 && m.type != 100) {
                    transcript.add((m.isFromUser ? myName : characterName) + ": " + m.content.trim());
                }
            }
            if (transcript.isEmpty()) return;

            // Gather existing episodes for dedup context
            com.yoyo.jingxi.utils.MemoryManager memMgr = com.yoyo.jingxi.utils.MemoryManager.getInstance();
            memMgr.init(db);
            StringBuilder existingEpisodes = new StringBuilder();
            java.util.List<com.yoyo.jingxi.data.entity.EpisodicMemory> recentEps = memMgr.getRecentEpisodes(characterId, myPersonaName, 50);
            if (recentEps != null && !recentEps.isEmpty()) {
                for (com.yoyo.jingxi.data.entity.EpisodicMemory ep : recentEps) {
                    existingEpisodes.append("- ").append(ep.title != null ? ep.title : "").append("\n");
                }
            }

            // Build relationship/worldbook context
            String relInfo = buildRelationshipInfo(persona, db);
            String wbCtx = buildWorldbookContextForCurator(db);

            // Call Unified Curator (只产出 episodes)
            android.util.Log.d("UnifiedCurator", "Review with " + transcript.size() + " lines");
            MemoryApiConfig.ApiConfig curatorConfig = com.yoyo.jingxi.utils.MemoryApiConfig.getCuratorApiConfig();
            com.yoyo.jingxi.network.OpenAiRequest curatorReq = aiManager.buildUnifiedCuratorRequest(
                persona, myName, transcript, existingEpisodes.toString(),
                relInfo, wbCtx, curatorConfig.model);
            curatorReq.response_format = new com.yoyo.jingxi.network.OpenAiRequest.ResponseFormat();
            retrofit2.Response<com.yoyo.jingxi.network.OpenAiResponse> curResp =
                aiManager.getApi().createChatCompletion(curatorConfig.endpoint + "v1/chat/completions", "Bearer " + curatorConfig.apiKey, curatorReq).execute();
            if (curResp.isSuccessful() && curResp.body() != null && curResp.body().choices != null && !curResp.body().choices.isEmpty()) {
                String curJson = curResp.body().choices.get(0).message.content;
                com.yoyo.jingxi.network.OpenAIManager.CuratorResult result = aiManager.parseCuratorResponse(curJson);
                android.util.Log.d("UnifiedCurator", "episodes:" + result.episodes.size());

                memMgr.init(db);
                // Process episodes — 同批次去重 + 扩大窗口(50条)
                java.util.Set<String> batchSeenTitles = new java.util.HashSet<>();
                for (com.yoyo.jingxi.network.OpenAIManager.CuratorItem ci : result.episodes) {
                    if (ci.title == null || ci.title.isEmpty() || ci.content == null || ci.content.isEmpty()) continue;
                    String ciTitleNorm = normalizeForDedup(ci.title);
                    if (batchSeenTitles.contains(ciTitleNorm)) continue;
                    boolean dup = false;
                    if (recentEps != null) {
                        for (com.yoyo.jingxi.data.entity.EpisodicMemory ep : recentEps) {
                            if (ep.title != null && normalizeForDedup(ep.title).equals(ciTitleNorm)) {
                                dup = true; break;
                            }
                        }
                    }
                    if (!dup) {
                        com.yoyo.jingxi.data.entity.EpisodicMemory existing = db.episodicMemoryDao()
                            .findByCharacterIdAndTitle(characterId, myPersonaName, ci.title);
                        dup = existing != null;
                    }
                    if (!dup) {
                        memMgr.addEpisodicMemory(characterId, myPersonaName,
                            new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date()),
                            ci.title, ci.content, "",
                            ci.emotionTone != null ? ci.emotionTone : "平静",
                            ci.importanceLevel > 0 ? ci.importanceLevel : 2, "");
                        batchSeenTitles.add(ciTitleNorm);
                        android.util.Log.d("UnifiedCurator", "Episode: " + ci.title);
                    }
                }
                SpUtils.putInt(msgTrackKey, maxMsgId);
            } else {
                android.util.Log.w("UnifiedCurator", "API failed: " + (curResp != null ? curResp.code() : "null"));
            }
        } catch (Exception e) {
            android.util.Log.e("UnifiedCurator", "Review error", e);
        }
    }

    /** 宽松去重：去除标点、空格后比较 */
    private static String normalizeForDedup(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s，。！？、；：\"'（）《》\\[\\].,!?;:]+", "").trim().toLowerCase();
    }

    /**
     * 画像 curator：定期回顾对话，发现/验证对用户的认知。
     * 每 N 轮触发一次（默认 10 轮），由 AiReplyService 在每次回复后调用。
     */
    public static void runProfileCurator(AppDatabase db, OpenAIManager aiManager, int sessionId,
                                          int characterId, String persona, String characterName, String myPersonaName) {
        try {
            // 检查当前人设是否启用了画像提取
            if (myPersonaName != null && !myPersonaName.isEmpty()) {
                com.yoyo.jingxi.data.entity.MyPersona mp = db.myPersonaDao().getMyPersonaByName(myPersonaName);
                if (mp != null && !mp.enableProfileExtraction) {
                    return; // 当前人设未启用画像提取，跳过
                }
            }
            // Check round counter: only run every N rounds
            String counterKey = "PROFILE_CURATOR_ROUND_COUNT_" + characterId;
            int roundCount = SpUtils.getInt(counterKey, 0) + 1;
            int interval = SpUtils.getInt("PROFILE_CURATOR_INTERVAL", 10);
            if (roundCount < interval) {
                SpUtils.putInt(counterKey, roundCount);
                return;
            }
            SpUtils.putInt(counterKey, 0); // Reset counter

            int reviewRounds = Math.min(SpUtils.getInt("SETTING_HISTORY_ROUNDS", 80), 30);
            java.util.List<com.yoyo.jingxi.data.entity.Message> recentMsgs =
                db.messageDao().getRecentMessagesBySessionIdSync(sessionId, reviewRounds * 2);
            if (recentMsgs == null || recentMsgs.isEmpty()) return;

            java.util.Collections.reverse(recentMsgs);
            java.util.List<String> transcript = new java.util.ArrayList<>();
            String myName = myPersonaName != null && !myPersonaName.isEmpty() ? myPersonaName : SpUtils.getString("MY_NAME", "用户");
            for (com.yoyo.jingxi.data.entity.Message m : recentMsgs) {
                if (m.content != null && !m.content.trim().isEmpty() && m.type != 99 && m.type != 100) {
                    transcript.add((m.isFromUser ? myName : characterName) + ": " + m.content.trim());
                }
            }
            if (transcript.isEmpty()) return;

            // Gather existing profiles for context
            com.yoyo.jingxi.utils.MemoryManager memMgr = com.yoyo.jingxi.utils.MemoryManager.getInstance();
            memMgr.init(db);
            String profileContext = memMgr.getUserProfileContext(characterId, myPersonaName);

            android.util.Log.d("ProfileCurator", "Review with " + transcript.size() + " lines, interval=" + interval);

            // Get API config (reuse curator config)
            MemoryApiConfig.ApiConfig curatorConfig =
                com.yoyo.jingxi.utils.MemoryApiConfig.getCuratorApiConfig();

            // Build relationship/worldbook context for curator
            String relInfo = buildRelationshipInfo(persona, db);
            String wbCtx = buildWorldbookContextForCurator(db);

            // Build request and call AI
            com.yoyo.jingxi.network.OpenAiRequest curatorReq = aiManager.buildProfileCuratorRequest(
                persona, myName, transcript, profileContext, curatorConfig.model, relInfo, wbCtx);

            retrofit2.Response<com.yoyo.jingxi.network.OpenAiResponse> curResp =
                aiManager.getApi().createChatCompletion(
                    curatorConfig.endpoint + "v1/chat/completions",
                    "Bearer " + curatorConfig.apiKey,
                    curatorReq).execute();

            if (!curResp.isSuccessful() || curResp.body() == null
                || curResp.body().choices == null || curResp.body().choices.isEmpty()) {
                android.util.Log.w("ProfileCurator", "Review failed: " + (curResp != null ? curResp.code() : "null"));
                return;
            }

            String curJson = curResp.body().choices.get(0).message.content;
            com.yoyo.jingxi.network.OpenAIManager.CuratorResult result = aiManager.parseCuratorResponse(curJson);

            android.util.Log.d("ProfileCurator", "Got " + result.profiles.size() + " profile actions");

            // Process profile actions with dedup (by category+keyItem within this batch)
            java.util.Set<String> processed = new java.util.HashSet<>();
            memMgr.init(db);

            for (com.yoyo.jingxi.network.OpenAIManager.CuratorItem ci : result.profiles) {
                if (ci.action == null) continue;
                String cat = ci.category != null ? ci.category : "其他";
                String ki = ci.keyItem != null && !ci.keyItem.isEmpty() ? ci.keyItem : "";
                if (ki.isEmpty()) continue;
                String dedupKey = cat + "::" + ki;
                if (processed.contains(dedupKey)) continue;
                processed.add(dedupKey);

                if ("confirm".equals(ci.action)) {
                    boolean found = memMgr.confirmProfileNode(characterId, myPersonaName, cat, ki);
                    android.util.Log.d("ProfileCurator",
                        "confirm " + cat + "/" + ki + " → " + (found ? "updated" : "not found"));
                } else {
                    // "add" (default) — only add if there's actual content
                    if (ci.content == null || ci.content.trim().isEmpty()) continue;
                    int conf = (ci.confidence != null && ci.confidence >= 1 && ci.confidence <= 10)
                        ? ci.confidence : 6;
                    String emo = ci.emotionTone != null ? ci.emotionTone : "普通";
                    memMgr.addUserProfileNode(characterId, myPersonaName, cat, ki, ci.content.trim(), emo, conf);
                    android.util.Log.d("ProfileCurator",
                        "add " + cat + "/" + ki + " conf=" + conf + (ci.evidence != null ? " evidence:" + ci.evidence : ""));
                }
            }
        } catch (Exception e) {
            android.util.Log.e("ProfileCurator", "Review error", e);
        }
    }

    private static com.yoyo.jingxi.data.entity.Memory createImportantMemory(int characterId, com.yoyo.jingxi.network.OpenAIManager.ReplyItem item) {
        com.yoyo.jingxi.data.entity.Memory memory = new com.yoyo.jingxi.data.entity.Memory();
        memory.characterId = characterId;
        memory.type = 1;
        memory.content = item.content;
        memory.starLevel = item.star > 0 ? item.star : 3;
        memory.timestamp = System.currentTimeMillis();
        memory.category = item.category;
        return memory;
    }

    /**
     * 清理消息文本中的 TTS 语气词标签和停顿标签，用于通知展示。
     * 语气标签如 (laughs)/(sighs) 等，停顿标签如 &lt;#0.5#&gt;。
     * ChatAdapter.getCleanVoiceText() 也委托到此方法，避免重复维护标签列表。
     */
    public static String cleanNotificationText(String content) {
        if (content == null) return "";
        String cleaned = content;
        // 移除停顿标签 <#0.5#>, <#1#>, <#2.0#> 等
        cleaned = cleaned.replaceAll("<#[0-9.]+?#>", "");
        // 移除语气词标签
        cleaned = cleaned.replaceAll("\\(laughs\\)", "");
        cleaned = cleaned.replaceAll("\\(chuckle\\)", "");
        cleaned = cleaned.replaceAll("\\(coughs\\)", "");
        cleaned = cleaned.replaceAll("\\(clear-throat\\)", "");
        cleaned = cleaned.replaceAll("\\(clears throat\\)", "");
        cleaned = cleaned.replaceAll("\\(groans\\)", "");
        cleaned = cleaned.replaceAll("\\(breath\\)", "");
        cleaned = cleaned.replaceAll("\\(pant\\)", "");
        cleaned = cleaned.replaceAll("\\(inhale\\)", "");
        cleaned = cleaned.replaceAll("\\(exhale\\)", "");
        cleaned = cleaned.replaceAll("\\(gasps\\)", "");
        cleaned = cleaned.replaceAll("\\(sniffs\\)", "");
        cleaned = cleaned.replaceAll("\\(sighs\\)", "");
        cleaned = cleaned.replaceAll("\\(cries\\)", "");
        cleaned = cleaned.replaceAll("\\(yawns\\)", "");
        cleaned = cleaned.replaceAll("\\(swallows\\)", "");
        cleaned = cleaned.replaceAll("\\(snorts\\)", "");
        cleaned = cleaned.replaceAll("\\(burps\\)", "");
        cleaned = cleaned.replaceAll("\\(lip-smacking\\)", "");
        cleaned = cleaned.replaceAll("\\(humming\\)", "");
        cleaned = cleaned.replaceAll("\\(hissing\\)", "");
        cleaned = cleaned.replaceAll("\\(emm\\)", "");
        cleaned = cleaned.replaceAll("\\(sneezes\\)", "");
        // 兜底：移除剩余的任何 (英文) 标签
        cleaned = cleaned.replaceAll("\\([a-zA-Z][^)]*\\)", "");
        return cleaned.trim();
    }

    private static void sendLocalNotification(Context context, String title, String content, int sessionId, int notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "new_message_channel";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("AiReplyHelper", "Notification permission not granted, skipping local notification.");
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "新消息通知", NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true);
            channel.enableLights(true);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, ChatMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent);

        notificationManager.notify(notificationId, builder.build());
    }

    private static void updateNotification(Context context, String text) {
        Intent updateIntent = new Intent(com.yoyo.jingxi.service.AiReplyService.ACTION_UPDATE_NOTIFICATION);
        updateIntent.putExtra(com.yoyo.jingxi.service.AiReplyService.EXTRA_NOTIFICATION_TEXT, text);
        context.sendBroadcast(updateIntent);
    }

    /**
     * 构建天气+便签背景上下文，作为自然背景信息注入 prompt。
     * 只在全局天气开关开启时生效，角色不刻意提及。
     */
    private static String buildWeatherAndNoteContext(int characterId) {
        StringBuilder ctx = new StringBuilder();

        // 全局天气映射
        if (SpUtils.getBoolean("WEATHER_GLOBAL_MAPPING_ENABLED", false)) {
            String prefsName = "jingxi_prefs";
            android.content.Context context = com.yoyo.jingxi.JingxiApplication.getInstance();
            if (context == null) return "";

            android.content.SharedPreferences prefs = context.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE);
            String apiType = prefs.getString("WEATHER_DETAIL_API_TYPE", "");
            String dailyJson = prefs.getString("WEATHER_DETAIL_DAILY_JSON", "");
            String cityName = prefs.getString("WEATHER_CITY_NAME", "北京");

            if (!dailyJson.isEmpty()) {
                try {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    String dailyWeather = "";
                    String maxTemp = "";
                    String minTemp = "";

                    if ("qweather".equals(apiType)) {
                        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.List<com.yoyo.jingxi.network.QWeatherApi.QWeatherDailyResponse.Daily>>() {}.getType();
                        java.util.List<com.yoyo.jingxi.network.QWeatherApi.QWeatherDailyResponse.Daily> dailyList = gson.fromJson(dailyJson, type);
                        if (dailyList != null && !dailyList.isEmpty()) {
                            dailyWeather = dailyList.get(0).textDay;
                            maxTemp = dailyList.get(0).tempMax + "°C";
                            minTemp = dailyList.get(0).tempMin + "°C";
                        }
                    } else if ("openmeteo".equals(apiType)) {
                        com.yoyo.jingxi.network.OpenMeteoApi.OpenMeteoResponse.Daily daily = gson.fromJson(dailyJson, com.yoyo.jingxi.network.OpenMeteoApi.OpenMeteoResponse.Daily.class);
                        if (daily != null && daily.temperature_2m_max != null && !daily.temperature_2m_max.isEmpty()) {
                            if (daily.weathercode != null && !daily.weathercode.isEmpty()) {
                                dailyWeather = getWeatherDescStatic(daily.weathercode.get(0));
                            }
                            maxTemp = Math.round(daily.temperature_2m_max.get(0)) + "°C";
                            minTemp = Math.round(daily.temperature_2m_min.get(0)) + "°C";
                        }
                    }

                    if (!dailyWeather.isEmpty() || !maxTemp.isEmpty()) {
                        ctx.append("[环境背景] 用户当前所在地：").append(cityName);
                        if (!dailyWeather.isEmpty()) {
                            ctx.append("，今天天气").append(dailyWeather);
                        }
                        if (!maxTemp.isEmpty()) {
                            ctx.append("，最高温").append(maxTemp).append("，最低温").append(minTemp);
                        }
                        ctx.append("。");
                    }

                    // 逐小时天气（仅当同时开启逐小时和全局映射时）
                    if (SpUtils.getBoolean("WEATHER_NOTE_HOURLY_ENABLED", false)) {
                        String hourlyJson = prefs.getString("WEATHER_DETAIL_HOURLY_JSON", "");
                        if (!hourlyJson.isEmpty()) {
                            String hourlyInfo = buildHourlyInfoStatic(apiType, hourlyJson, gson);
                            if (!hourlyInfo.isEmpty()) {
                                ctx.append(" 今天逐小时天气：").append(hourlyInfo).append("。");
                            }
                        }
                    }

                    ctx.append(" （你知道今天的天气，但不用专门汇报——你只是自然地感知着外面的温度、光线和空气。）\n");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // 便签自知（作为背景信息一起注入）
        try {
            String dateKey = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
            String noteKey = "WEATHER_REMINDER_" + characterId + "_" + dateKey;
            String todayNote = SpUtils.getString(noteKey, "");
            if (!todayNote.isEmpty()) {
                ctx.append("[背景] 你今天给用户写过一条便签提醒，内容大意是：\"").append(todayNote)
                   .append("\"。这只是背景信息，不要刻意提起便签，仅在对话自然涉及相关内容时才可提及。\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ctx.toString();
    }

    private static String getWeatherDescStatic(int code) {
        if (code == 0) return "晴";
        if (code >= 1 && code <= 3) return "多云";
        if (code >= 45 && code <= 48) return "雾";
        if (code >= 51 && code <= 55) return "毛毛雨";
        if (code >= 61 && code <= 65) return "雨";
        if (code >= 71 && code <= 75) return "雪";
        if (code >= 80 && code <= 82) return "阵雨";
        if (code >= 95 && code <= 99) return "雷暴";
        return "未知";
    }

    private static String buildHourlyInfoStatic(String apiType, String hourlyJson, com.google.gson.Gson gson) {
        StringBuilder hb = new StringBuilder();
        try {
            if ("qweather".equals(apiType)) {
                java.lang.reflect.Type hType = new com.google.gson.reflect.TypeToken<java.util.List<com.yoyo.jingxi.network.QWeatherApi.QWeatherHourlyResponse.Hourly>>() {}.getType();
                java.util.List<com.yoyo.jingxi.network.QWeatherApi.QWeatherHourlyResponse.Hourly> hList = gson.fromJson(hourlyJson, hType);
                if (hList != null) {
                    for (com.yoyo.jingxi.network.QWeatherApi.QWeatherHourlyResponse.Hourly h : hList) {
                        if (hb.length() > 0) hb.append("，");
                        String time = h.fxTime.length() >= 13 ? h.fxTime.substring(11, 13) + ":00" : h.fxTime;
                        hb.append(time).append(" ").append(h.temp).append("°C ").append(h.text);
                    }
                }
            } else if ("openmeteo".equals(apiType)) {
                com.yoyo.jingxi.network.OpenMeteoApi.OpenMeteoResponse.Hourly hData = gson.fromJson(hourlyJson, com.yoyo.jingxi.network.OpenMeteoApi.OpenMeteoResponse.Hourly.class);
                if (hData != null && hData.time != null && hData.temperature_2m != null) {
                    for (int i = 0; i < Math.min(hData.time.size(), 24); i++) {
                        if (hb.length() > 0) hb.append("，");
                        String time = hData.time.get(i).length() >= 16 ? hData.time.get(i).substring(11, 16) : hData.time.get(i);
                        String wDesc = (hData.weathercode != null && i < hData.weathercode.size()) ? getWeatherDescStatic(hData.weathercode.get(i)) : "";
                        hb.append(time).append(" ").append(Math.round(hData.temperature_2m.get(i))).append("°C ").append(wDesc);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return hb.toString();
    }

    private static String extractLatestUserMessage(java.util.List<com.yoyo.jingxi.data.entity.Message> history) {
        if (history == null) return "";
        for (int i = history.size() - 1; i >= 0; i--) {
            com.yoyo.jingxi.data.entity.Message msg = history.get(i);
            if (msg.isFromUser && msg.content != null && !msg.content.trim().isEmpty()) {
                return msg.content.trim();
            }
        }
        return "";
    }

    private static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    // ==================== Curator 辅助方法 ====================

    /**
     * 组装关系信息文本，供 curator prompt 使用。
     * 从人设词（最重要的关系来源）+ 关系网络图中提取。
     * 即使用户没有手动构建关系图，人设词中的关系描述也能被 curator 看到。
     */
    static String buildRelationshipInfo(String persona, AppDatabase db) {
        StringBuilder sb = new StringBuilder();
        // 人设词是第一优先级的关系信息来源
        sb.append("角色人设: ").append(persona).append("\n");
        // 尝试从关系网络获取
        try {
            java.util.List<com.yoyo.jingxi.data.entity.RelationshipNode> nodes =
                db.relationshipNodeDao().getAllNodesSync();
            java.util.List<com.yoyo.jingxi.data.entity.RelationshipEdge> edges =
                db.relationshipEdgeDao().getAllEdgesSync();
            if (nodes != null && !nodes.isEmpty()) {
                sb.append("关系网络节点:\n");
                for (com.yoyo.jingxi.data.entity.RelationshipNode node : nodes) {
                    sb.append("- ").append(node.name);
                    if (node.description != null && !node.description.isEmpty()) {
                        sb.append(": ").append(node.description);
                    }
                    sb.append("\n");
                }
            }
            if (edges != null && !edges.isEmpty() && nodes != null) {
                sb.append("关系连线:\n");
                for (com.yoyo.jingxi.data.entity.RelationshipEdge edge : edges) {
                    String fromName = "", toName = "";
                    for (com.yoyo.jingxi.data.entity.RelationshipNode n : nodes) {
                        if (n.id.equals(edge.sourceNodeId)) fromName = n.name;
                        if (n.id.equals(edge.targetNodeId)) toName = n.name;
                    }
                    sb.append("- ").append(fromName).append(" → ").append(toName);
                    if (edge.relation != null && !edge.relation.isEmpty()) {
                        sb.append(" (").append(edge.relation).append(")");
                    }
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            // 关系表可能尚不存在，静默忽略
        }
        return sb.toString();
    }

    /**
     * 组装世界书上下文文本，供 curator prompt 使用。
     */
    static String buildWorldbookContextForCurator(AppDatabase db) {
        try {
            java.util.List<com.yoyo.jingxi.data.entity.WorldbookEntry> entries =
                db.worldbookDao().getAllEnabledEntriesSync();
            if (entries == null || entries.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (com.yoyo.jingxi.data.entity.WorldbookEntry entry : entries) {
                if (entry.content != null && !entry.content.isEmpty()) {
                    sb.append(entry.content).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 解析 JSON 数组格式的图片 URL */
    private static java.util.List<String> parseImageUrls(String imageUrlsJson) {
        if (imageUrlsJson == null || imageUrlsJson.isEmpty()) return null;
        try {
            return new com.google.gson.Gson().fromJson(imageUrlsJson,
                    new com.google.gson.reflect.TypeToken<java.util.List<String>>(){}.getType());
        } catch (Exception e) {
            return null;
        }
    }

    /** 下载图片并返回 base64 编码字符串（去除 data:image 前缀） */
    private static String downloadImageAsBase64(String imageUrl) {
        try {
            java.net.URL url = new java.net.URL(imageUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");
            conn.setRequestProperty("Referer", "https://www.xiaohongshu.com/");

            java.io.InputStream is = conn.getInputStream();
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
            is.close();
            conn.disconnect();

            if (bitmap == null) return null;

            // 压缩到 1024px 宽
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (w > 1024) {
                float ratio = 1024f / w;
                android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(
                        bitmap, 1024, (int)(h * ratio), true);
                bitmap.recycle();
                bitmap = scaled;
            }

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos);
            bitmap.recycle();
            byte[] bytes = baos.toByteArray();
            baos.close();

            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从文本中提取 URL。与 ChatActivity.extractUrlFromText 逻辑一致。
     */
    private static String extractUrlFromText(String text) {
        if (text == null) return null;
        if (text.startsWith("http://") || text.startsWith("https://")) {
            int sp = text.indexOf(' ');
            return sp > 0 ? text.substring(0, sp) : text;
        }
        for (String domain : new String[]{"xhslink.com", "xhslink.cn", "b23.tv", "v.douyin.com", "t.cn"}) {
            int i = text.indexOf(domain);
            if (i >= 0) {
                int s = i;
                while (s > 0 && text.charAt(s - 1) != ' ' && text.charAt(s - 1) != '\n') s--;
                int e = i + domain.length();
                while (e < text.length() && text.charAt(e) != ' ' && text.charAt(e) != '\n') e++;
                String u = text.substring(s, e);
                return u.startsWith("http") ? u : "https://" + u;
            }
        }
        return null;
    }

}
