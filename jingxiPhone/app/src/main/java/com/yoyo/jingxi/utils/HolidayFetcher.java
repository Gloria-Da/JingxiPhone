package com.yoyo.jingxi.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.HolidayCache;
import com.yoyo.jingxi.network.HolidayApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 节假日数据拉取与缓存。
 * 数据来源: NateScarlet/holiday-cn (GitHub开源，无需API Key)
 */
public class HolidayFetcher {

    private static final String BASE_URL = "https://raw.githubusercontent.com/";
    private static final long CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000; // 30天过期
    private static final String TAG = "HolidayFetcher";

    private static HolidayApi api;
    private static volatile boolean isFetching = false;

    private static HolidayApi getApi() {
        if (api == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            api = retrofit.create(HolidayApi.class);
        }
        return api;
    }

    /**
     * 检查缓存并在需要时拉取指定年份的节假日数据。
     * 可以在任何线程调用——内部自动使用后台线程访问数据库。
     *
     * @param year      要拉取的年份
     * @param db        数据库实例
     * @param onUpdated 数据更新后主线程回调（可为 null）
     */
    public static void fetchIfNeeded(int year, AppDatabase db, Runnable onUpdated) {
        if (isFetching) return;

        // 所有 DB 操作必须在后台线程执行
        Executors.newSingleThreadExecutor().execute(() -> {
            // Check cache
            String startDate = year + "-01-01";
            String endDate = year + "-12-31";
            int count = db.holidayCacheDao().countInRange(startDate, endDate);

            if (count > 5) {
                // Check if cache is stale
                List<HolidayCache> existing = db.holidayCacheDao().getHolidaysInRange(startDate, endDate);
                if (!existing.isEmpty()) {
                    long newestFetch = 0;
                    for (HolidayCache h : existing) {
                        if (h.fetchedAt > newestFetch) newestFetch = h.fetchedAt;
                    }
                    if (System.currentTimeMillis() - newestFetch < CACHE_TTL_MS) {
                        // Cache is fresh — notify caller that data is available
                        if (onUpdated != null) {
                            new Handler(Looper.getMainLooper()).post(onUpdated);
                        }
                        return;
                    }
                }
            }

            // Fetch from network
            isFetching = true;
            Log.i(TAG, "Fetching holidays for year " + year);

            getApi().getHolidays(year).enqueue(new Callback<HolidayApi.HolidayResponse>() {
                @Override
                public void onResponse(Call<HolidayApi.HolidayResponse> call,
                                       Response<HolidayApi.HolidayResponse> response) {
                    isFetching = false;
                    if (!response.isSuccessful() || response.body() == null) {
                        Log.w(TAG, "Holiday API returned unsuccessful: " + response.code());
                        return;
                    }

                    List<HolidayApi.HolidayDayItem> days = response.body().days;
                    if (days == null || days.isEmpty()) {
                        Log.w(TAG, "Holiday API returned empty days");
                        return;
                    }

                    List<HolidayCache> cacheList = new ArrayList<>();
                    long now = System.currentTimeMillis();

                    for (HolidayApi.HolidayDayItem day : days) {
                        if (day.date == null) continue;
                        HolidayCache cache = new HolidayCache();
                        cache.date = day.date;
                        cache.name = day.name;
                        cache.isOffDay = day.isOffDay;
                        cache.fetchedAt = now;
                        cacheList.add(cache);
                    }

                    // DB 写入也必须在后台线程
                    Executors.newSingleThreadExecutor().execute(() -> {
                        db.holidayCacheDao().insertAll(cacheList);
                        Log.i(TAG, "Cached " + cacheList.size() + " holidays for year " + year);

                        // Clean old data (older than 2 years ago)
                        String beforeDate = (year - 2) + "-01-01";
                        db.holidayCacheDao().deleteOlderThan(beforeDate);

                        // 通知调用方数据已更新
                        if (onUpdated != null) {
                            new Handler(Looper.getMainLooper()).post(onUpdated);
                        }
                    });
                }

                @Override
                public void onFailure(Call<HolidayApi.HolidayResponse> call, Throwable t) {
                    isFetching = false;
                    Log.w(TAG, "Holiday fetch failed (network may be unavailable): " + t.getMessage());
                    // Silent degradation — calendar works fine without holiday data
                }
            });
        });
    }
}
