package com.yoyo.jingxi.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.yoyo.jingxi.R;

public class ThemeManager {

    private static final String PREF_NAME = "theme_prefs";
    private static final String KEY_THEME = "current_theme";
    private static final String KEY_BG_IMAGE = "bg_image_path";
    private static final String KEY_NIGHT_MODE = "night_mode";

    public static final int THEME_YELLOW = 0;
    public static final int THEME_PINK = 1;
    public static final int THEME_BLUE = 2;
    public static final int THEME_WHITE = 3;
    public static final int THEME_DARK = 4;
    
    public static boolean isDarkMode(Context context) {
        if (getTheme(context) == THEME_DARK) return true;
        
        int nightMode = getNightMode(context);
        if (nightMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) {
            return true;
        } else if (nightMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) {
            return false;
        }
        
        int currentNightMode = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static int getNightMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_NIGHT_MODE, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static void setNightMode(Context context, int nightMode) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_NIGHT_MODE, nightMode).apply();
    }

    public static void applyTheme(Context context) {
        if (isDarkMode(context)) {
            context.setTheme(R.style.Theme_JingxiPhone_Dark);
            return;
        }

        int theme = getTheme(context);
        switch (theme) {
            case THEME_YELLOW:
                context.setTheme(R.style.Theme_JingxiPhone_Yellow);
                break;
            case THEME_PINK:
                context.setTheme(R.style.Theme_JingxiPhone_Pink);
                break;
            case THEME_BLUE:
                context.setTheme(R.style.Theme_JingxiPhone_Blue);
                break;
            case THEME_WHITE:
                context.setTheme(R.style.Theme_JingxiPhone_White);
                break;
            case THEME_DARK:
                context.setTheme(R.style.Theme_JingxiPhone_Dark);
                break;
            default:
                context.setTheme(R.style.Theme_JingxiPhone_Yellow);
                break;
        }
    }

    public static void applyDesktopThemeBackground(android.app.Activity activity) {
        if (isDarkMode(activity)) {
            activity.findViewById(android.R.id.content).setBackgroundColor(activity.getResources().getColor(R.color.theme_dark_bg));
            return;
        }

        int theme = getTheme(activity);
        switch (theme) {
            case THEME_YELLOW:
                activity.findViewById(android.R.id.content).setBackgroundColor(activity.getResources().getColor(R.color.colorBackground));
                break;
            case THEME_PINK:
                activity.findViewById(android.R.id.content).setBackgroundColor(activity.getResources().getColor(R.color.pink_bg));
                break;
            case THEME_BLUE:
                activity.findViewById(android.R.id.content).setBackgroundColor(activity.getResources().getColor(R.color.blue_bg));
                break;
            case THEME_WHITE:
                activity.findViewById(android.R.id.content).setBackgroundColor(activity.getResources().getColor(R.color.white_bg));
                break;
            case THEME_DARK:
                activity.findViewById(android.R.id.content).setBackgroundColor(activity.getResources().getColor(R.color.theme_dark_bg));
                break;
        }
    }

    public static void setTheme(Context context, int theme) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME, theme).apply();
    }

    public static int getTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME, THEME_YELLOW);
    }

    public static void setBgImagePath(Context context, String path) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_BG_IMAGE, path).apply();
    }

    public static String getBgImagePath(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_BG_IMAGE, null);
    }

    public static void setDesktopPhoto1Path(Context context, String path) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("desktop_photo_1_path", path).apply();
    }

    public static String getDesktopPhoto1Path(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString("desktop_photo_1_path", null);
    }

    public static void setDesktopPhoto2Path(Context context, String path) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("desktop_photo_2_path", path).apply();
    }

    public static String getDesktopPhoto2Path(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString("desktop_photo_2_path", null);
    }

    public static void setChatBgPath(Context context, String path) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("chat_bg_path", path).apply();
    }

    public static String getChatBgPath(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString("chat_bg_path", null);
    }

}
