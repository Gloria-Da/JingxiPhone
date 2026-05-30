package com.yoyo.jingxi.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.os.Handler;
import android.os.Looper;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DataSettingsActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_EXPORT = 101;
    private static final int REQUEST_CODE_IMPORT = 102;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("数据管理");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        Button btnExport = findViewById(R.id.btnExport);
        Button btnImport = findViewById(R.id.btnImport);
        Button btnClearCache = findViewById(R.id.btnClearCache);
        Button btnClearData = findViewById(R.id.btnClearData);

        btnExport.setOnClickListener(v -> startExport());
        btnImport.setOnClickListener(v -> startImport());
        btnClearCache.setOnClickListener(v -> clearImageCache());
        
        Button btnClearAudioCache = findViewById(R.id.btnClearAudioCache);
        if (btnClearAudioCache != null) {
            btnClearAudioCache.setOnClickListener(v -> clearAudioCache());
        }
        
        btnClearData.setOnClickListener(v -> showClearDataConfirmDialog());
        
        calculateCacheSizes();
    }
    
    private void calculateCacheSizes() {
        Executors.newSingleThreadExecutor().execute(() -> {
            long imageSize = 0;
            try {
                File glideCache = com.bumptech.glide.Glide.getPhotoCacheDir(this);
                if (glideCache != null) {
                    imageSize = getFolderSize(glideCache);
                }
            } catch (Exception e) {}
            
            long audioSize = 0;
            File voiceDir = new File(getExternalFilesDir(null), "voice");
            if (voiceDir.exists()) {
                audioSize += getFolderSize(voiceDir);
            }
            File cacheDir = getExternalCacheDir();
            if (cacheDir != null && cacheDir.exists()) {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().startsWith("voice_") && f.getName().endsWith(".mp3")) {
                            audioSize += f.length();
                        }
                    }
                }
            }
            
            final String finalImageSize = formatSize(imageSize);
            final String finalAudioSize = formatSize(audioSize);
            
            mainHandler.post(() -> {
                android.widget.TextView tvImageCacheSize = findViewById(R.id.tvImageCacheSize);
                if (tvImageCacheSize != null) tvImageCacheSize.setText(finalImageSize);
                
                android.widget.TextView tvAudioCacheSize = findViewById(R.id.tvAudioCacheSize);
                if (tvAudioCacheSize != null) tvAudioCacheSize.setText(finalAudioSize);
            });
        });
    }
    
    private long getFolderSize(File file) {
        long size = 0;
        try {
            File[] fileList = file.listFiles();
            if (fileList != null) {
                for (File f : fileList) {
                    if (f.isDirectory()) {
                        size = size + getFolderSize(f);
                    } else {
                        size = size + f.length();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return size;
    }
    
    private String formatSize(long size) {
        if (size <= 0) return "0 MB";
        float result = (float) size / (1024 * 1024);
        return String.format(Locale.getDefault(), "%.2f MB", result);
    }
    
    private void clearAudioCache() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 清理用户录音缓存
                File voiceDir = new File(getExternalFilesDir(null), "voice");
                if (voiceDir.exists()) {
                    File[] files = voiceDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            f.delete();
                        }
                    }
                }
                
                // 清理AI生成的语音缓存
                File cacheDir = getExternalCacheDir();
                if (cacheDir != null && cacheDir.exists()) {
                    File[] files = cacheDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.getName().startsWith("voice_") && f.getName().endsWith(".mp3")) {
                                f.delete();
                            }
                        }
                    }
                }
                
                mainHandler.post(() -> {
                    Toast.makeText(DataSettingsActivity.this, "语音缓存清理成功", Toast.LENGTH_SHORT).show();
                    calculateCacheSizes(); // 刷新显示
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> Toast.makeText(DataSettingsActivity.this, "语音缓存清理失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void clearImageCache() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 清理 Glide 的磁盘缓存
                com.bumptech.glide.Glide.get(DataSettingsActivity.this).clearDiskCache();
                mainHandler.post(() -> {
                    // 清理 Glide 的内存缓存
                    com.bumptech.glide.Glide.get(DataSettingsActivity.this).clearMemory();
                    Toast.makeText(DataSettingsActivity.this, "图片缓存清理成功", Toast.LENGTH_SHORT).show();
                    calculateCacheSizes(); // 刷新显示
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> Toast.makeText(DataSettingsActivity.this, "图片缓存清理失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showClearDataConfirmDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("严重警告")
                .setMessage("此操作将永久删除应用内的所有数据（包括所有角色、人设、聊天记录和设置）。操作不可逆！确定要继续吗？")
                .setPositiveButton("清空全部数据", (d, which) -> {
                    performClearAllData();
                })
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
        });
        
        dialog.show();
    }

    private void performClearAllData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                com.yoyo.jingxi.data.AppDatabase db = com.yoyo.jingxi.data.AppDatabase.getDatabase(this);
                db.clearAllTables();
                
                // Clear SharedPreferences
                getSharedPreferences("jingxi_prefs", MODE_PRIVATE).edit().clear().commit();
                getSharedPreferences("theme_prefs", MODE_PRIVATE).edit().clear().commit();
                getSharedPreferences("jingxi_theme_prefs", MODE_PRIVATE).edit().clear().commit();
                
                showToast("所有数据已清空，应用即将重启...");
                
                mainHandler.postDelayed(() -> {
                    // Close db before restart
                    db.close();
                    
                    // Restart app via DesktopActivity
                    Intent intent = new Intent(this, DesktopActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    Runtime.getRuntime().exit(0);
                }, 1500);
                
            } catch (Exception e) {
                e.printStackTrace();
                showToast("清空数据失败：" + e.getMessage());
            }
        });
    }

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "jingxiphone_backup_" + timeStamp + ".zip");
        startActivityForResult(intent, REQUEST_CODE_EXPORT);
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == REQUEST_CODE_EXPORT) {
            performExport(uri);
        } else if (requestCode == REQUEST_CODE_IMPORT) {
            performImport(uri);
        }
    }

    private void performExport(Uri destUri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Checkpoint db to ensure all data is written
                com.yoyo.jingxi.data.AppDatabase.getDatabase(this).getOpenHelper().getWritableDatabase().query("PRAGMA wal_checkpoint(FULL)").moveToFirst();

                File dbFile = getDatabasePath("jingxi_database");
                File dbWalFile = getDatabasePath("jingxi_database-wal");
                File dbShmFile = getDatabasePath("jingxi_database-shm");
                
                File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
                File prefsFile = new File(prefsDir, "jingxi_prefs.xml");
                File themePrefsFile = new File(prefsDir, "theme_prefs.xml");
                File themeManagerPrefsFile = new File(prefsDir, "jingxi_theme_prefs.xml");

                try (OutputStream out = getContentResolver().openOutputStream(destUri);
                     ZipOutputStream zos = new ZipOutputStream(out)) {
                    
                    // Add DB files
                    addFileToZip(dbFile, "jingxi_database", zos);
                    addFileToZip(dbWalFile, "jingxi_database-wal", zos);
                    addFileToZip(dbShmFile, "jingxi_database-shm", zos);
                    
                    // Add Prefs file
                    addFileToZip(prefsFile, "jingxi_prefs.xml", zos);
                    if (themePrefsFile.exists()) {
                        addFileToZip(themePrefsFile, "theme_prefs.xml", zos);
                    }
                    if (themeManagerPrefsFile.exists()) {
                        addFileToZip(themeManagerPrefsFile, "jingxi_theme_prefs.xml", zos);
                    }

                    // Add all files in getFilesDir() recursively
                    File filesDir = getFilesDir();
                    if (filesDir != null && filesDir.exists()) {
                        addFolderToZip(filesDir, "files", zos);
                    }

                    // Add all files in getExternalFilesDir(null) recursively
                    File extFilesDir = getExternalFilesDir(null);
                    if (extFilesDir != null && extFilesDir.exists()) {
                        addFolderToZip(extFilesDir, "extFiles", zos);
                    }

                    // Add all files in getCacheDir() recursively (avatars might be here)
                    File cacheDir = getCacheDir();
                    if (cacheDir != null && cacheDir.exists()) {
                        addFolderToZip(cacheDir, "cache", zos);
                    }
                    
                    zos.finish();
                    showToast("导出成功！");
                }
            } catch (Exception e) {
                e.printStackTrace();
                showToast("导出失败：" + e.getMessage());
            }
        });
    }

    private void addFileToZip(File file, String entryName, ZipOutputStream zos) throws Exception {
        if (file == null || !file.exists()) return;
        
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
        }
        zos.closeEntry();
    }

    private void addFolderToZip(File folder, String parentPath, ZipOutputStream zos) throws Exception {
        if (folder == null || !folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            String entryPath = parentPath + "/" + file.getName();
            if (file.isDirectory()) {
                addFolderToZip(file, entryPath, zos);
            } else {
                addFileToZip(file, entryPath, zos);
            }
        }
    }

    private void performImport(Uri sourceUri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Close current db connection
                com.yoyo.jingxi.data.AppDatabase.getDatabase(this).close();

                File dbFile = getDatabasePath("jingxi_database");
                File dbWalFile = getDatabasePath("jingxi_database-wal");
                File dbShmFile = getDatabasePath("jingxi_database-shm");
                
                File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
                File prefsFile = new File(prefsDir, "jingxi_prefs.xml");
                File themePrefsFile = new File(prefsDir, "theme_prefs.xml");
                File themeManagerPrefsFile = new File(prefsDir, "jingxi_theme_prefs.xml");

                // Ensure directories exist
                if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
                    dbFile.getParentFile().mkdirs();
                }
                if (!prefsDir.exists()) {
                    prefsDir.mkdirs();
                }

                // Clear old DB temp files just in case
                if (dbWalFile.exists()) dbWalFile.delete();
                if (dbShmFile.exists()) dbShmFile.delete();

                try (InputStream in = getContentResolver().openInputStream(sourceUri);
                     ZipInputStream zis = new ZipInputStream(in)) {
                    
                    ZipEntry entry;
                    boolean hasDb = false;
                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();
                        File targetFile = null;
                        
                        if ("jingxi_database".equals(name)) {
                            targetFile = dbFile;
                            hasDb = true;
                        } else if ("jingxi_database-wal".equals(name)) {
                            targetFile = dbWalFile;
                        } else if ("jingxi_database-shm".equals(name)) {
                            targetFile = dbShmFile;
                        } else if ("jingxi_prefs.xml".equals(name)) {
                            targetFile = prefsFile;
                        } else if ("theme_prefs.xml".equals(name)) {
                            targetFile = themePrefsFile;
                        } else if ("jingxi_theme_prefs.xml".equals(name)) {
                            targetFile = themeManagerPrefsFile;
                        } else if (name.startsWith("files/")) {
                            // Extract to getFilesDir()
                            String relativePath = name.substring("files/".length());
                            if (!relativePath.isEmpty()) {
                                targetFile = new File(getFilesDir(), relativePath);
                            }
                        } else if (name.startsWith("extFiles/")) {
                            // Extract to getExternalFilesDir(null)
                            String relativePath = name.substring("extFiles/".length());
                            if (!relativePath.isEmpty() && getExternalFilesDir(null) != null) {
                                targetFile = new File(getExternalFilesDir(null), relativePath);
                            }
                        } else if (name.startsWith("cache/")) {
                            // Extract to getCacheDir()
                            String relativePath = name.substring("cache/".length());
                            if (!relativePath.isEmpty()) {
                                targetFile = new File(getCacheDir(), relativePath);
                            }
                        }

                        if (targetFile != null) {
                            // Ensure parent directory exists
                            if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
                                targetFile.getParentFile().mkdirs();
                            }
                            // Do not create FileOutputStream for directories (which usually shouldn't happen with our zipping logic unless empty folders are added)
                            if (!name.endsWith("/")) {
                                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                                    byte[] buffer = new byte[4096];
                                    int len;
                                    while ((len = zis.read(buffer)) > 0) {
                                        fos.write(buffer, 0, len);
                                    }
                                }
                            }
                        }
                        zis.closeEntry();
                    }

                    if (hasDb) {
                        showToast("导入成功，应用即将重启！");
                        mainHandler.postDelayed(() -> {
                            Intent intent = new Intent(this, DesktopActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            Runtime.getRuntime().exit(0);
                        }, 2000);
                    } else {
                        showToast("导入失败：压缩包内未找到数据库文件。");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                showToast("导入失败：" + e.getMessage());
            }
        });
    }

    private void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(DataSettingsActivity.this, msg, Toast.LENGTH_SHORT).show());
    }
}
