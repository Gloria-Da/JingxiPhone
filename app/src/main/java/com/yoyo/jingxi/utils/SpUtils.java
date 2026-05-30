package com.yoyo.jingxi.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SpUtils {
    private static final String PREF_NAME = "jingxi_prefs";
    private static SharedPreferences sharedPreferences;

    public static void init(Context context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    public static void putString(String key, String value) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putString(key, value).apply();
        }
    }

    public static String getString(String key, String defaultValue) {
        if (sharedPreferences != null) {
            return sharedPreferences.getString(key, defaultValue);
        }
        return defaultValue;
    }

    public static void putInt(String key, int value) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putInt(key, value).apply();
        }
    }

    public static int getInt(String key, int defaultValue) {
        if (sharedPreferences != null) {
            return sharedPreferences.getInt(key, defaultValue);
        }
        return defaultValue;
    }

    public static void putBoolean(String key, boolean value) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putBoolean(key, value).apply();
        }
    }

    public static void putFloat(String key, float value) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putFloat(key, value).apply();
        }
    }

    public static float getFloat(String key, float defaultValue) {
        if (sharedPreferences != null) {
            return sharedPreferences.getFloat(key, defaultValue);
        }
        return defaultValue;
    }

    public static long getLong(String key, long defValue) {
        return getSharedPreferences().getLong(key, defValue);
    }

    public static void putLong(String key, long value) {
        getSharedPreferences().edit().putLong(key, value).apply();
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        if (sharedPreferences != null) {
            return sharedPreferences.getBoolean(key, defaultValue);
        }
        return defaultValue;
    }
    
    private static SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }
}
