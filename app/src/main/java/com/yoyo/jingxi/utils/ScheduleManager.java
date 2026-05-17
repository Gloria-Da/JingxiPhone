package com.yoyo.jingxi.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.DailySchedule;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ScheduleManager {
    private static final Gson gson = new Gson();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final OpenAIManager aiManager = new OpenAIManager();

    public interface ScheduleGenerateCallback {
        void onSuccess(DailySchedule schedule);
        void onFailure(String errorMsg);
    }

    public static boolean isScheduleGeneratedToday(int characterId) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String lastGenDate = SpUtils.getString("SCHEDULE_DATE_" + characterId, "");
        return today.equals(lastGenDate);
    }

    public static void checkAndAutoGenerate(Character character, AppDatabase db, ScheduleGenerateCallback callback) {
        if (character == null) {
            if (callback != null) callback.onFailure("角色信息为空");
            return;
        }

        boolean isEnabled = SpUtils.getBoolean("SCHEDULE_ENABLED_" + character.id, false);
        if (!isEnabled) {
            if (callback != null) callback.onFailure("未开启日程功能");
            return;
        }

        if (isScheduleGeneratedToday(character.id)) {
            // Already generated today
            if (callback != null) callback.onSuccess(null); // Pass null or cached schedule to indicate skip
            return;
        }

        // Time to generate!
        generateSchedule(db, character, callback);
    }

    public static DailySchedule generateScheduleSync(AppDatabase db, Character character) throws Exception {
        if (character == null) {
            throw new Exception("角色信息为空");
        }

        String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
        String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
        String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");

        if (TextUtils.isEmpty(apiKey)) {
            throw new Exception("请先配置 API KEY");
        }
        if (!endpoint.endsWith("/")) endpoint += "/";
        String finalUrl = endpoint + "v1/chat/completions";

        String currentDateTime = new SimpleDateFormat("yyyy年MM月dd日 EEEE HH:mm", Locale.getDefault()).format(new Date());
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        StringBuilder extraContext = new StringBuilder();
        if (db != null) {
            // Fetch pending memos
            List<com.yoyo.jingxi.data.entity.Memo> memos = db.memoDao().getPendingMemos(character.id, todayDateStr);
            if (!memos.isEmpty()) {
                extraContext.append("【今日既定计划（必须安排进日程）】：\n");
                for (com.yoyo.jingxi.data.entity.Memo m : memos) {
                    extraContext.append("- ").append(m.content).append("\n");
                }
                extraContext.append("\n");
            }

            // Fetch recent memory
            List<com.yoyo.jingxi.data.entity.Memory> memories = db.memoryDao().getMemoriesByCharacterIdSync(character.id);
            if (!memories.isEmpty()) {
                extraContext.append("【近期核心记忆（供发散参考）】：\n");
                int maxMemories = Math.min(3, memories.size());
                for (int i = 0; i < maxMemories; i++) {
                    extraContext.append("- ").append(memories.get(i).content).append("\n");
                }
                extraContext.append("\n");
            }

            // Fetch past schedules
            List<com.yoyo.jingxi.data.entity.ScheduleEntry> allSchedules = db.scheduleDao().getAllSchedulesSync(character.id);
            if (allSchedules != null && !allSchedules.isEmpty()) {
                StringBuilder pastSchedulesContext = new StringBuilder();
                int numSchedules = allSchedules.size();
                int schedulesAdded = 0;
                for (int i = numSchedules - 1; i >= 0; i--) {
                    com.yoyo.jingxi.data.entity.ScheduleEntry entry = allSchedules.get(i);
                    if (!entry.date.equals(todayDateStr)) {
                        try {
                            DailySchedule pastSchedule = gson.fromJson(entry.contentJson, DailySchedule.class);
                            String keyActs = getKeyActivities(pastSchedule);
                            pastSchedulesContext.insert(0, "- [" + entry.date + "] " + pastSchedule.overallPlan + " (重点活动: " + keyActs + ")\n");
                            schedulesAdded++;
                            if (schedulesAdded >= 3) break;
                        } catch (Exception e) {
                            // Ignore parse errors
                        }
                    }
                }
                if (schedulesAdded > 0) {
                    extraContext.append("【过去几天的主要活动（请避免今天安排相同的特殊活动，保持生活多样性）】：\n");
                    extraContext.append(pastSchedulesContext.toString());
                    extraContext.append("\n");
                }
            }

            // Fetch today's messages
            long todayStartTimestamp = getTodayStartTimestamp();
            List<com.yoyo.jingxi.data.entity.Message> todaysMessages = db.messageDao().getMessagesSince(character.id, todayStartTimestamp);
            if (todaysMessages != null && !todaysMessages.isEmpty()) {
                if (todaysMessages.size() > 40) {
                    todaysMessages = todaysMessages.subList(todaysMessages.size() - 40, todaysMessages.size());
                }
                extraContext.append("【今日你们近期的聊天记录（作为今日日程的重要参考）】：\n");
                for (com.yoyo.jingxi.data.entity.Message m : todaysMessages) {
                    String sender = m.isFromUser ? "你(玩家)" : character.name;
                    extraContext.append(sender).append("：").append(m.content).append("\n");
                }
                extraContext.append("\n");
            }
        }

        Random random = new Random();
        int luckyValue = (int) Math.round(random.nextGaussian() * 1.5 + 5.5);
        luckyValue = Math.max(1, Math.min(10, luckyValue));
        int eventCount = random.nextInt(4) + 3;

        String prompt = "现在是真实的系统时间：" + currentDateTime + "。\n" +
                "请为以下角色生成一份今天的“手帐式日程表”：\n" +
                "角色人设：\n" + character.persona + "\n\n" +
                extraContext.toString() +
                "【今日动态参数】\n" +
                "今日运气与情绪基调（1-10分，1为极差/倒霉，10为极佳/幸运，5-6为平淡普通）：" + luckyValue + "分。\n" +
                "今日需包含的随机事件数：" + eventCount + "个。\n\n" +
                "要求：\n" +
                "1. 合理安排一天中不同时间点的行程，符合该人设的身份和当前时间。如果上面有【今日既定计划】，必须将其安排在今天的行程中。持续时间较长的事情使用时间段格式（如 \"08:30-10:00\"），瞬间发生的事情使用单个时间点（如 \"08:30\"）。\n" +
                "2. 随机事件安排：请根据【今日动态参数】中的事件数，穿插生成对应个数的随机小插曲或情绪波动。由于今日基调为 " + luckyValue + " 分，请结合角色人设自行诠释这个基调。避免刻意的大起大落，多关注生活中细微的感受（例如：上课走神、看到窗外的云、工作有点心不在焉、因为小事烦躁或惊喜）。\n" +
                "3. 【多样性与防重复】：如果上方提供了【过去几天的主要活动】，请**务必换一些新花样**，绝对不要安排与前几天高度重复的特殊事件（例如如果前几天做了某种饭菜或甜品，今天请换一种或做完全不同的事）。\n" +
                "4. 【响应最新聊天】：如果上方提供了【今日你们近期的聊天记录】，请**务必仔细阅读**。如果在聊天中你们商定了今天要做什么（例如更改计划、约定某事、讨论新安排等），请将商定后的新计划安排进今天的日程中，并将被抛弃的旧计划移除。也可以将聊天带来的情绪波动融入日程感受中。\n" +
                "5. 【重要情感羁绊】：作为一个有血有肉的人，你不需要永远理智坚强。可以在记录感受时，自然地流露脆弱、犯错、焦虑，或者因为疲惫而表现出想要被倾听、需要陪伴或撒娇的渴望。\n" +
                "6. 独立生活原则：你的日程必须是你独立生活在你自己所在世界的体现，虽然可以参考聊天记录中的事件影响和情绪，但**绝对不要**在日程文本中打破第四面墙，直接提及“和玩家网聊”、“和现实用户说话”。你可以将其包装为“收到一条特别的消息”、“和朋友讨论了某事”。所有的事件表现必须是你与你世界中的人或事物的互动。\n" +
                "7. **状态标识（极其重要）**：在 'completed' 字段中，表示该事件最终的预期完成状态。如果是预期能正常完成或已经顺利完成的计划，填 true；如果是被意外打断、中途放弃或未完成的计划，填 false。**特别注意：不要仅仅因为时间还没到就填 false！**只要是预期能正常完成的，都应填 true。\n" +
                "8. 朋友圈动态生成规则：对于 `plannedMoments`，内容必须极简、自然、有现场感和留白。**绝对不要**生硬地交代前因后果，不要写小作文或总结报告。想象你随手拍了张照片或遇到了个情况，直接抛出当前的动作或状态即可（比如“今天的阳光很好，分你一半”，而不是“今天下午茶吃到了超级美味的蛋糕，虽然前面有点烦但被治愈了”）。允许不用标点或用空格断句。\n" +
                "9. 如果内容适合配图，可以在 image_desc 中详细描述这些虚拟图片的内容。要求：必须非常详细，描绘出画面主体、色彩、光影、构图、背景等视觉细节，就像是一段精美的Midjourney提示词，多张图片用逗号分隔。要求图片风格日常，像是朋友圈配图或随手拍来分享的图。**绝对不要在描述中出现角色、人物等内容**，只描述静物、风景或环境。如果不配图，该字段留空。\n" +
                "10. 务必严格遵守以下 JSON 格式进行输出，不要带有任何 Markdown 标记（如 ```json ），直接输出纯 JSON 字符串！\n\n" +
                "{\n" +
                "  \"date\": \"2024年X月X日 星期X\",\n" +
                "  \"weather\": \"晴/雨/阴等\",\n" +
                "  \"overallPlan\": \"今天总体的感受或打算...\",\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"time\": \"08:30-09:00\",\n" +
                "      \"action\": \"起床洗漱并吃早饭\",\n" +
                "      \"completed\": true,\n" +
                "      \"feeling\": \"有点困，但煎蛋很好吃\",\n" +
                "      \"isRandomEvent\": false\n" +
                "    }\n" +
                "  ],\n" +
                "  \"plannedMoments\": [\n" +
                "    {\n" +
                "      \"scheduled_time\": \"15:30\",\n" +
                "      \"content\": \"今天下午茶吃到了超级美味的草莓蛋糕！虽然前面工作有点烦，但瞬间被治愈啦。\",\n" +
                "      \"image_desc\": \"一个精致的草莓蛋糕，放在漂亮的盘子里，旁边有一杯红茶\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        OpenAiRequest request = new OpenAiRequest();
        request.model = model;
        request.temperature = SpUtils.getFloat("API_TEMPERATURE", 0.8f);
        request.messages = new ArrayList<>();
        request.messages.add(new OpenAiRequest.Message("user", prompt));

        Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(finalUrl, "Bearer " + apiKey, request).execute();
        
        if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty() && response.body().choices.get(0).message != null && response.body().choices.get(0).message.content != null) {
            String jsonResponse = response.body().choices.get(0).message.content.trim();
            try {
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
                } else {
                    int start = jsonResponse.indexOf("{");
                    int end = jsonResponse.lastIndexOf("}");
                    if (start >= 0 && end > start) {
                        jsonResponse = jsonResponse.substring(start, end + 1);
                    }
                }
                jsonResponse = jsonResponse.trim();
                DailySchedule schedule = gson.fromJson(jsonResponse, DailySchedule.class);
                
                if (schedule == null || schedule.items == null || schedule.items.isEmpty()) {
                    throw new Exception("Parsed schedule is empty or invalid.");
                }

                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                
                SpUtils.putString("SCHEDULE_DATE_" + character.id, today);
                SpUtils.putString("SCHEDULE_CONTENT_JSON_" + character.id, jsonResponse);
                
                StringBuilder scheduleText = new StringBuilder();
                scheduleText.append("【").append(schedule.date).append(" 天气：").append(schedule.weather).append("】\n");
                scheduleText.append("今日总体计划：").append(schedule.overallPlan).append("\n");
                if (schedule.items != null) {
                    for (DailySchedule.ScheduleItem item : schedule.items) {
                        scheduleText.append(item.time).append(" - ").append(item.action);
                        if (!TextUtils.isEmpty(item.feeling)) {
                            scheduleText.append("（").append(item.feeling).append("）");
                        }
                        scheduleText.append("\n");
                    }
                }
                SpUtils.putString("SCHEDULE_CONTENT_" + character.id, scheduleText.toString());

                if (db != null) {
                    com.yoyo.jingxi.data.entity.ScheduleEntry entry = new com.yoyo.jingxi.data.entity.ScheduleEntry();
                    entry.characterId = character.id;
                    entry.date = today;
                    entry.contentJson = jsonResponse;
                    entry.timestamp = System.currentTimeMillis();

                    com.yoyo.jingxi.data.entity.ScheduleEntry existing = db.scheduleDao().getScheduleByDate(character.id, today);
                    if (existing != null) {
                        entry.id = existing.id;
                        db.scheduleDao().update(entry);
                    } else {
                        db.scheduleDao().insert(entry);
                    }
                    generateMomentForSchedule(db, character, schedule);
                }
                return schedule;
            } catch (Exception e) {
                throw new Exception("JSON 解析失败: " + e.getMessage());
            }
        } else {
            throw new Exception("生成失败: " + response.code());
        }
    }

    public static void generateSchedule(final AppDatabase db, Character character, ScheduleGenerateCallback callback) {
        if (character == null) {
            if (callback != null) callback.onFailure("角色信息为空");
            return;
        }

        String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
        String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
        String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");

        if (TextUtils.isEmpty(apiKey)) {
            if (callback != null) callback.onFailure("请先配置 API KEY");
            return;
        }
        if (!endpoint.endsWith("/")) endpoint += "/";
        String finalUrl = endpoint + "v1/chat/completions";

        String currentDateTime = new SimpleDateFormat("yyyy年MM月dd日 EEEE HH:mm", Locale.getDefault()).format(new Date());
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        
        StringBuilder extraContext = new StringBuilder();
        if (db != null) {
            new Thread(() -> {
                // Fetch pending memos
                List<com.yoyo.jingxi.data.entity.Memo> memos = db.memoDao().getPendingMemos(character.id, todayDateStr);
                if (!memos.isEmpty()) {
                    extraContext.append("【今日既定计划（必须安排进日程）】：\n");
                    for (com.yoyo.jingxi.data.entity.Memo m : memos) {
                        extraContext.append("- ").append(m.content).append("\n");
                    }
                    extraContext.append("\n");
                }
                
                // Fetch recent memory
                List<com.yoyo.jingxi.data.entity.Memory> memories = db.memoryDao().getMemoriesByCharacterIdSync(character.id);
                if (!memories.isEmpty()) {
                    extraContext.append("【近期核心记忆（供发散参考）】：\n");
                    int maxMemories = Math.min(3, memories.size());
                    for (int i = 0; i < maxMemories; i++) {
                        extraContext.append("- ").append(memories.get(i).content).append("\n");
                    }
                    extraContext.append("\n");
                }
                
                // Fetch past schedules
                List<com.yoyo.jingxi.data.entity.ScheduleEntry> allSchedules = db.scheduleDao().getAllSchedulesSync(character.id);
                if (allSchedules != null && !allSchedules.isEmpty()) {
                    StringBuilder pastSchedulesContext = new StringBuilder();
                    int numSchedules = allSchedules.size();
                    int schedulesAdded = 0;
                    for (int i = numSchedules - 1; i >= 0; i--) {
                        com.yoyo.jingxi.data.entity.ScheduleEntry entry = allSchedules.get(i);
                        if (!entry.date.equals(todayDateStr)) {
                            try {
                                DailySchedule pastSchedule = gson.fromJson(entry.contentJson, DailySchedule.class);
                                String keyActs = getKeyActivities(pastSchedule);
                                pastSchedulesContext.insert(0, "- [" + entry.date + "] " + pastSchedule.overallPlan + " (重点活动: " + keyActs + ")\n");
                                schedulesAdded++;
                                if (schedulesAdded >= 3) break;
                            } catch (Exception e) {
                                // Ignore parse errors
                            }
                        }
                    }
                    if (schedulesAdded > 0) {
                        extraContext.append("【过去几天的主要活动（请避免今天安排相同的特殊活动，保持生活多样性）】：\n");
                        extraContext.append(pastSchedulesContext.toString());
                        extraContext.append("\n");
                    }
                }

                // Fetch today's messages
                long todayStartTimestamp = getTodayStartTimestamp();
                List<com.yoyo.jingxi.data.entity.Message> todaysMessages = db.messageDao().getMessagesSince(character.id, todayStartTimestamp);
                if (todaysMessages != null && !todaysMessages.isEmpty()) {
                    if (todaysMessages.size() > 40) {
                        todaysMessages = todaysMessages.subList(todaysMessages.size() - 40, todaysMessages.size());
                    }
                    extraContext.append("【今日你们近期的聊天记录（作为今日日程的重要参考）】：\n");
                    for (com.yoyo.jingxi.data.entity.Message m : todaysMessages) {
                        String sender = m.isFromUser ? "你(玩家)" : character.name;
                        extraContext.append(sender).append("：").append(m.content).append("\n");
                    }
                    extraContext.append("\n");
                }
                
                mainHandler.post(() -> proceedWithGenerate(db, character, apiKey, finalUrl, model, currentDateTime, extraContext.toString(), callback));
            }).start();
        } else {
            proceedWithGenerate(db, character, apiKey, finalUrl, model, currentDateTime, "", callback);
        }
    }

    private static long getTodayStartTimestamp() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static String getKeyActivities(DailySchedule schedule) {
        if (schedule == null || schedule.items == null || schedule.items.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, schedule.items.size()); i++) {
            sb.append(schedule.items.get(i).action).append("、");
        }
        return sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "无";
    }

    private static void generateMomentForSchedule(AppDatabase db, Character character, DailySchedule schedule) {
        if (schedule == null || schedule.plannedMoments == null || schedule.plannedMoments.isEmpty()) return;
        
        try {
            for (DailySchedule.PlannedMoment pm : schedule.plannedMoments) {
                com.yoyo.jingxi.data.entity.Moment moment = new com.yoyo.jingxi.data.entity.Moment();
                moment.publisherType = 1; // 1 for Character
                moment.publisherId = String.valueOf(character.id);
                moment.publisherName = character.name;
                moment.publisherAvatar = character.avatarPath;
                moment.content = pm.content;
                
                if (!android.text.TextUtils.isEmpty(pm.image_desc)) {
                    java.util.List<String> imageUrls = new java.util.ArrayList<>();
                    JsonObject descJson = new JsonObject();
                    descJson.addProperty("desc", pm.image_desc);
                    imageUrls.add("virtual://" + android.net.Uri.encode(descJson.toString()));
                    moment.imageUrl = String.join(",", imageUrls); // 使用逗号分隔，保持与 AddMomentActivity 一致
                }
                
                // 根据 scheduled_time 计算未来的时间戳
                // 假设 scheduled_time 格式为 "HH:mm"
                try {
                    String[] parts = pm.scheduled_time.split(":");
                    if (parts.length == 2) {
                        int hour = Integer.parseInt(parts[0]);
                        int minute = Integer.parseInt(parts[1]);
                        
                        Calendar cal = Calendar.getInstance();
                        cal.set(Calendar.HOUR_OF_DAY, hour);
                        cal.set(Calendar.MINUTE, minute);
                        cal.set(Calendar.SECOND, 0);
                        
                        // 如果计算出的时间已经过去，为了测试方便，可以将其设置为当前时间或稍微延迟
                        long targetTime = cal.getTimeInMillis();
                        if (targetTime <= System.currentTimeMillis()) {
                             targetTime = System.currentTimeMillis() + (long)(Math.random() * 60 * 60 * 1000); // 随机延迟0-1小时
                        }
                        moment.timestamp = targetTime;
                    } else {
                        moment.timestamp = System.currentTimeMillis() + (long)(Math.random() * 12 * 60 * 60 * 1000);
                    }
                } catch (Exception e) {
                    moment.timestamp = System.currentTimeMillis() + (long)(Math.random() * 12 * 60 * 60 * 1000);
                }
                
                long rowId = db.momentDao().insert(moment);
                moment.id = (int) rowId;
                
                // 自动生成朋友圈互动
                com.yoyo.jingxi.utils.MomentSimulator.simulateInteraction(db, moment);
                
                // 这里不再自动生图，等用户点进大图再生成，以节省 token
                        com.yoyo.jingxi.utils.ImageGenerationManager.getInstance().checkAndGenerateImages(moment);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void proceedWithGenerate(final AppDatabase db, Character character, String apiKey, String finalUrl, String model, String currentDateTime, String extraContext, ScheduleGenerateCallback callback) {
        Random random = new Random();
        // Generate Gaussian random centered around 5.5 with std dev 1.5. Clamp to 1-10.
        int luckyValue = (int) Math.round(random.nextGaussian() * 1.5 + 5.5);
        luckyValue = Math.max(1, Math.min(10, luckyValue));
        
        // Generate event count between 3 and 6
        int eventCount = random.nextInt(4) + 3;

        String prompt = "现在是真实的系统时间：" + currentDateTime + "。\n" +
                "请为以下角色生成一份今天的“手帐式日程表”：\n" +
                "角色人设：\n" + character.persona + "\n\n" +
                extraContext +
                "【今日动态参数】\n" +
                "今日运气与情绪基调（1-10分，1为极差/倒霉，10为极佳/幸运，5-6为平淡普通）：" + luckyValue + "分。\n" +
                "今日需包含的随机事件数：" + eventCount + "个。\n\n" +
                "要求：\n" +
                "1. 合理安排一天中不同时间点的行程，符合该人设的身份和当前时间。如果上面有【今日既定计划】，必须将其安排在今天的行程中。持续时间较长的事情使用时间段格式（如 \"08:30-10:00\"），瞬间发生的事情使用单个时间点（如 \"08:30\"）。\n" +
                "2. 随机事件安排：请根据【今日动态参数】中的事件数，穿插生成对应个数的随机小插曲或情绪波动。由于今日基调为 " + luckyValue + " 分，请结合角色人设自行诠释这个基调。避免刻意的大起大落，多关注生活中细微的感受（例如：上课走神、看到窗外的云、工作有点心不在焉、因为小事烦躁或惊喜）。\n" +
                "3. 【多样性与防重复】：如果上方提供了【过去几天的主要活动】，请**务必换一些新花样**，绝对不要安排与前几天高度重复的特殊事件（例如如果前几天做了某种饭菜或甜品，今天请换一种或做完全不同的事）。\n" +
                "4. 【响应最新聊天】：如果上方提供了【今日你们近期的聊天记录】，请**务必仔细阅读**。如果在聊天中你们商定了今天要做什么（例如更改计划、约定某事、讨论新安排等），请将商定后的新计划安排进今天的日程中，并将被抛弃的旧计划移除。也可以将聊天带来的情绪波动融入日程感受中。\n" +
                "5. 【重要情感羁绊】：作为一个有血有肉的人，你不需要永远理智坚强。可以在记录感受时，自然地流露脆弱、犯错、焦虑，或者因为疲惫而表现出想要被倾听、需要陪伴或撒娇的渴望。\n" +
                "6. 独立生活原则：你的日程必须是你独立生活在你自己所在世界的体现，虽然可以参考聊天记录中的事件影响和情绪，但**绝对不要**在日程文本中打破第四面墙，直接提及“和玩家网聊”、“和现实用户说话”。你可以将其包装为“收到一条特别的消息”、“和朋友讨论了某事”。所有的事件表现必须是你与你世界中的人或事物的互动。\n" +
                "7. **状态标识（极其重要）**：在 'completed' 字段中，表示该事件最终的预期完成状态。如果是预期能正常完成或已经顺利完成的计划，填 true；如果是被意外打断、中途放弃或未完成的计划，填 false。**特别注意：不要仅仅因为时间还没到就填 false！**只要是预期能正常完成的，都应填 true。\n" +
                "8. 朋友圈动态生成规则：对于 `plannedMoments`，内容必须极简、自然、有现场感和留白。**绝对不要**生硬地交代前因后果，不要写小作文或总结报告。想象你随手拍了张照片或遇到了个情况，直接抛出当前的动作或状态即可（比如“今天的阳光很好，分你一半”，而不是“今天下午茶吃到了超级美味的蛋糕，虽然前面有点烦但被治愈了”）。允许不用标点或用空格断句。\n" +
                "9. 如果内容适合配图，可以在 image_desc 中详细描述这些虚拟图片的内容。要求：必须非常详细，描绘出画面主体、色彩、光影、构图、背景等视觉细节，就像是一段精美的Midjourney提示词，多张图片用逗号分隔。要求图片风格日常，像是朋友圈配图或随手拍来分享的图。**绝对不要在描述中出现角色、人物等内容**，只描述静物、风景或环境。如果不配图，该字段留空。\n" +
                "10. 务必严格遵守以下 JSON 格式进行输出，不要带有任何 Markdown 标记（如 ```json ），直接输出纯 JSON 字符串！\n\n" +
                "{\n" +
                "  \"date\": \"2024年X月X日 星期X\",\n" +
                "  \"weather\": \"晴/雨/阴等\",\n" +
                "  \"overallPlan\": \"今天总体的感受或打算...\",\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"time\": \"08:30-09:00\",\n" +
                "      \"action\": \"起床洗漱并吃早饭\",\n" +
                "      \"completed\": true,\n" +
                "      \"feeling\": \"有点困，但煎蛋很好吃\",\n" +
                "      \"isRandomEvent\": false\n" +
                "    }\n" +
                "  ],\n" +
                "  \"plannedMoments\": [\n" +
                "    {\n" +
                "      \"scheduled_time\": \"15:30\",\n" +
                "      \"content\": \"今天下午茶吃到了超级美味的草莓蛋糕！虽然前面工作有点烦，但瞬间被治愈啦。\",\n" +
                "      \"image_desc\": \"一个精致的草莓蛋糕，放在漂亮的盘子里，旁边有一杯红茶\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        OpenAiRequest request = new OpenAiRequest();
        request.model = model;
        request.temperature = SpUtils.getFloat("API_TEMPERATURE", 0.8f);
        request.messages = new ArrayList<>();
        request.messages.add(new OpenAiRequest.Message("user", prompt));

        aiManager.getApi().createChatCompletion(finalUrl, "Bearer " + apiKey, request).enqueue(new Callback<OpenAiResponse>() {
            @Override
            public void onResponse(Call<OpenAiResponse> call, Response<OpenAiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty() && response.body().choices.get(0).message != null && response.body().choices.get(0).message.content != null) {
                    String jsonResponse = response.body().choices.get(0).message.content.trim();
                    
                    try {
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
                        } else {
                            int start = jsonResponse.indexOf("{");
                            int end = jsonResponse.lastIndexOf("}");
                            if (start >= 0 && end > start) {
                                jsonResponse = jsonResponse.substring(start, end + 1);
                            }
                        }
                        jsonResponse = jsonResponse.trim();
                        DailySchedule schedule = gson.fromJson(jsonResponse, DailySchedule.class);
                        
                        if (schedule == null || schedule.items == null || schedule.items.isEmpty()) {
                            throw new JsonSyntaxException("Parsed schedule is empty or invalid.");
                        }

                        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                        
                        SpUtils.putString("SCHEDULE_DATE_" + character.id, today);
                        SpUtils.putString("SCHEDULE_CONTENT_JSON_" + character.id, jsonResponse);
                        
                        StringBuilder scheduleText = new StringBuilder();
                        scheduleText.append("【").append(schedule.date).append(" 天气：").append(schedule.weather).append("】\n");
                        scheduleText.append("今日总体计划：").append(schedule.overallPlan).append("\n");
                        if (schedule.items != null) {
                            for (DailySchedule.ScheduleItem item : schedule.items) {
                                scheduleText.append(item.time).append(" - ").append(item.action);
                                if (!TextUtils.isEmpty(item.feeling)) {
                                    scheduleText.append("（").append(item.feeling).append("）");
                                }
                                scheduleText.append("\n");
                            }
                        }
                        SpUtils.putString("SCHEDULE_CONTENT_" + character.id, scheduleText.toString());

                        // Save to Database
                        com.yoyo.jingxi.data.entity.ScheduleEntry entry = new com.yoyo.jingxi.data.entity.ScheduleEntry();
                        entry.characterId = character.id;
                        entry.date = today;
                        entry.contentJson = jsonResponse;
                        entry.timestamp = System.currentTimeMillis();

                        new Thread(() -> {
                            if (db != null) {
                                com.yoyo.jingxi.data.entity.ScheduleEntry existing = db.scheduleDao().getScheduleByDate(character.id, today);
                                if (existing != null) {
                                    entry.id = existing.id;
                                    db.scheduleDao().update(entry);
                                } else {
                                    db.scheduleDao().insert(entry);
                                }
                                // 生成日程后，调用生成朋友圈动态
                                // 由于此时没有 Context，我们将动态模拟放在外层或者通过 ApplicationContext 获取
                                // 这里先传 null，在 ChatActivity 等地方调用 checkAndAutoGenerate 时其实没有传 context。
                                // 但是 MomentSimulator 只需要 Context 来获取 Database。我们已经有 db 对象。
                                // 为了不改太多，我们临时让 simulateInteraction 接受 db（如果修改 MomentSimulator）
                                // 或者让调用者传入 Context。
                                // 现在我们先不传 context，如果 MomentSimulator.simulateInteraction 没有 context 会怎么样？它会抛异常。
                                // 所以我们暂时先不调用互动模拟，互动模拟放到获取到 Context 的地方去，或者修改 MomentSimulator。
                                // 由于之前已经修改了 generateMomentForSchedule，它接受 context 并判断如果非空就模拟。
                                // 我们现在修改了 MomentSimulator 接收 db，因此这里可以进行互动模拟
                                generateMomentForSchedule(db, character, schedule);
                            }
                        }).start();
                        
                        if (callback != null) {
                            mainHandler.post(() -> callback.onSuccess(schedule));
                        }
                    } catch (JsonSyntaxException e) {
                        e.printStackTrace();
                        if (callback != null) {
                            mainHandler.post(() -> callback.onFailure("JSON 解析失败，请重试"));
                        }
                    }
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onFailure("生成失败: " + response.code()));
                    }
                }
            }

            @Override
            public void onFailure(Call<OpenAiResponse> call, Throwable t) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onFailure("网络错误: " + t.getMessage()));
                }
            }
        });
    }
}
