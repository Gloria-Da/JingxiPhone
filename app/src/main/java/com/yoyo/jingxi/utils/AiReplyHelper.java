package com.yoyo.jingxi.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.ui.activity.ChatMainActivity;

public class AiReplyHelper {

    public static void requestAiReplySynchronous(Context context, int sessionId, int characterId, String autoReason) {
        AppDatabase db = AppDatabase.getDatabase(context);
        OpenAIManager aiManager = new OpenAIManager();

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

            com.yoyo.jingxi.data.entity.MyPersona myPersona = db.myPersonaDao().getMyPersonaByName(session.myPersonaName);
            String myName = myPersona != null ? myPersona.name : SpUtils.getString("MY_NAME", "我");
            String myPersonaDesc = myPersona != null ? myPersona.persona : SpUtils.getString("MY_PERSONA", "普通人");

            int memoryCallCount = SpUtils.getInt("SETTING_MEMORY_CALL_COUNT", 20);
            java.util.List<com.yoyo.jingxi.data.entity.Memory> importantMemories = db.memoryDao().getImportantMemoriesSync(characterId);
            java.util.List<com.yoyo.jingxi.data.entity.Memory> normalMemories = memoryCallCount > 0 ? 
                db.memoryDao().getNormalMemoriesSync(characterId, memoryCallCount) : 
                db.memoryDao().getAllNormalMemoriesSync(characterId);

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

            com.yoyo.jingxi.network.OpenAiRequest request = aiManager.buildRequestWithReason(
                character.persona, history, myName, myPersonaDesc, model, 
                importantMemories, normalMemories, new java.util.ArrayList<>(), scheduleContent, worldbookEntries, 
                emojiEntries, false, relationshipContent, maxAiMessages, momentsContent, autoReason);

            retrofit2.Response<com.yoyo.jingxi.network.OpenAiResponse> response = 
                aiManager.getApi().createChatCompletion(finalUrl, "Bearer " + apiKey, request).execute();

            if (response.isSuccessful() && response.body() != null && response.body().choices != null 
                && !response.body().choices.isEmpty()) {
                String rawContent = response.body().choices.get(0).message.content;
                
                handleAiReplies(context, aiManager, db, sessionId, character, rawContent);
                
                broadcastReplyStatus(context, false);
            } else {
                broadcastReplyStatus(context, false);
            }

        } catch (Exception e) {
            e.printStackTrace();
            com.yoyo.jingxi.data.entity.Message errorMsg = new com.yoyo.jingxi.data.entity.Message();
            errorMsg.sessionId = sessionId;
            errorMsg.characterId = characterId;
            errorMsg.content = "[系统提示: 网络请求异常，AI 回复失败。(" + e.getClass().getSimpleName() + ")]";
            errorMsg.isFromUser = false;
            errorMsg.type = 0;
            errorMsg.timestamp = System.currentTimeMillis();
            db.messageDao().insert(errorMsg);
            
            Intent updateIntent = new Intent("com.yoyo.jingxi.ACTION_MESSAGE_UPDATED");
            updateIntent.setPackage(context.getPackageName());
            updateIntent.putExtra("session_id", sessionId);
            context.sendBroadcast(updateIntent);

            broadcastReplyStatus(context, false);
        }
    }

    public static void broadcastReplyStatus(Context context, boolean isReplying) {
        Intent intent = new Intent("com.yoyo.jingxi.ACTION_AI_REPLY_STATUS");
        intent.setPackage(context.getPackageName()); // 显式广播，防止被系统限制
        intent.putExtra("is_replying", isReplying);
        context.sendBroadcast(intent);
    }

    private static void handleAiReplies(Context context, OpenAIManager aiManager, AppDatabase db, int sessionId, Character character, String rawContent) {
        java.util.List<com.yoyo.jingxi.network.OpenAIManager.ReplyItem> replies = aiManager.parseMultiReplies(rawContent);
        
        long baseTime = System.currentTimeMillis();
        boolean hasNewMessage = false;
        int newMessageCount = 0;
        String latestTextContent = "";

        for (int i = 0; i < replies.size(); i++) {
            com.yoyo.jingxi.network.OpenAIManager.ReplyItem item = replies.get(i);
            
            if ("call".equalsIgnoreCase(item.type) || item.revoke_id != null || 
                "important_memory".equalsIgnoreCase(item.type) || "memo".equalsIgnoreCase(item.type) ||
                "moment".equalsIgnoreCase(item.type) || "moment_interaction".equalsIgnoreCase(item.type)) {
                continue;
            }
            
            if (("text".equalsIgnoreCase(item.type) || item.type == null) && (item.content == null || item.content.trim().isEmpty())) {
                continue; // 过滤掉空消息，防止出现空闲气泡
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
            } else if ("virtual_image".equalsIgnoreCase(item.type)) {
                msg.type = 4;
                msg.imageDesc = item.content;
                msg.content = "[虚拟图片]";
            } else {
                msg.type = 0;
            }
            
            long msgId = db.messageDao().insert(msg);
            msg.id = (int) msgId;
            
            if (msg.type == 4) {
                com.yoyo.jingxi.utils.ImageGenerationManager.getInstance().checkAndGenerateImagesForMessage(msg);
            }
            
            hasNewMessage = true;
            newMessageCount++;
            if (msg.type == 0 || msg.type == 2) {
                latestTextContent = msg.content;
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
            
            // Introduce a short delay between messages to simulate typing/reading if it's an active session
            if (i < replies.size() - 1) {
                try {
                    Thread.sleep(1500); // 1.5 seconds delay between multiple messages
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
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
}
