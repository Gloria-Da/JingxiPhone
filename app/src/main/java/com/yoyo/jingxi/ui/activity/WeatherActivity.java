package com.yoyo.jingxi.ui.activity;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yoyo.jingxi.R;
import com.bumptech.glide.Glide;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.network.OpenAiApi;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;
import com.yoyo.jingxi.network.OpenMeteoApi;
import com.yoyo.jingxi.ui.adapter.CharacterListAdapter;
import com.yoyo.jingxi.ui.adapter.WeatherDailyAdapter;
import com.yoyo.jingxi.ui.adapter.WeatherHourlyAdapter;
import com.yoyo.jingxi.utils.SpUtils;
import com.yoyo.jingxi.utils.ThemeManager;

import java.lang.reflect.Type;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class WeatherActivity extends AppCompatActivity {

    private TextView tvCity;
    private TextView tvTemp;
    private TextView tvDesc;
    
    private ImageView ivAvatar;
    private LinearLayout llWeatherReminder;
    private TextView tvReminderTitle;
    private TextView tvWeatherReminder;
    private TextView tvRegenerateReminder;

    private RecyclerView rvHourlyForecast;
    private RecyclerView rvDailyForecast;
    private WeatherHourlyAdapter hourlyAdapter;
    private WeatherDailyAdapter dailyAdapter;

    private static final long WEATHER_DETAIL_REFRESH_INTERVAL = 10 * 60 * 1000L;

    private static final String KEY_API_TYPE    = "WEATHER_DETAIL_API_TYPE";
    private static final String KEY_HOURLY_JSON = "WEATHER_DETAIL_HOURLY_JSON";
    private static final String KEY_DAILY_JSON  = "WEATHER_DETAIL_DAILY_JSON";

    private String cityName = "未知";
    private double lat = 39.9042;
    private double lon = 116.4074;

    private OpenMeteoApi openMeteoApi;
    private com.yoyo.jingxi.network.QWeatherApi qWeatherApi;
    private final Gson gson = new Gson();

    private Character selectedCharacter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);
        ThemeManager.applyDesktopThemeBackground(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("天气详情");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        String intentCity = getIntent().getStringExtra("cityName");
        if (intentCity != null) cityName = intentCity;
        lat = getIntent().getDoubleExtra("lat", lat);
        lon = getIntent().getDoubleExtra("lon", lon);

        tvCity = findViewById(R.id.tvCity);
        tvTemp = findViewById(R.id.tvTemp);
        tvDesc = findViewById(R.id.tvDesc);
        
        ivAvatar = findViewById(R.id.ivWeatherCharacterAvatar);
        llWeatherReminder = findViewById(R.id.llWeatherReminder);
        tvReminderTitle = findViewById(R.id.tvReminderTitle);
        tvWeatherReminder = findViewById(R.id.tvWeatherReminder);
        tvRegenerateReminder = findViewById(R.id.tvRegenerateReminder);
        
        tvRegenerateReminder.setOnClickListener(v -> fetchWeatherReminder());

        rvHourlyForecast = findViewById(R.id.rvHourlyForecast);
        rvDailyForecast = findViewById(R.id.rvDailyForecast);
        
        hourlyAdapter = new WeatherHourlyAdapter();
        rvHourlyForecast.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvHourlyForecast.setAdapter(hourlyAdapter);
        
        dailyAdapter = new WeatherDailyAdapter();
        rvDailyForecast.setLayoutManager(new LinearLayoutManager(this));
        rvDailyForecast.setAdapter(dailyAdapter);

        tvCity.setText(cityName);

        String intentTemp = getIntent().getStringExtra("temperature");
        String intentDesc = getIntent().getStringExtra("weatherDesc");
        if (intentTemp != null) tvTemp.setText(intentTemp);
        if (intentDesc != null) tvDesc.setText(intentDesc);

        initNetwork();

        ivAvatar.setOnClickListener(v -> showCharacterSelectionDialog());

        loadSelectedCharacter();

        SharedPreferences cachePrefs = getSharedPreferences("jingxi_prefs", MODE_PRIVATE);
        long lastFetch = cachePrefs.getLong("WEATHER_DETAIL_LAST_FETCH", 0);
        if (System.currentTimeMillis() - lastFetch > WEATHER_DETAIL_REFRESH_INTERVAL) {
            cachePrefs.edit().putLong("WEATHER_DETAIL_LAST_FETCH", System.currentTimeMillis()).apply();
            fetchWeatherData();
        } else {
            loadCachedForecastData(cachePrefs);
        }
    }

    private void initNetwork() {
        Retrofit retrofitOpenMeteo = new Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        openMeteoApi = retrofitOpenMeteo.create(OpenMeteoApi.class);

        SharedPreferences qwPrefs = getSharedPreferences("jingxi_prefs", MODE_PRIVATE);
        String customHost = qwPrefs.getString("QWEATHER_API_HOST", "").trim();
        String qBaseUrl = customHost.isEmpty() ? "https://devapi.qweather.com/" : "https://" + customHost + "/";
        Retrofit retrofitQWeather = new Retrofit.Builder()
                .baseUrl(qBaseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        qWeatherApi = retrofitQWeather.create(com.yoyo.jingxi.network.QWeatherApi.class);
    }

    private void loadCachedForecastData(SharedPreferences prefs) {
        String apiType = prefs.getString(KEY_API_TYPE, "");
        String hourlyJson = prefs.getString(KEY_HOURLY_JSON, "");
        String dailyJson = prefs.getString(KEY_DAILY_JSON, "");

        if ("qweather".equals(apiType)) {
            if (!hourlyJson.isEmpty()) {
                Type type = new TypeToken<List<com.yoyo.jingxi.network.QWeatherApi.QWeatherHourlyResponse.Hourly>>() {}.getType();
                List<com.yoyo.jingxi.network.QWeatherApi.QWeatherHourlyResponse.Hourly> hourlyList = gson.fromJson(hourlyJson, type);
                if (hourlyList != null) hourlyAdapter.setQWeatherData(hourlyList);
            }
            if (!dailyJson.isEmpty()) {
                Type type = new TypeToken<List<com.yoyo.jingxi.network.QWeatherApi.QWeatherDailyResponse.Daily>>() {}.getType();
                List<com.yoyo.jingxi.network.QWeatherApi.QWeatherDailyResponse.Daily> dailyList = gson.fromJson(dailyJson, type);
                if (dailyList != null) dailyAdapter.setQWeatherData(dailyList);
            }
        } else if ("openmeteo".equals(apiType)) {
            if (!hourlyJson.isEmpty()) {
                OpenMeteoApi.OpenMeteoResponse.Hourly hourly = gson.fromJson(hourlyJson, OpenMeteoApi.OpenMeteoResponse.Hourly.class);
                if (hourly != null) {
                    hourlyAdapter.setData(hourly.time, hourly.temperature_2m, hourly.weathercode, false, null, "");
                }
            }
            if (!dailyJson.isEmpty()) {
                OpenMeteoApi.OpenMeteoResponse.Daily daily = gson.fromJson(dailyJson, OpenMeteoApi.OpenMeteoResponse.Daily.class);
                if (daily != null) {
                    dailyAdapter.setData(daily.time, daily.temperature_2m_max, daily.temperature_2m_min, daily.weathercode, false, null);
                }
            }
        }
    }

    private void fetchWeatherData() {
        SharedPreferences prefs = getSharedPreferences("jingxi_prefs", MODE_PRIVATE);
        String qWeatherKey = prefs.getString("QWEATHER_API_KEY", "");

        if (!qWeatherKey.isEmpty()) {
            fetchQWeather(qWeatherKey.trim());
        } else {
            fetchOpenMeteoWeather();
        }
    }

    private void fetchQWeather(String qWeatherKey) {
        String location = String.format(java.util.Locale.US, "%.2f,%.2f", lon, lat);
        
        // 1. 获取实时天气
        qWeatherApi.getNow(location, qWeatherKey).enqueue(new Callback<com.yoyo.jingxi.network.QWeatherApi.QWeatherNowResponse>() {
            @Override
            public void onResponse(Call<com.yoyo.jingxi.network.QWeatherApi.QWeatherNowResponse> call, Response<com.yoyo.jingxi.network.QWeatherApi.QWeatherNowResponse> response) {
                if (response.isSuccessful() && response.body() != null && "200".equals(response.body().code)) {
                    runOnUiThread(() -> {
                        if (response.body().now != null) {
                            tvTemp.setText(response.body().now.temp + "°C");
                            tvDesc.setText(response.body().now.text);
                        }
                    });
                } else {
                    String code = response.body() != null ? response.body().code : String.valueOf(response.code());
                    String errBody = "";
                    if (response.body() == null && response.errorBody() != null) {
                        try { errBody = " body=" + response.errorBody().string(); } catch (Exception ignored) {}
                    }
                    android.util.Log.e("WeatherActivity", "QWeather getNow failed, code: " + code + errBody);
                    runOnUiThread(() -> Toast.makeText(WeatherActivity.this, "和风天气实时获取失败:" + code, Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(Call<com.yoyo.jingxi.network.QWeatherApi.QWeatherNowResponse> call, Throwable t) {
                android.util.Log.e("WeatherActivity", "QWeather getNow error", t);
                runOnUiThread(() -> Toast.makeText(WeatherActivity.this, "和风实时请求网络异常", Toast.LENGTH_SHORT).show());
            }
        });

        // 2. 获取逐小时预报
        qWeatherApi.getHourly(location, qWeatherKey).enqueue(new Callback<com.yoyo.jingxi.network.QWeatherApi.QWeatherHourlyResponse>() {
            @Override
            public void onResponse(Call<com.yoyo.jingxi.network.QWeatherApi.QWeatherHourlyResponse> call, Response<com.yoyo.jingxi.network.QWeatherApi.QWeatherHourlyResponse> response) {
                if (response.isSuccessful() && response.body() != null && "200".equals(response.body().code)) {
                    runOnUiThread(() -> {
                        hourlyAdapter.setData(null, null, null, true, null, null); // 清除旧数据，适配和风
                        hourlyAdapter.setQWeatherData(response.body().hourly);
                    });
                    getSharedPreferences("jingxi_prefs", MODE_PRIVATE).edit()
                            .putString(KEY_API_TYPE, "qweather")
                            .putString(KEY_HOURLY_JSON, gson.toJson(response.body().hourly))
                            .apply();
                } else {
                    String code = response.body() != null ? response.body().code : String.valueOf(response.code());
                    String errBody = "";
                    if (response.body() == null && response.errorBody() != null) {
                        try { errBody = " body=" + response.errorBody().string(); } catch (Exception ignored) {}
                    }
                    android.util.Log.e("WeatherActivity", "QWeather getHourly failed, code: " + code + errBody);
                }
            }

            @Override
            public void onFailure(Call<com.yoyo.jingxi.network.QWeatherApi.QWeatherHourlyResponse> call, Throwable t) {
                android.util.Log.e("WeatherActivity", "QWeather getHourly error", t);
            }
        });

        // 3. 获取多天预报
        qWeatherApi.getDaily(location, qWeatherKey).enqueue(new Callback<com.yoyo.jingxi.network.QWeatherApi.QWeatherDailyResponse>() {
            @Override
            public void onResponse(Call<com.yoyo.jingxi.network.QWeatherApi.QWeatherDailyResponse> call, Response<com.yoyo.jingxi.network.QWeatherApi.QWeatherDailyResponse> response) {
                if (response.isSuccessful() && response.body() != null && "200".equals(response.body().code)) {
                    runOnUiThread(() -> {
                        dailyAdapter.setData(null, null, null, null, true, null); // 清除旧数据，适配和风
                        dailyAdapter.setQWeatherData(response.body().daily);
                    });
                    getSharedPreferences("jingxi_prefs", MODE_PRIVATE).edit()
                            .putString(KEY_DAILY_JSON, gson.toJson(response.body().daily))
                            .apply();
                } else {
                    String code = response.body() != null ? response.body().code : String.valueOf(response.code());
                    String errBody = "";
                    if (response.body() == null && response.errorBody() != null) {
                        try { errBody = " body=" + response.errorBody().string(); } catch (Exception ignored) {}
                    }
                    android.util.Log.e("WeatherActivity", "QWeather getDaily failed, code: " + code + errBody);
                }
            }

            @Override
            public void onFailure(Call<com.yoyo.jingxi.network.QWeatherApi.QWeatherDailyResponse> call, Throwable t) {
                android.util.Log.e("WeatherActivity", "QWeather getDaily error", t);
            }
        });
    }

    private void fetchOpenMeteoWeather() {
        openMeteoApi.getFullWeather(lat, lon, true, "temperature_2m,weathercode", "weathercode,temperature_2m_max,temperature_2m_min", "auto").enqueue(new Callback<OpenMeteoApi.OpenMeteoResponse>() {
            @Override
            public void onResponse(Call<OpenMeteoApi.OpenMeteoResponse> call, Response<OpenMeteoApi.OpenMeteoResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    OpenMeteoApi.OpenMeteoResponse weather = response.body();
                    
                    runOnUiThread(() -> {
                        if (weather.current_weather != null) {
                            tvTemp.setText(Math.round(weather.current_weather.temperature) + "°C");
                            tvDesc.setText(getWeatherDesc(weather.current_weather.weathercode));
                        }
                            
                        // 更新适配器数据
                        if (weather.hourly != null) {
                            hourlyAdapter.setData(
                                weather.hourly.time,
                                weather.hourly.temperature_2m,
                                weather.hourly.weathercode,
                                false,
                                null,
                                ""
                            );
                        }

                        if (weather.daily != null) {
                            dailyAdapter.setData(
                                weather.daily.time,
                                weather.daily.temperature_2m_max,
                                weather.daily.temperature_2m_min,
                                weather.daily.weathercode,
                                false,
                                null
                            );
                        }

                        SharedPreferences cachePrefs = getSharedPreferences("jingxi_prefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = cachePrefs.edit().putString(KEY_API_TYPE, "openmeteo");
                        if (weather.hourly != null) editor.putString(KEY_HOURLY_JSON, gson.toJson(weather.hourly));
                        if (weather.daily != null) editor.putString(KEY_DAILY_JSON, gson.toJson(weather.daily));
                        editor.apply();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(WeatherActivity.this, "获取天气失败", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(Call<OpenMeteoApi.OpenMeteoResponse> call, Throwable t) {
                runOnUiThread(() -> Toast.makeText(WeatherActivity.this, "网络错误", Toast.LENGTH_SHORT).show());
            }
        });
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

    private void showCharacterSelectionDialog() {
        new Thread(() -> {
            List<Character> characters = AppDatabase.getDatabase(this).characterDao().getAllCharactersSync();
            runOnUiThread(() -> {
                if (characters == null || characters.isEmpty()) {
                    Toast.makeText(this, "暂无角色", Toast.LENGTH_SHORT).show();
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("选择提供天气提醒的角色");
                
                RecyclerView rvCharacters = new RecyclerView(this);
                rvCharacters.setLayoutManager(new LinearLayoutManager(this));
                
                AlertDialog dialog = builder.setView(rvCharacters).create();
                
                CharacterListAdapter adapter = new CharacterListAdapter();
                adapter.setCharacters(characters);
                adapter.setOnItemClickListener(new CharacterListAdapter.OnItemClickListener() {
                    @Override
                    public void onItemClick(Character character) {
                        selectedCharacter = character;
                        saveSelectedCharacter(character.id);
                        updateCharacterAvatar();
                        dialog.dismiss();
                        fetchWeatherReminder();
                    }

                    @Override
                    public void onDeleteClick(Character character) {
                        // ignore here
                    }
                });
                rvCharacters.setAdapter(adapter);
                
                dialog.show();
            });
        }).start();
    }

    private void saveSelectedCharacter(int characterId) {
        SpUtils.putInt("WEATHER_REMINDER_CHARACTER_ID", characterId);
    }

    private void loadSelectedCharacter() {
        int characterId = SpUtils.getInt("WEATHER_REMINDER_CHARACTER_ID", -1);
        if (characterId != -1) {
            new Thread(() -> {
                selectedCharacter = AppDatabase.getDatabase(this).characterDao().getCharacterById(characterId);
                runOnUiThread(() -> {
                    updateCharacterAvatar();
                    loadCachedReminder();
                });
            }).start();
        }
    }

    private void updateCharacterAvatar() {
        if (selectedCharacter != null) {
            if (selectedCharacter.avatarPath != null && !selectedCharacter.avatarPath.isEmpty()) {
                Glide.with(this)
                        .load(selectedCharacter.avatarPath)
                        .circleCrop()
                        .placeholder(R.drawable.ic_launcher_round)
                        .error(R.drawable.ic_launcher_round)
                        .into(ivAvatar);
            } else {
                Glide.with(this)
                        .load(R.drawable.ic_launcher_round)
                        .circleCrop()
                        .into(ivAvatar);
            }
        }
    }

    private void loadCachedReminder() {
        if (selectedCharacter == null) return;
        String dateKey = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        String cacheKey = "WEATHER_REMINDER_" + selectedCharacter.id + "_" + dateKey;
        String cachedReminder = SpUtils.getString(cacheKey, "");
        if (!cachedReminder.isEmpty()) {
            showReminder(cachedReminder);
        }
    }

    private void saveReminderCache(String reminder) {
        if (selectedCharacter == null) return;
        String dateKey = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        String cacheKey = "WEATHER_REMINDER_" + selectedCharacter.id + "_" + dateKey;
        SpUtils.putString(cacheKey, reminder);
    }

    private void showReminder(String reminder) {
        llWeatherReminder.setVisibility(View.VISIBLE);
        if (selectedCharacter != null) {
            tvReminderTitle.setText(selectedCharacter.name + "的便签");
        }
        tvWeatherReminder.setText(reminder);
        tvRegenerateReminder.setVisibility(View.VISIBLE);
    }

    private void fetchWeatherReminder() {
        if (selectedCharacter == null) return;
        String weatherStr = tvDesc.getText().toString();
        String tempStr = tvTemp.getText().toString();
        if (weatherStr.isEmpty() || tempStr.isEmpty()) return;

        SharedPreferences cachePrefs = getSharedPreferences("jingxi_prefs", MODE_PRIVATE);
        String apiType = cachePrefs.getString(KEY_API_TYPE, "");
        String dailyJson = cachePrefs.getString(KEY_DAILY_JSON, "");
        
        String maxTemp = "";
        String minTemp = "";
        
        try {
            if ("qweather".equals(apiType) && !dailyJson.isEmpty()) {
                Type type = new TypeToken<List<com.yoyo.jingxi.network.QWeatherApi.QWeatherDailyResponse.Daily>>() {}.getType();
                List<com.yoyo.jingxi.network.QWeatherApi.QWeatherDailyResponse.Daily> dailyList = gson.fromJson(dailyJson, type);
                if (dailyList != null && !dailyList.isEmpty()) {
                    maxTemp = dailyList.get(0).tempMax + "°C";
                    minTemp = dailyList.get(0).tempMin + "°C";
                }
            } else if ("openmeteo".equals(apiType) && !dailyJson.isEmpty()) {
                OpenMeteoApi.OpenMeteoResponse.Daily daily = gson.fromJson(dailyJson, OpenMeteoApi.OpenMeteoResponse.Daily.class);
                if (daily != null && daily.temperature_2m_max != null && !daily.temperature_2m_max.isEmpty()) {
                    maxTemp = Math.round(daily.temperature_2m_max.get(0)) + "°C";
                    minTemp = Math.round(daily.temperature_2m_min.get(0)) + "°C";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        String finalMaxTemp = maxTemp;
        String finalMinTemp = minTemp;

        llWeatherReminder.setVisibility(View.VISIBLE);
        if (selectedCharacter != null) {
            tvReminderTitle.setText(selectedCharacter.name + "的便签");
        }
        tvWeatherReminder.setText("正在生成提醒...");
        tvRegenerateReminder.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                // Get context
                // Find most recent session with this character
                List<ChatSession> sessions = AppDatabase.getDatabase(this).chatSessionDao().getAllSessionsSync();
                ChatSession targetSession = null;
                if (sessions != null) {
                    for (ChatSession s : sessions) {
                        if (s.characterId == selectedCharacter.id) {
                            if (targetSession == null || s.lastMessageTimestamp > targetSession.lastMessageTimestamp) {
                                targetSession = s;
                            }
                        }
                    }
                }
                
                int historyCount = SpUtils.getInt("SETTING_HISTORY_ROUNDS", 80);
                List<Message> history = null;
                if (targetSession != null) {
                    history = AppDatabase.getDatabase(this).messageDao().getRecentMessagesBySessionIdSync(targetSession.id, historyCount);
                    java.util.Collections.reverse(history);
                }
                
                if (targetSession != null) {
                    targetSession.lastMessageTimestamp = System.currentTimeMillis();
                    targetSession.unreadCount += 1;
                    AppDatabase.getDatabase(this).chatSessionDao().update(targetSession);
                }

                String worldbookContent = "";
                List<com.yoyo.jingxi.data.entity.WorldbookEntry> entries = AppDatabase.getDatabase(this).worldbookDao().getAllEnabledEntriesSync();
                StringBuilder wbBuilder = new StringBuilder();
                for (com.yoyo.jingxi.data.entity.WorldbookEntry entry : entries) {
                    wbBuilder.append(entry.keyword).append(": ").append(entry.content).append("\n");
                }
                worldbookContent = wbBuilder.toString();

                int memoryCallCount = SpUtils.getInt("SETTING_MEMORY_CALL_COUNT", 20);
                List<com.yoyo.jingxi.data.entity.Memory> importantMemories = AppDatabase.getDatabase(this).memoryDao().getImportantMemoriesSync(selectedCharacter.id);
                List<com.yoyo.jingxi.data.entity.Memory> normalMemories = memoryCallCount > 0 ? 
                    AppDatabase.getDatabase(this).memoryDao().getNormalMemoriesSync(selectedCharacter.id, memoryCallCount) : 
                    AppDatabase.getDatabase(this).memoryDao().getAllNormalMemoriesSync(selectedCharacter.id);

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

                // Build prompt
                StringBuilder promptBuilder = new StringBuilder();
                promptBuilder.append("你现在是").append(selectedCharacter.name).append("。");
                promptBuilder.append("你的设定是：").append(selectedCharacter.persona).append("。");
                
                String myPersonaName = targetSession != null ? targetSession.myPersonaName : SpUtils.getString("MY_NAME", "我");
                com.yoyo.jingxi.data.entity.MyPersona myPersona = AppDatabase.getDatabase(this).myPersonaDao().getMyPersonaByName(myPersonaName);
                String userPersonaDesc = myPersona != null ? myPersona.persona : SpUtils.getString("MY_PERSONA", "普通人");

                promptBuilder.append("用户的名字是：").append(myPersonaName).append("，用户的设定是：").append(userPersonaDesc).append("。");

                if (memBuilder.length() > 0) {
                    promptBuilder.append("\n以下是你关于用户的记忆：\n").append(memBuilder.toString());
                }

                if (!worldbookContent.isEmpty()) {
                    promptBuilder.append("\n以下是一些世界观设定：\n").append(worldbookContent);
                }

                promptBuilder.append("\n现在的天气情况是：【").append(cityName).append("，当前天气").append(weatherStr).append("，当前气温").append(tempStr);
                if (!finalMaxTemp.isEmpty() && !finalMinTemp.isEmpty()) {
                    promptBuilder.append("，今日最高气温").append(finalMaxTemp).append("，今日最低气温").append(finalMinTemp);
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
                
                // Add a final user message to trigger the response
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
                    runOnUiThread(() -> {
                        showReminder(reply);
                        saveReminderCache(reply);
                    });
                } else {
                    runOnUiThread(() -> {
                        tvWeatherReminder.setText("生成提醒失败。");
                        tvRegenerateReminder.setVisibility(View.VISIBLE);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    tvWeatherReminder.setText("生成提醒时出错。");
                    tvRegenerateReminder.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }
}
