package com.yoyo.jingxi.worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.utils.SpUtils;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;
import com.yoyo.jingxi.service.AiReplyService;
import com.yoyo.jingxi.ui.activity.ChatActivity;

import java.util.List;

import retrofit2.Response;

public class AutoMessageWorker extends Worker {

    private static final String TAG = "AutoMessageWorker";

    public AutoMessageWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "doWork: Checking if AI should send an auto message...");

        // 1. 检查全局设置是否开启 (默认开启)
        boolean autoMsgEnabled = SpUtils.getBoolean("AUTO_MESSAGE_ENABLED", true);
        if (!autoMsgEnabled) {
            Log.d(TAG, "Auto message is disabled globally.");
            return Result.success();
        }

        String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
        if (apiKey.isEmpty()) {
            Log.d(TAG, "API key is missing.");
            return Result.failure();
        }

        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        OpenAIManager aiManager = new OpenAIManager();

        // 2. 获取所有的会话并检查
        List<ChatSession> sessions = db.chatSessionDao().getAllSessionsSync();
        if (sessions == null || sessions.isEmpty()) {
            Log.d(TAG, "No chat sessions found.");
            return Result.success();
        }

        long currentTime = System.currentTimeMillis();

        for (ChatSession session : sessions) {
            // 如果总开关被关闭，但由于某些原因worker还在运行，确保不再发消息
            if (!autoMsgEnabled) continue;

            boolean isAutoEnabled = SpUtils.getBoolean("AUTO_MESSAGE_ENABLED_" + session.id, false);
            if (!isAutoEnabled) continue;

            float intervalHours = SpUtils.getFloat("AUTO_MESSAGE_INTERVAL_" + session.id, 4.0f);
            long minIdleTimeMs = (long) (intervalHours * 60 * 60 * 1000L);
            
            String startTimeStr = SpUtils.getString("AUTO_MESSAGE_START_" + session.id, "08:00");
            String endTimeStr = SpUtils.getString("AUTO_MESSAGE_END_" + session.id, "22:00");
            
            if (!isTimeInRange(startTimeStr, endTimeStr)) {
                Log.d(TAG, "Current time is outside the allowed auto message time window for session " + session.id + ". Window: " + startTimeStr + " - " + endTimeStr);
                continue;
            }

            Character character = db.characterDao().getCharacterById(session.characterId);
            if (character == null) continue;

            // 获取最新一条消息
            List<Message> recentMessages = db.messageDao().getRecentMessagesBySessionIdSync(session.id, 1);
            if (recentMessages == null || recentMessages.isEmpty()) continue;

            Message lastMsg = recentMessages.get(0);
            
            // 只有当最后一条消息是用户发的，或者是很久以前的 AI 消息，才考虑
            long idleTimeMs = currentTime - lastMsg.timestamp;
            if (idleTimeMs < minIdleTimeMs) {
                Log.d(TAG, "Session " + session.id + " is too recent. Idle time: " + (idleTimeMs / 1000 / 60) + " mins, needs " + (minIdleTimeMs / 1000 / 60) + " mins.");
                continue;
            }

            // 3. 构建请求，询问大模型此时是否应该主动发起聊天
            Log.d(TAG, "Asking AI for decision on session " + session.id);
            String scheduleContent = SpUtils.getString("SCHEDULE_CONTENT_" + character.id, "");
            String myName = SpUtils.getString("MY_NAME", "我");
            
            List<Message> historyForDecision = db.messageDao().getRecentMessagesBySessionIdSync(session.id, 10);
            java.util.Collections.reverse(historyForDecision);

            OpenAiRequest decisionRequest = buildAutoMessageDecisionRequest(character.persona, myName, scheduleContent, historyForDecision, idleTimeMs);

            try {
                String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
                if (!endpoint.endsWith("/")) endpoint += "/";

                Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(endpoint + "v1/chat/completions", "Bearer " + apiKey, decisionRequest).execute();
                
                if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty()) {

                    String jsonContent = response.body().choices.get(0).message.content;
                    Log.d(TAG, "Decision response: " + jsonContent);
                    
                    // 解析 JSON
                    // 由于大模型返回的可能带有 ```json 标记，简单清理一下
                    if (jsonContent.contains("```json")) {
                        jsonContent = jsonContent.substring(jsonContent.indexOf("```json") + 7, jsonContent.lastIndexOf("```")).trim();
                    } else if (jsonContent.contains("```")) {
                        jsonContent = jsonContent.substring(jsonContent.indexOf("```") + 3, jsonContent.lastIndexOf("```")).trim();
                    }

                    org.json.JSONObject resultObj = new org.json.JSONObject(jsonContent);
                    boolean shouldSend = resultObj.optBoolean("should_send", false);

                    if (shouldSend) {
                        Log.d(TAG, "AI decided to send a message. Using AiReplyHelper...");
                        String reason = resultObj.optString("reason", "");
                        
                        // 直接在 Worker 线程中同步执行回复逻辑，避免在 Android 12+ 后台启动前台服务引发异常
                        com.yoyo.jingxi.utils.AiReplyHelper.requestAiReplySynchronous(getApplicationContext(), session.id, character.id, reason);
                        
                        // 为了避免短时间内给多个角色群发，主动发送一次后就结束此次 Worker
                        break; 
                    } else {
                        Log.d(TAG, "AI decided NOT to send a message. Reason: " + resultObj.optString("reason"));
                    }
                } else {
                    Log.w(TAG, "AI response failed. Code: " + response.code() + " msg: " + response.message());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking auto message decision", e);
            }
        }

        return Result.success();
    }

    private OpenAiRequest buildAutoMessageDecisionRequest(String persona, String myName, String scheduleContent, List<Message> history, long idleTimeMs) {
        OpenAiRequest request = new OpenAiRequest();
        request.model = SpUtils.getString("API_MODEL", "gpt-4o-mini");
        request.temperature = 0.8f;
        request.messages = new java.util.ArrayList<>();

        StringBuilder sysPrompt = new StringBuilder();
        sysPrompt.append("你现在正在扮演以下角色：\n").append(persona).append("\n\n")
                 .append("当前时间是: ").append(formatTimestamp(System.currentTimeMillis())).append("\n");
                 
        if (!scheduleContent.isEmpty()) {
            sysPrompt.append("你今天的日程/状态如下：\n").append(scheduleContent).append("\n\n");
        }

        long idleHours = idleTimeMs / (1000 * 60 * 60);
        sysPrompt.append("你和用户（").append(myName).append("）已经有大概 ").append(idleHours).append(" 个小时没有说话了。\n\n");

        if (history != null && !history.isEmpty()) {
            sysPrompt.append("你们最近的聊天记录：\n");
            for (Message msg : history) {
                if (msg.type == 99 || msg.type == 100) continue;
                String sender = msg.isFromUser ? myName : "你";
                sysPrompt.append(sender).append(": ").append(msg.content).append("\n");
            }
            sysPrompt.append("\n");
        }

        sysPrompt.append("请根据你的人设、当前的日程状态以及你们的聊天上下文，主动找用户聊天。\n")
                 .append("注意：\n")
                 .append("1. 既然是主动找用户，说明已经过了一段时间。不要顺着上次的旧话题继续追问，要找新话题。\n")
                 .append("2. 结合现在的系统时间、你的日程表，寻找自然的话头（比如分享正在做的事、随口吐槽、表达想念等）。内容不必隆重，微小的瞬间也是理由。\n")
                 .append("3. 【默认选择发送】：除非现在是深夜（23:00-07:00）需要休息，或日程明确表明你在忙/睡觉，否则请选择发送。高冷人设同样可以用简短冷淡但有趣的方式开口，不需要大段文字。\n")
                 .append("请严格以 JSON 格式返回：\n")
                 .append("{\n")
                 .append("  \"should_send\": true/false,\n")
                 .append("  \"reason\": \"你的内心OS或理由\"\n")
                 .append("}");

        request.messages.add(new OpenAiRequest.Message("user", sysPrompt.toString()));
        return request;
    }

    private String formatTimestamp(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
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
