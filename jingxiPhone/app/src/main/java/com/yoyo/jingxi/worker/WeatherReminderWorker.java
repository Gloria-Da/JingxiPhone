package com.yoyo.jingxi.worker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yoyo.jingxi.JingxiApplication;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.data.entity.MyPersona;
import com.yoyo.jingxi.network.OpenAiApi;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;
import com.yoyo.jingxi.network.OpenMeteoApi;
import com.yoyo.jingxi.network.QWeatherApi;
import com.yoyo.jingxi.utils.SpUtils;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class WeatherReminderWorker extends Worker {

    private static final String TAG = "WeatherReminderWorker";
    private final Gson gson = new Gson();

    public WeatherReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting WeatherReminderWorker...");

        // 1. 检查是否开启每日便签
        if (!SpUtils.getBoolean("WEATHER_NOTE_ENABLED", false)) {
            Log.d(TAG, "Weather note generation disabled, skipping.");
            // 重新调度下一天
            JingxiApplication.scheduleWeatherReminderAtTime();
            return Result.success();
        }

        // 2. 读取多选角色列表，随机选一个
        String idsStr = SpUtils.getString("WEATHER_NOTE_CHARACTER_IDS", "");
        if (idsStr.isEmpty()) {
            Log.d(TAG, "No characters selected for weather note, skipping.");
            JingxiApplication.scheduleWeatherReminderAtTime();
            return Result.success();
        }

        String[] ids = idsStr.split(",");
        int randomIndex = new Random().nextInt(ids.length);
        int characterId;
        try {
            characterId = Integer.parseInt(ids[randomIndex].trim());
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid character ID in list");
            return Result.failure();
        }

        // 3. 检查今日是否已为此角色生成过
        String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String cacheKey = "WEATHER_REMINDER_" + characterId + "_" + dateKey;
        String cachedReminder = SpUtils.getString(cacheKey, "");
        if (!cachedReminder.isEmpty()) {
            Log.d(TAG, "Weather note already generated for character " + characterId + " today.");
            JingxiApplication.scheduleWeatherReminderAtTime();
            return Result.success();
        }

        // 保存当日生成角色 ID（供天气页头像显示）
        SpUtils.putInt("WEATHER_REMINDER_CHARACTER_ID", characterId);

        // 4. 获取天气数据（当日总体天气 + 可选逐小时）
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("jingxi_prefs", Context.MODE_PRIVATE);
        String apiType = prefs.getString("WEATHER_DETAIL_API_TYPE", "");
        String dailyJson = prefs.getString("WEATHER_DETAIL_DAILY_JSON", "");
        String hourlyJson = prefs.getString("WEATHER_DETAIL_HOURLY_JSON", "");

        String dailyWeather = "";
        String maxTemp = "";
        String minTemp = "";
        String hourlyInfo = "";

        try {
            if ("qweather".equals(apiType) && !dailyJson.isEmpty()) {
                Type type = new TypeToken<List<QWeatherApi.QWeatherDailyResponse.Daily>>() {}.getType();
                List<QWeatherApi.QWeatherDailyResponse.Daily> dailyList = gson.fromJson(dailyJson, type);
                if (dailyList != null && !dailyList.isEmpty()) {
                    dailyWeather = dailyList.get(0).textDay;
                    maxTemp = dailyList.get(0).tempMax + "°C";
                    minTemp = dailyList.get(0).tempMin + "°C";
                }
            } else if ("openmeteo".equals(apiType) && !dailyJson.isEmpty()) {
                OpenMeteoApi.OpenMeteoResponse.Daily daily = gson.fromJson(dailyJson, OpenMeteoApi.OpenMeteoResponse.Daily.class);
                if (daily != null && daily.temperature_2m_max != null && !daily.temperature_2m_max.isEmpty()) {
                    if (daily.weathercode != null && !daily.weathercode.isEmpty()) {
                        dailyWeather = getWeatherDesc(daily.weathercode.get(0));
                    }
                    maxTemp = Math.round(daily.temperature_2m_max.get(0)) + "°C";
                    minTemp = Math.round(daily.temperature_2m_min.get(0)) + "°C";
                }
            }

            // 逐小时天气
            if (SpUtils.getBoolean("WEATHER_NOTE_HOURLY_ENABLED", false) && !hourlyJson.isEmpty()) {
                hourlyInfo = buildHourlyInfo(apiType, hourlyJson);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing weather data for note generation", e);
        }

        if (dailyWeather.isEmpty() && maxTemp.isEmpty()) {
            Log.w(TAG, "No daily weather data available, retrying later.");
            return Result.retry();
        }

        // 5. 获取角色信息和主人设会话
        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        Character character = db.characterDao().getCharacterById(characterId);
        if (character == null) {
            Log.e(TAG, "Character not found: " + characterId);
            return Result.failure();
        }

        // 找到主人设
        MyPersona mainPersona = db.myPersonaDao().getMainPersona();
        String myPersonaName = mainPersona != null ? mainPersona.name : SpUtils.getString("MY_NAME", "我");
        String myPersonaDesc = mainPersona != null ? mainPersona.persona : SpUtils.getString("MY_PERSONA", "普通人");

        // 查找主人设+该角色的会话
        ChatSession targetSession = null;
        List<ChatSession> sessions = db.chatSessionDao().getAllSessionsSync();
        if (sessions != null) {
            for (ChatSession s : sessions) {
                if (s.characterId == characterId && myPersonaName.equals(s.myPersonaName)) {
                    if (targetSession == null || s.lastMessageTimestamp > targetSession.lastMessageTimestamp) {
                        targetSession = s;
                    }
                }
            }
        }

        // 如果不存在该会话，回退到该角色的任意会话
        if (targetSession == null && sessions != null) {
            for (ChatSession s : sessions) {
                if (s.characterId == characterId) {
                    if (targetSession == null || s.lastMessageTimestamp > targetSession.lastMessageTimestamp) {
                        targetSession = s;
                    }
                }
            }
        }

        int historyCount = SpUtils.getInt("SETTING_HISTORY_ROUNDS", 80);
        List<Message> history = null;
        if (targetSession != null) {
            history = db.messageDao().getRecentMessagesBySessionIdSync(targetSession.id, historyCount * 2);
            if (history != null) {
                Collections.reverse(history);
            }
        }

        // 世界书
        String worldbookContent = "";
        List<com.yoyo.jingxi.data.entity.WorldbookEntry> entries = db.worldbookDao().getAllEnabledEntriesSync();
        if (entries != null && !entries.isEmpty()) {
            StringBuilder wbBuilder = new StringBuilder();
            for (com.yoyo.jingxi.data.entity.WorldbookEntry entry : entries) {
                wbBuilder.append(entry.keyword).append(": ").append(entry.content).append("\n");
            }
            worldbookContent = wbBuilder.toString();
        }

        // 记忆
        int memoryCallCount = SpUtils.getInt("SETTING_MEMORY_CALL_COUNT", 20);
        List<com.yoyo.jingxi.data.entity.Memory> importantMemories = db.memoryDao().getImportantMemoriesSyncAll(characterId);
        List<com.yoyo.jingxi.data.entity.Memory> normalMemories = memoryCallCount > 0 ?
            db.memoryDao().getNormalMemoriesSyncAll(characterId, memoryCallCount) :
            db.memoryDao().getAllNormalMemoriesSyncAll(characterId);

        StringBuilder memBuilder = new StringBuilder();
        if (importantMemories != null && !importantMemories.isEmpty()) {
            memBuilder.append("【核心记忆】\n");
            for (com.yoyo.jingxi.data.entity.Memory mem : importantMemories) {
                memBuilder.append("- ").append(mem.content).append("\n");
            }
        }
        if (normalMemories != null && !normalMemories.isEmpty()) {
            memBuilder.append("【近期记忆】\n");
            for (com.yoyo.jingxi.data.entity.Memory mem : normalMemories) {
                memBuilder.append("- ").append(mem.content).append("\n");
            }
        }

        // 城市名（从SharedPreferences读取，回退默认北京）
        String cityName = prefs.getString("WEATHER_CITY_NAME", "北京");

        // 6. 构建 Prompt（新天气格式）
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("你现在是").append(character.name).append("。");
        promptBuilder.append("你的设定是：").append(character.persona).append("。");
        promptBuilder.append("用户的名字是：").append(myPersonaName).append("，用户的设定是：").append(myPersonaDesc).append("。");

        if (memBuilder.length() > 0) {
            promptBuilder.append("\n以下是你关于用户的记忆：\n").append(memBuilder.toString());
        }

        if (!worldbookContent.isEmpty()) {
            promptBuilder.append("\n以下是一些世界观设定：\n").append(worldbookContent);
        }

        promptBuilder.append("\n今天的天气：【").append(cityName);
        if (!dailyWeather.isEmpty()) {
            promptBuilder.append("，今日天气").append(dailyWeather);
        }
        if (!maxTemp.isEmpty() && !minTemp.isEmpty()) {
            promptBuilder.append("，最高温").append(maxTemp).append("，最低温").append(minTemp);
        }
        promptBuilder.append("】。\n");

        if (!hourlyInfo.isEmpty()) {
            promptBuilder.append("今天逐小时天气：").append(hourlyInfo).append("\n");
        }

        promptBuilder.append("请根据你的性格设定、世界书以及记忆，结合今天的天气情况和历史聊天记录，用你的口吻给用户写一条简短的便签提醒（例如天冷加衣，下雨带伞等，符合你的角色性格）。要求：只需要输出提醒的内容，便签中无需刻意强调地点和温度，不要包含其他的格式或解释，尽量简短在50字以内。\n");

        // 7. 调用 API
        try {
            OpenAiRequest request = new OpenAiRequest();
            request.model = SpUtils.getString("API_MODEL", "gpt-4o-mini");
            request.messages = new java.util.ArrayList<>();
            request.messages.add(new OpenAiRequest.Message("system", promptBuilder.toString()));

            if (history != null && !history.isEmpty()) {
                for (Message msg : history) {
                    if (msg.content != null && !msg.content.isEmpty()) {
                        request.messages.add(new OpenAiRequest.Message(msg.isFromUser ? "user" : "assistant", msg.content));
                    }
                }
            }

            request.messages.add(new OpenAiRequest.Message("user", "给我一条今天的天气提醒便签吧。"));

            OpenAiApi api = new OpenAIManager().getApi();
            String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
            if (!endpoint.endsWith("/")) {
                endpoint += "/";
            }
            String apiUrl = endpoint + "v1/chat/completions";
            String apiKey = SpUtils.getString("OPENAI_API_KEY", "");

            Call<OpenAiResponse> call = api.createChatCompletion(apiUrl, "Bearer " + apiKey, request);
            Response<OpenAiResponse> response = call.execute();

            if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty()) {
                String reply = response.body().choices.get(0).message.content.trim();
                // 缓存便签
                SpUtils.putString(cacheKey, reply);
                Log.d(TAG, "Weather note generated for character " + character.name + ": " + reply);

                // 记忆2.0：极端天气时创建最近关注条目
                if (SpUtils.getBoolean("MEMORY_V2_ENABLED", true) && !dailyWeather.isEmpty()) {
                    String extremeWeather = null;
                    if (dailyWeather.contains("雨") && (dailyWeather.contains("大") || dailyWeather.contains("暴") || dailyWeather.contains("雷"))) {
                        extremeWeather = "今天有大雨";
                    } else if (dailyWeather.contains("雪") && (dailyWeather.contains("大") || dailyWeather.contains("暴"))) {
                        extremeWeather = "今天有大雪";
                    } else if (dailyWeather.contains("雷暴") || dailyWeather.contains("雷")) {
                        extremeWeather = "今天有雷暴";
                    } else if (dailyWeather.contains("雾") || dailyWeather.contains("霾")) {
                        extremeWeather = "今天有雾霾";
                    }
                    if (extremeWeather != null) {
                        // Extreme weather noted for next reminder cycle
                    }
                }

                // 重新调度下一天
                JingxiApplication.scheduleWeatherReminderAtTime();
                return Result.success();
            } else {
                Log.e(TAG, "API call failed with code: " + response.code());
                return Result.retry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating weather note", e);
            return Result.retry();
        }
    }

    private String buildHourlyInfo(String apiType, String hourlyJson) {
        StringBuilder hb = new StringBuilder();
        try {
            if ("qweather".equals(apiType)) {
                Type hType = new TypeToken<List<QWeatherApi.QWeatherHourlyResponse.Hourly>>() {}.getType();
                List<QWeatherApi.QWeatherHourlyResponse.Hourly> hList = gson.fromJson(hourlyJson, hType);
                if (hList != null) {
                    for (QWeatherApi.QWeatherHourlyResponse.Hourly h : hList) {
                        if (hb.length() > 0) hb.append("，");
                        String time = h.fxTime.length() >= 13 ? h.fxTime.substring(11, 13) + ":00" : h.fxTime;
                        hb.append(time).append(" ").append(h.temp).append("°C ").append(h.text);
                    }
                }
            } else if ("openmeteo".equals(apiType)) {
                OpenMeteoApi.OpenMeteoResponse.Hourly hData = gson.fromJson(hourlyJson, OpenMeteoApi.OpenMeteoResponse.Hourly.class);
                if (hData != null && hData.time != null && hData.temperature_2m != null) {
                    for (int i = 0; i < Math.min(hData.time.size(), 24); i++) {
                        if (hb.length() > 0) hb.append("，");
                        String time = hData.time.get(i).length() >= 16 ? hData.time.get(i).substring(11, 16) : hData.time.get(i);
                        String wDesc = (hData.weathercode != null && i < hData.weathercode.size()) ? getWeatherDesc(hData.weathercode.get(i)) : "";
                        hb.append(time).append(" ").append(Math.round(hData.temperature_2m.get(i))).append("°C ").append(wDesc);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error building hourly info", e);
        }
        return hb.toString();
    }

    private String getWeatherDesc(int code) {
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
}
