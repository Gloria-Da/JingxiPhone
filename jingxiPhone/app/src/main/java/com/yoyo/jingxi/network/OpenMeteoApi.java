package com.yoyo.jingxi.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenMeteoApi {
    @GET("v1/forecast")
    Call<OpenMeteoResponse> getWeather(
            @Query("latitude") double lat,
            @Query("longitude") double lon,
            @Query("current_weather") boolean currentWeather,
            @Query("timezone") String timezone
    );

    @GET("v1/forecast")
    Call<OpenMeteoResponse> getFullWeather(
            @Query("latitude") double lat,
            @Query("longitude") double lon,
            @Query("current_weather") boolean currentWeather,
            @Query("hourly") String hourly,
            @Query("daily") String daily,
            @Query("timezone") String timezone
    );

    class OpenMeteoResponse {
        public CurrentWeather current_weather;
        public Hourly hourly;
        public Daily daily;
        
        public static class CurrentWeather {
            public double temperature;
            public int weathercode;
            public int is_day;
        }

        public static class Hourly {
            public java.util.List<String> time;
            public java.util.List<Double> temperature_2m;
            public java.util.List<Integer> weathercode;
        }

        public static class Daily {
            public java.util.List<String> time;
            public java.util.List<Integer> weathercode;
            public java.util.List<Double> temperature_2m_max;
            public java.util.List<Double> temperature_2m_min;
        }
    }
}
