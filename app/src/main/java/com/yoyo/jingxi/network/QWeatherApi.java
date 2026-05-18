package com.yoyo.jingxi.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import java.util.List;

public interface QWeatherApi {
    @GET("v7/weather/now")
    Call<QWeatherNowResponse> getNow(
            @Query("location") String location, // "lon,lat" or LocationID
            @Query("key") String key
    );

    @GET("v7/weather/24h")
    Call<QWeatherHourlyResponse> getHourly(
            @Query("location") String location,
            @Query("key") String key
    );

    @GET("v7/weather/7d")
    Call<QWeatherDailyResponse> getDaily(
            @Query("location") String location,
            @Query("key") String key
    );

    class QWeatherNowResponse {
        public String code;
        public Now now;
        
        public static class Now {
            public String temp;
            public String text;
            public String icon;
        }
    }

    class QWeatherHourlyResponse {
        public String code;
        public List<Hourly> hourly;
        
        public static class Hourly {
            public String fxTime;
            public String temp;
            public String text;
            public String icon;
        }
    }

    class QWeatherDailyResponse {
        public String code;
        public List<Daily> daily;
        
        public static class Daily {
            public String fxDate;
            public String tempMax;
            public String tempMin;
            public String textDay;
            public String iconDay;
        }
    }
}