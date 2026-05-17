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

    class OpenMeteoResponse {
        public CurrentWeather current_weather;
        
        public static class CurrentWeather {
            public double temperature;
            public int weathercode;
            public int is_day;
        }
    }
}
