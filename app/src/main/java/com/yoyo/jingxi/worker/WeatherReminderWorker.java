package com.yoyo.jingxi.worker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.Message;
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
        
        // 1. 检查是否设置了提醒角色
        int characterId = SpUtils.getInt("WEATHER_REMINDER_CHARACTER_ID", -1);
        if (characterId == -1) {
            Log.d(TAG, "No character selected for weather reminder, skipping.");
            return Result.success();
        }

        // 2. 检查今天是否已经生成过缓存
        String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String cacheKey = "WEATHER_REMINDER_" + characterId + "_" + dateKey;
        String cachedReminder = SpUtils.getString(cacheKey, "");
        if (!cachedReminder.isEmpty()) {
            Log.d(TAG, "Weather reminder already generated for today, skipping.");
            return Result.success();
        }

        // 3. 获取天气数据
        String weatherStr = "";
        String tempStr = "";
        String maxTemp = "";
        String minTemp = "";
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("jingxi_prefs", Context.MODE_PRIVATE);
        String apiType = prefs.getString("WEATHER_DETAIL_API_TYPE", "");
        
        if ("qweather".equals(apiType)) {
            String qWeatherKey = prefs.getString("QWEATHER_API_KEY", "");
            if (!qWeatherKey.isEmpty()) {
                weatherStr = getQWeatherDesc(qWeatherKey);
                
                String dailyJson = prefs.getString("WEATHER_DETAIL_DAILY_JSON", "");
                if (!dailyJson.isEmpty()) {
                    try {
                        Type type = new TypeToken<List<QWeatherApi.QWeatherDailyResponse.Daily>>() {}.getType();
                        List<QWeatherApi.QWeatherDailyResponse.Daily> dailyList = gson.fromJson(dailyJson, type);
                        if (dailyList != null && !dailyList.isEmpty()) {
                            maxTemp = dailyList.get(0).tempMax + "°C";
                            minTemp = dailyList.get(0).tempMin + "°C";
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing qweather daily json", e);
                    }
                }
            }
        } else if ("openmeteo".equals(apiType)) {
            weatherStr = getOpenMeteoDesc();
            
            String dailyJson = prefs.getString("WEATHER_DETAIL_DAILY_JSON", "");
            if (!dailyJson.isEmpty()) {
                try {
                    OpenMeteoApi.OpenMeteoResponse.Daily daily = gson.fromJson(dailyJson, OpenMeteoApi.OpenMeteoResponse.Daily.class);
                    if (daily != null && daily.temperature_2m_max != null && !daily.temperature_2m_max.isEmpty()) {
                        maxTemp = Math.round(daily.temperature_2m_max.get(0)) + "°C";
                        minTemp = Math.round(daily.temperature_2m_min.get(0)) + "°C";
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing openmeteo daily json", e);
                }
            }
        }

        if (weatherStr.isEmpty()) {
            Log.w(TAG, "Failed to get weather data for background generation.");
            return Result.retry();
        }
        
        // 我们从 weatherStr 中提取天气和温度（如果在刚才的函数中拼好了）
        // 为了简单起见，我们在上面两个 getDesc 方法中直接返回 "晴，气温25°C" 这种格式。

        // 4. 获取角色信息和上下文
        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        Character character = db.characterDao().getCharacterById(characterId);
        if (character == null) {
            Log.e(TAG, "Character not found.");
            return Result.failure();
        }

        // 查找最近的会话
        List<ChatSession> sessions = db.chatSessionDao().getAllSessionsSync();
        ChatSession targetSession = null;
        if (sessions != null) {
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

        String worldbookContent = "";
        List<com.yoyo.jingxi.data.entity.WorldbookEntry> entries = db.worldbookDao().getAllEnabledEntriesSync();
        StringBuilder wbBuilder = new StringBuilder();
        for (com.yoyo.jingxi.data.entity.WorldbookEntry entry : entries) {
            wbBuilder.append(entry.keyword).append(": ").append(entry.content).append("\n");
        }
        worldbookContent = wbBuilder.toString();

        int memoryCallCount = SpUtils.getInt("SETTING_MEMORY_CALL_COUNT", 20);
        List<com.yoyo.jingxi.data.entity.Memory> importantMemories = db.memoryDao().getImportantMemoriesSync(characterId);
        List<com.yoyo.jingxi.data.entity.Memory> normalMemories = memoryCallCount > 0 ? 
            db.memoryDao().getNormalMemoriesSync(characterId, memoryCallCount) : 
            db.memoryDao().getAllNormalMemoriesSync(characterId);

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

        // 5. 构建 Prompt
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("你现在是").append(character.name).append("。");
        promptBuilder.append("你的设定是：").append(character.persona).append("。");
        
        String myPersonaName = targetSession != null ? targetSession.myPersonaName : SpUtils.getString("MY_NAME", "我");
        com.yoyo.jingxi.data.entity.MyPersona myPersona = db.myPersonaDao().getMyPersonaByName(myPersonaName);
        String userPersonaDesc = myPersona != null ? myPersona.persona : SpUtils.getString("MY_PERSONA", "普通人");

        promptBuilder.append("用户的名字是：").append(myPersonaName).append("，用户的设定是：").append(userPersonaDesc).append("。");

        if (memBuilder.length() > 0) {
            promptBuilder.append("\n以下是你关于用户的记忆：\n").append(memBuilder.toString());
        }

        if (!worldbookContent.isEmpty()) {
            promptBuilder.append("\n以下是一些世界观设定：\n").append(worldbookContent);
        }

        promptBuilder.append("\n现在的天气情况是：【").append(weatherStr);
        if (!maxTemp.isEmpty() && !minTemp.isEmpty()) {
            promptBuilder.append("，今日最高气温").append(maxTemp).append("，今日最低气温").append(minTemp);
        }
        promptBuilder.append("】。\n");
        promptBuilder.append("请根据你的性格设定、世界书以及记忆，结合现在的天气情况和历史聊天记录，用你的口吻给用户写一条简短的便签提醒（例如天冷加衣，下雨带伞等，符合你的角色性格）。要求：只需要输出提醒的内容，便签中无需刻意强调地点和温度，不要包含其他的格式或解释，尽量简短在50字以内。\n");

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

        // 6. 调用大模型 API
        try {
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
                SpUtils.putString(cacheKey, reply);
                Log.d(TAG, "Weather reminder generated successfully: " + reply);
                return Result.success();
            } else {
                Log.e(TAG, "API call failed with code: " + response.code());
                return Result.retry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating reminder", e);
            return Result.retry();
        }
    }

    private String getQWeatherDesc(String qWeatherKey) {
        try {
            SharedPreferences qwPrefs = getApplicationContext().getSharedPreferences("jingxi_prefs", Context.MODE_PRIVATE);
            String customHost = qwPrefs.getString("QWEATHER_API_HOST", "").trim();
            String qBaseUrl = customHost.isEmpty() ? "https://devapi.qweather.com/" : "https://" + customHost + "/";
            Retrofit retrofitQWeather = new Retrofit.Builder()
                    .baseUrl(qBaseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            QWeatherApi api = retrofitQWeather.create(QWeatherApi.class);
            
            // 为了简单起见，使用默认的北京坐标。如果在前台定位了，这里应该从 SpUtils 读取。
            double lat = 39.9042;
            double lon = 116.4074;
            String location = String.format(Locale.US, "%.2f,%.2f", lon, lat);
            
            Response<QWeatherApi.QWeatherNowResponse> response = api.getNow(location, qWeatherKey).execute();
            if (response.isSuccessful() && response.body() != null && "200".equals(response.body().code) && response.body().now != null) {
                return "北京" + "，天气" + response.body().now.text + "，气温" + response.body().now.temp + "°C";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching QWeather", e);
        }
        return "";
    }

    private String getOpenMeteoDesc() {
        try {
            Retrofit retrofitOpenMeteo = new Retrofit.Builder()
                    .baseUrl("https://api.open-meteo.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            OpenMeteoApi api = retrofitOpenMeteo.create(OpenMeteoApi.class);
            
            double lat = 39.9042;
            double lon = 116.4074;
            
            Response<OpenMeteoApi.OpenMeteoResponse> response = api.getFullWeather(lat, lon, true, "temperature_2m,weathercode", "weathercode,temperature_2m_max,temperature_2m_min", "auto").execute();
            if (response.isSuccessful() && response.body() != null && response.body().current_weather != null) {
                String desc = getWeatherDesc(response.body().current_weather.weathercode);
                long temp = Math.round(response.body().current_weather.temperature);
                return "北京" + "，天气" + desc + "，气温" + temp + "°C";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching OpenMeteo", e);
        }
        return "";
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
