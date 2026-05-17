package com.yoyo.jingxi.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import java.util.List;

public interface AmapWeatherApi {
    @GET("v3/weather/weatherInfo")
    Call<AmapWeatherResponse> getWeather(
            @Query("city") String cityCode,
            @Query("key") String key,
            @Query("extensions") String extensions // "base" for current, "all" for forecast
    );

    @GET("v3/geocode/regeo")
    Call<AmapGeocodeResponse> getGeocode(
            @Query("location") String location, // "lon,lat"
            @Query("key") String key
    );

    class AmapWeatherResponse {
        public String status;
        public String info;
        public List<Lives> lives;
        
        public static class Lives {
            public String weather;
            public String temperature;
            public String city;
        }
    }

    class AmapGeocodeResponse {
        public String status;
        public String info;
        public Regeocode regeocode;
        
        public static class Regeocode {
            public AddressComponent addressComponent;
            
            public static class AddressComponent {
                public String adcode; // this is the city code for weather
                public String city;
                public String district;
            }
        }
    }
}
