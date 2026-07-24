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
import com.yoyo.jingxi.network.ApiUrlBuilder;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.utils.SpUtils;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;
import com.yoyo.jingxi.service.AiReplyService;
import com.yoyo.jingxi.ui.activity.ChatActivity;

import java.io.IOException;
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
        boolean anyRetry = false;
        boolean didSend = false;

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

            // 检查上次AI拒绝时间，冷却期内跳过（避免每15分钟重复询问浪费token）
            long lastRefusalTime = SpUtils.getLong("AUTO_MSG_LAST_REFUSAL_TIME_" + session.id, 0L);
            if (lastRefusalTime > 0 && (currentTime - lastRefusalTime) < minIdleTimeMs) {
                Log.d(TAG, "Session " + session.id + " is in cooldown after AI refusal. "
                    + (currentTime - lastRefusalTime) / 1000 / 60 + " mins since refusal, needs "
                    + minIdleTimeMs / 1000 / 60 + " mins. Skipping.");
                continue;
            }

            // 3. 构建请求，询问大模型此时是否应该主动发起聊天
            Log.d(TAG, "Asking AI for decision on session " + session.id);
            String scheduleContent = SpUtils.getString("SCHEDULE_CONTENT_" + character.id, "");
            String myName = SpUtils.getString("MY_NAME", "我");

            List<Message> historyForDecision = db.messageDao().getRecentMessagesBySessionIdSync(session.id, 10);
            java.util.Collections.reverse(historyForDecision);

            OpenAiRequest decisionRequest = buildAutoMessageDecisionRequest(character.persona, myName, scheduleContent, historyForDecision, idleTimeMs, character.nationality, character.location);

            try {
                String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/v1/");
                if (!endpoint.endsWith("/")) endpoint += "/";

                Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(ApiUrlBuilder.chatCompletions(endpoint), "Bearer " + apiKey, decisionRequest).execute();
                
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
                        // 设置冷却时间（避免 Worker 重试时重复询问）
                        SpUtils.putLong("AUTO_MSG_LAST_REFUSAL_TIME_" + session.id, currentTime);
                        SpUtils.putString("AUTO_MSG_LAST_REFUSAL_REASON_" + session.id, "");
                        didSend = true;
                        Log.d(TAG, "AI decided to send a message. Using AiReplyHelper...");
                        String reason = resultObj.optString("reason", "");
                        String actionType = resultObj.optString("action_type", "text");

                        if ("call".equals(actionType)) {
                            // 时间窗口检查（不在深夜响铃）
                            if (!isTimeInRange(startTimeStr, endTimeStr)) {
                                Log.d(TAG, "Call blocked: outside allowed time window.");
                                continue;
                            }

                            // 防止重复启动通话
                            if (com.yoyo.jingxi.ui.activity.CallActivity.instance != null
                                && !com.yoyo.jingxi.ui.activity.CallActivity.instance.isCallEnded()) {
                                Log.d(TAG, "Call blocked: another call is already active.");
                                continue;
                            }

                            String callFirstWords = resultObj.optString("call_first_words", "");
                            if (callFirstWords.isEmpty()) {
                                callFirstWords = reason;
                            }

                            // 检查用户是否正在该角色的聊天窗口中
                            int currentActiveSessionId = SpUtils.getInt("CURRENT_CHAT_SESSION_ID", -1);
                            if (currentActiveSessionId == session.id) {
                                // 用户正在该聊天窗口 → 直接全屏启动 CallActivity
                                Log.d(TAG, "AI decided to CALL. User in chat, launching CallActivity...");
                                Intent callIntent = new Intent(getApplicationContext(),
                                    com.yoyo.jingxi.ui.activity.CallActivity.class);
                                callIntent.putExtra("session_id", session.id);
                                callIntent.putExtra("is_incoming", true);
                                callIntent.putExtra("initial_message", callFirstWords);
                                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                getApplicationContext().startActivity(callIntent);
                            } else {
                                // 用户不在该聊天 → 发送横幅通知 + 铃声震动
                                Log.d(TAG, "AI decided to CALL. User NOT in chat, sending notification...");
                                com.yoyo.jingxi.utils.CallIncomingNotificationHelper.sendIncomingCallNotification(
                                    getApplicationContext(), session.id, character.id,
                                    character.name, callFirstWords);
                            }
                            break;
                        } else {
                            // 将 action_type 偏好编码进 reason 传给 Stage 2
                            String augmentedReason = reason;
                            if (!"text".equals(actionType)) {
                                augmentedReason = reason + "\n[特别提醒：你这次倾向于用" + actionType
                                    + "的形式主动联系对方，请在 replies 中体现这个偏好。]";
                            }
                            // 启动 AiReplyService（带前台通知 + WakeLock 保护），确保后台可靠执行
                            Intent serviceIntent = new Intent(getApplicationContext(), AiReplyService.class);
                            serviceIntent.setAction(AiReplyService.ACTION_START_REPLY);
                            serviceIntent.putExtra(AiReplyService.EXTRA_SESSION_ID, session.id);
                            serviceIntent.putExtra(AiReplyService.EXTRA_CHARACTER_ID, character.id);
                            serviceIntent.putExtra(AiReplyService.EXTRA_AUTO_REASON, augmentedReason);
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    getApplicationContext().startForegroundService(serviceIntent);
                                } else {
                                    getApplicationContext().startService(serviceIntent);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to start AiReplyService, fallback to direct call", e);
                                com.yoyo.jingxi.utils.AiReplyHelper.requestAiReplySynchronous(getApplicationContext(), session.id, character.id, augmentedReason);
                            }
                        }

                        // 为了避免短时间内给多个角色群发，主动发送一次后就结束此次 Worker
                        break;
                    } else {
                        String refusalReason = resultObj.optString("reason", "");
                        Log.d(TAG, "AI decided NOT to send a message. Reason: " + refusalReason);
                        // 记录拒绝时间，冷却期内不再询问（避免每15分钟重复消耗token）
                        SpUtils.putLong("AUTO_MSG_LAST_REFUSAL_TIME_" + session.id, currentTime);
                        SpUtils.putString("AUTO_MSG_LAST_REFUSAL_REASON_" + session.id, refusalReason);
                    }
                } else {
                    Log.w(TAG, "AI response failed. Code: " + response.code() + " msg: " + response.message());
                }
            } catch (IOException e) {
                Log.w(TAG, "Transient error in auto message decision for session " + session.id + ", will retry later", e);
                anyRetry = true;
                continue;
            } catch (Exception e) {
                Log.e(TAG, "Error checking auto message decision for session " + session.id, e);
                continue;
            }
        }

        return (!didSend && anyRetry) ? Result.retry() : Result.success();
    }

    private OpenAiRequest buildAutoMessageDecisionRequest(String persona, String myName, String scheduleContent, List<Message> history, long idleTimeMs, String nationality, String location) {
        OpenAiRequest request = new OpenAiRequest();
        request.model = SpUtils.getString("API_MODEL", "gpt-4o-mini");
        request.temperature = 0.8f;
        request.messages = new java.util.ArrayList<>();

        long idleHours = idleTimeMs / (1000 * 60 * 60);
        long idleMinutesRemainder = (idleTimeMs / (1000 * 60)) % 60;

        StringBuilder sysPrompt = new StringBuilder();
        sysPrompt.append("你现在正在扮演以下角色：\n").append(persona).append("\n\n")
                 .append(com.yoyo.jingxi.network.OpenAIManager.buildCultureContext(nationality, location))
                 .append("【当前真实时间】").append(formatTimestamp(System.currentTimeMillis()))
                 .append("。请严格基于这个时间做判断，不要混淆时间感。\n\n");

        if (!scheduleContent.isEmpty()) {
            sysPrompt.append("你今天的日程/状态如下：\n").append(scheduleContent).append("\n\n");
        }

        sysPrompt.append("你和用户（").append(myName).append("）已经有大约 ")
                 .append(idleHours).append(" 小时 ").append(idleMinutesRemainder).append(" 分钟没有说话了。\n\n");

        // 找到最后一条 AI 消息，帮助模型在 decision reason 中避免重复
        String lastAiContent = "";
        if (history != null && !history.isEmpty()) {
            sysPrompt.append("你们最近的聊天记录：\n");
            for (Message msg : history) {
                if (msg.type == 99 || msg.type == 100) continue;
                String sender = msg.isFromUser ? myName : "你";
                sysPrompt.append(sender).append(": ").append(msg.content).append("\n");
            }
            sysPrompt.append("\n");

            for (int i = history.size() - 1; i >= 0; i--) {
                Message msg = history.get(i);
                if (!msg.isFromUser && msg.content != null && !msg.content.trim().isEmpty()
                    && msg.type != 99 && msg.type != 100) {
                    lastAiContent = msg.content.trim();
                    break;
                }
            }
        }

        sysPrompt.append("请根据你的人设、当前的日程状态以及你们的聊天上下文，决定是否主动找用户聊天。\n")
                 .append("注意：\n")
                 .append("1. 距离上次聊天已经过去了 ").append(idleHours).append(" 个多小时，世界在向前走。")
                 .append("不要顺着上次的旧话题继续追问，要找新话题。\n")
                 .append("2. 结合现在的系统时间（").append(formatTimestamp(System.currentTimeMillis())).append("）、")
                 .append("你的日程表，寻找自然的话头（比如分享正在做的事、随口吐槽、表达想念等）。\n");

        if (!lastAiContent.isEmpty()) {
            String truncated = lastAiContent.length() > 80 ? lastAiContent.substring(0, 80) + "..." : lastAiContent;
            sysPrompt.append("3. 你最后对用户说的是：").append(truncated)
                     .append("。reason 里不要复读这个内容，想点新的。\n");
        }

        sysPrompt.append("4. 【默认选择发送】：除非现在是深夜（23:00-07:00）需要休息，或日程明确表明你在忙/睡觉，")
                 .append("否则请选择发送。高冷人设同样可以用简短冷淡但有趣的方式开口，不需要大段文字。\n")
                 .append("5. 【选择行动方式】：默认用文字（text）。")
                 .append("如果情绪很强/想让对方听到声音/不方便打字，用语音（voice）。")
                 .append("想轻松开场甩表情包用 emoji。看到有趣场景想分享照片用 virtual_image。")
                 .append("【极少使用】只有极其重要/紧急/情绪极度激动时才用电话（call），普通闲聊绝对不要打电话。\n")
                 .append("请严格以 JSON 格式返回：\n")
                 .append("{\n")
                 .append("  \"should_send\": true/false,\n")
                 .append("  \"action_type\": \"text\",\n")
                 .append("  \"reason\": \"你的内心OS或理由\",\n")
                 .append("  \"call_first_words\": \"（仅 action_type='call' 时填写）接通后说的第一句话\"\n")
                 .append("}\n\n")
                 .append("action_type 说明：\n")
                 .append("- text：绝大多数情况，发文字最自然\n")
                 .append("- voice：情绪激动、想让对方听声音、不方便打字时用\n")
                 .append("- emoji：想轻松随意开场，一个表情包胜过千言万语\n")
                 .append("- virtual_image：正好看到有趣场景想拍照分享\n")
                 .append("- call：极少用！仅限紧急/重要/情绪极度激动。一天最多一次，日常话题严禁打电话\n");

        request.messages.add(new OpenAiRequest.Message("system", sysPrompt.toString()));

        // 补充 user 消息，确保 API 请求有完整的对话轮次
        // 纯 system 消息会被很多 API 拒绝或错误处理
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("你必须严格以纯 JSON 格式返回结果（不要包含任何 markdown 代码块标记，不要带 ```json 或 ```）：\n");
        userPrompt.append("{\n");
        userPrompt.append("  \"should_send\": true 或 false,\n");
        userPrompt.append("  \"action_type\": \"text\" / \"voice\" / \"emoji\" / \"virtual_image\" / \"call\",\n");
        userPrompt.append("  \"reason\": \"你的决策原因或内心活动（一句话）\",\n");
        userPrompt.append("  \"call_first_words\": \"（仅 action_type='call' 时填写）接通后说的第一句话\"\n");
        userPrompt.append("}\n\n");
        userPrompt.append("注意：默认倾向发送（should_send: true），除非深夜或日程明确表明你在忙/睡觉。action_type 绝大多数情况用 \"text\"。reason 写一句简短的内心OS即可。");
        request.messages.add(new OpenAiRequest.Message("user", userPrompt.toString()));

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
