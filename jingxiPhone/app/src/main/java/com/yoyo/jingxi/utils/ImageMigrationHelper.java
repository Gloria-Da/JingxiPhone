package com.yoyo.jingxi.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.MyPersona;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * One-time migration: copy user images from getCacheDir() to getFilesDir()
 * so they survive cache clears and app upgrades.
 */
public class ImageMigrationHelper {

    private static final String FLAG_KEY = "image_storage_migrated";

    public static void migrateIfNeeded(Context context) {
        SharedPreferences flagPrefs = context.getSharedPreferences("jingxi_prefs", Context.MODE_PRIVATE);
        if (flagPrefs.getBoolean(FLAG_KEY, false)) {
            return;
        }

        File cacheDir = context.getCacheDir();
        File filesDir = context.getFilesDir();

        // Migrate theme_prefs paths
        SharedPreferences themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE);
        migratePrefPath(themePrefs, "bg_image_path", cacheDir, filesDir);
        migratePrefPath(themePrefs, "global_bg_image_path", cacheDir, filesDir);
        migratePrefPath(themePrefs, "desktop_photo_1_path", cacheDir, filesDir);
        migratePrefPath(themePrefs, "desktop_photo_2_path", cacheDir, filesDir);
        migratePrefPath(themePrefs, "chat_bg_path", cacheDir, filesDir);

        // Migrate all CHAT_BG_* keys in jingxi_prefs
        SharedPreferences jingxiPrefs = context.getSharedPreferences("jingxi_prefs", Context.MODE_PRIVATE);
        Map<String, ?> allJingxiPrefs = jingxiPrefs.getAll();
        SharedPreferences.Editor jingxiEditor = jingxiPrefs.edit();
        boolean jingxiChanged = false;
        for (String key : allJingxiPrefs.keySet()) {
            if (key.startsWith("CHAT_BG_")) {
                Object value = allJingxiPrefs.get(key);
                if (value instanceof String) {
                    String newPath = migratePath((String) value, cacheDir, filesDir);
                    if (newPath != null && !newPath.equals(value)) {
                        jingxiEditor.putString(key, newPath);
                        jingxiChanged = true;
                    }
                }
            }
        }
        if (jingxiChanged) {
            jingxiEditor.apply();
        }

        // Migrate Room DB avatar paths on a background thread
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(context);

                List<MyPersona> personas = db.myPersonaDao().getAllPersonasSync();
                for (MyPersona p : personas) {
                    if (p.avatarPath != null && !p.avatarPath.isEmpty()) {
                        String newPath = migratePath(p.avatarPath, cacheDir, filesDir);
                        if (newPath != null && !newPath.equals(p.avatarPath)) {
                            p.avatarPath = newPath;
                            db.myPersonaDao().update(p);
                        }
                    }
                }

                List<Character> characters = db.characterDao().getAllCharactersSync();
                for (Character c : characters) {
                    if (c.avatarPath != null && !c.avatarPath.isEmpty()) {
                        String newPath = migratePath(c.avatarPath, cacheDir, filesDir);
                        if (newPath != null && !newPath.equals(c.avatarPath)) {
                            c.avatarPath = newPath;
                            db.characterDao().update(c);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }).start();

        flagPrefs.edit().putBoolean(FLAG_KEY, true).apply();
    }

    private static void migratePrefPath(SharedPreferences prefs, String key, File cacheDir, File filesDir) {
        String oldPath = prefs.getString(key, null);
        if (oldPath == null || oldPath.isEmpty()) return;
        String newPath = migratePath(oldPath, cacheDir, filesDir);
        if (newPath != null && !newPath.equals(oldPath)) {
            prefs.edit().putString(key, newPath).apply();
        }
    }

    /**
     * If the path points to a file inside cacheDir that still exists,
     * copy it to filesDir and return the new URI. Otherwise return the original path.
     */
    private static String migratePath(String uriString, File cacheDir, File filesDir) {
        if (uriString == null || uriString.isEmpty()) return uriString;

        try {
            Uri uri = Uri.parse(uriString);
            String scheme = uri.getScheme();
            if (!"file".equals(scheme)) return uriString;

            String filePath = uri.getPath();
            if (filePath == null) return uriString;

            File oldFile = new File(filePath);
            if (!oldFile.exists()) return uriString;

            // Only migrate files that are inside the cache directory
            String cachePath = cacheDir.getAbsolutePath();
            String oldAbsPath = oldFile.getAbsolutePath();
            if (!oldAbsPath.startsWith(cachePath)) return uriString;

            // Copy to filesDir with a stable name to avoid overwriting existing files
            String fileName = oldFile.getName();
            // Ensure uniqueness: prepend "migrated_" if not already present
            String newFileName = fileName.startsWith("migrated_") ? fileName : "migrated_" + fileName;
            File newFile = new File(filesDir, newFileName);

            // Avoid overwriting an already-migrated file
            if (newFile.exists()) {
                return Uri.fromFile(newFile).toString();
            }

            copyFile(oldFile, newFile);
            return Uri.fromFile(newFile).toString();
        } catch (Exception e) {
            return uriString;
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }
}
