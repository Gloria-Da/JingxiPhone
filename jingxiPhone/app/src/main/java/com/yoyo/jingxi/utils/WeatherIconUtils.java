package com.yoyo.jingxi.utils;

import com.yoyo.jingxi.R;

public class WeatherIconUtils {

    /**
     * 获取和风天气 (QWeather) 对应的图标
     * @param code 和风天气的图标代码 (例如 "100" 为晴)
     * @return 对应的 R.drawable 资源 ID
     */
    public static int getQWeatherIcon(String code) {
        if (code == null) return R.drawable.ic_weather_cloudy;

        int iconCode;
        try {
            iconCode = Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return R.drawable.ic_weather_cloudy;
        }

        // 映射规则参考：https://dev.qweather.com/docs/resource/icons/
        if (iconCode == 100 || iconCode == 150) {
            return R.drawable.ic_weather_sunny; // 晴
        } else if (iconCode >= 101 && iconCode <= 104) {
            return R.drawable.ic_weather_cloudy; // 多云、阴
        } else if (iconCode >= 151 && iconCode <= 154) {
             return R.drawable.ic_weather_cloudy; // 晚上的多云
        } else if (iconCode >= 300 && iconCode <= 318) {
            return R.drawable.ic_weather_rain; // 阵雨、雷阵雨、各种雨
        } else if (iconCode >= 350 && iconCode <= 399) {
             return R.drawable.ic_weather_rain; // 夜间的雨
        } else if (iconCode >= 302 && iconCode <= 304) {
            return R.drawable.ic_weather_thunder; // 雷雨
        } else if (iconCode >= 400 && iconCode <= 410) {
            return R.drawable.ic_weather_snow; // 雪
        } else if (iconCode >= 451 && iconCode <= 499) {
            return R.drawable.ic_weather_snow; // 夜间雪
        } else if (iconCode >= 500 && iconCode <= 515) {
            return R.drawable.ic_weather_fog; // 雾、霾、沙尘暴
        }

        return R.drawable.ic_weather_cloudy; // 默认
    }

    /**
     * 获取 OpenMeteo (WMO 标准) 对应的图标
     * @param code WMO 天气代码
     * @return 对应的 R.drawable 资源 ID
     */
    public static int getOpenMeteoIcon(int code) {
        // WMO Weather interpretation codes (WW)
        // 0: Clear sky
        // 1, 2, 3: Mainly clear, partly cloudy, and overcast
        // 45, 48: Fog and depositing rime fog
        // 51, 53, 55: Drizzle: Light, moderate, and dense intensity
        // 56, 57: Freezing Drizzle: Light and dense intensity
        // 61, 63, 65: Rain: Slight, moderate and heavy intensity
        // 66, 67: Freezing Rain: Light and heavy intensity
        // 71, 73, 75: Snow fall: Slight, moderate, and heavy intensity
        // 77: Snow grains
        // 80, 81, 82: Rain showers: Slight, moderate, and violent
        // 85, 86: Snow showers slight and heavy
        // 95: Thunderstorm: Slight or moderate
        // 96, 99: Thunderstorm with slight and heavy hail

        if (code == 0) return R.drawable.ic_weather_sunny;
        if (code >= 1 && code <= 3) return R.drawable.ic_weather_cloudy;
        if (code >= 45 && code <= 48) return R.drawable.ic_weather_fog;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return R.drawable.ic_weather_rain;
        if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) return R.drawable.ic_weather_snow;
        if (code >= 95 && code <= 99) return R.drawable.ic_weather_thunder;

        return R.drawable.ic_weather_cloudy; // 默认
    }

    /**
     * 获取高德地图天气对应的图标 (根据描述文字简单映射)
     * @param text 天气现象文字描述
     * @return 对应的 R.drawable 资源 ID
     */
    public static int getAmapIcon(String text) {
        if (text == null || text.isEmpty()) return R.drawable.ic_weather_cloudy;

        if (text.contains("晴")) return R.drawable.ic_weather_sunny;
        if (text.contains("云") || text.contains("阴")) return R.drawable.ic_weather_cloudy;
        if (text.contains("雷")) return R.drawable.ic_weather_thunder;
        if (text.contains("雨")) return R.drawable.ic_weather_rain;
        if (text.contains("雪") || text.contains("冰雹")) return R.drawable.ic_weather_snow;
        if (text.contains("雾") || text.contains("霾") || text.contains("沙") || text.contains("尘")) return R.drawable.ic_weather_fog;

        return R.drawable.ic_weather_cloudy; // 默认
    }
}
