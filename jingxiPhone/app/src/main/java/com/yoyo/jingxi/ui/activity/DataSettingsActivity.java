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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

public class DataSettingsActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_EXPORT = 101;
    private static final int REQUEST_CODE_IMPORT = 102;
    private static final String SNAPSHOT_DB_SUFFIX = ".pre_import_backup";
    private static final String SNAPSHOT_PREFS_SUFFIX = ".pre_import_backup";
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout layoutProgress;
    private ProgressBar progressBar;
    private TextView tvProgressStatus;

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

        layoutProgress = findViewById(R.id.layoutProgress);
        progressBar = findViewById(R.id.progressBar);
        tvProgressStatus = findViewById(R.id.tvProgressStatus);

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

    // ========== Progress UI helpers ==========

    private void showProgress(String status) {
        mainHandler.post(() -> {
            layoutProgress.setVisibility(LinearLayout.VISIBLE);
            tvProgressStatus.setText(status);
            progressBar.setIndeterminate(true);
        });
    }

    private void updateProgress(String status, int percent) {
        mainHandler.post(() -> {
            layoutProgress.setVisibility(LinearLayout.VISIBLE);
            tvProgressStatus.setText(status);
            progressBar.setIndeterminate(false);
            progressBar.setProgress(Math.min(percent, 100));
        });
    }

    private void hideProgress() {
        mainHandler.post(() -> layoutProgress.setVisibility(LinearLayout.GONE));
    }

    // ========== Cache size calculation ==========

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

    // ========== Cache clearing ==========

    private void clearAudioCache() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File voiceDir = new File(getExternalFilesDir(null), "voice");
                if (voiceDir.exists()) {
                    File[] files = voiceDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            f.delete();
                        }
                    }
                }

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
                    calculateCacheSizes();
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
                com.bumptech.glide.Glide.get(DataSettingsActivity.this).clearDiskCache();
                mainHandler.post(() -> {
                    com.bumptech.glide.Glide.get(DataSettingsActivity.this).clearMemory();
                    Toast.makeText(DataSettingsActivity.this, "图片缓存清理成功", Toast.LENGTH_SHORT).show();
                    calculateCacheSizes();
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> Toast.makeText(DataSettingsActivity.this, "图片缓存清理失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ========== Clear all data ==========

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

                getSharedPreferences("jingxi_prefs", MODE_PRIVATE).edit().clear().commit();
                getSharedPreferences("theme_prefs", MODE_PRIVATE).edit().clear().commit();
                getSharedPreferences("jingxi_theme_prefs", MODE_PRIVATE).edit().clear().commit();

                showToast("所有数据已清空，应用即将重启...");

                mainHandler.postDelayed(() -> {
                    db.close();
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

    // ========== Export ==========

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "jingxiphone_backup_" + timeStamp + ".zip");
        startActivityForResult(intent, REQUEST_CODE_EXPORT);
    }

    // ========== Import ==========

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
            // Step 1: Show confirmation dialog before importing
            showImportConfirmDialog(uri);
        }
    }

    // ========== Export implementation ==========

    private void performExport(Uri destUri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                showProgress("正在准备导出...");

                com.yoyo.jingxi.data.AppDatabase db = com.yoyo.jingxi.data.AppDatabase.getDatabase(this);

                // 1. 激进 checkpoint：将 WAL 全部刷入主数据库文件
                db.getOpenHelper().getWritableDatabase().query("PRAGMA wal_checkpoint(TRUNCATE)").moveToFirst();

                // 2. 关闭 Room 实例，防止导出过程中有写入
                db.close();
                com.yoyo.jingxi.data.AppDatabase.resetInstance();

                // 3. 删除 WAL/SHM 残留文件，确保备份中不含脏 WAL
                File dbWalFile = getDatabasePath("jingxi_database-wal");
                File dbShmFile = getDatabasePath("jingxi_database-shm");
                if (dbWalFile.exists()) dbWalFile.delete();
                if (dbShmFile.exists()) dbShmFile.delete();

                updateProgress("正在计算文件大小...", 5);

                File dbFile = getDatabasePath("jingxi_database");

                File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
                File prefsFile = new File(prefsDir, "jingxi_prefs.xml");
                File themePrefsFile = new File(prefsDir, "theme_prefs.xml");
                File themeManagerPrefsFile = new File(prefsDir, "jingxi_theme_prefs.xml");

                // Pre-scan to estimate total size for progress
                long totalSize = safeLength(dbFile) + safeLength(dbWalFile) + safeLength(dbShmFile)
                        + safeLength(prefsFile);
                if (themePrefsFile.exists()) totalSize += themePrefsFile.length();
                if (themeManagerPrefsFile.exists()) totalSize += themeManagerPrefsFile.length();

                // Count files in filesDir
                File filesDir = getFilesDir();
                long filesDirSize = 0;
                if (filesDir != null && filesDir.exists()) {
                    filesDirSize = getFolderSize(filesDir);
                    totalSize += filesDirSize;
                }

                // Count files in extFiles (excluding crash_logs and model)
                Set<String> excludeDirs = new HashSet<>(Arrays.asList("crash_logs", "model"));
                File extFilesDir = getExternalFilesDir(null);
                long extFilesSize = 0;
                if (extFilesDir != null && extFilesDir.exists()) {
                    extFilesSize = getFolderSizeExcluding(extFilesDir, excludeDirs);
                    totalSize += extFilesSize;
                }

                updateProgress("正在创建备份文件...", 10);

                try (OutputStream out = getContentResolver().openOutputStream(destUri);
                     java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(out)) {

                    // Step 3: Write backup_metadata.json FIRST
                    JSONObject metadata = new JSONObject();
                    metadata.put("appVersion", getAppVersionSafe());
                    metadata.put("appVersionCode", getAppVersionCodeSafe());
                    metadata.put("dbVersion", com.yoyo.jingxi.data.AppDatabase.DB_VERSION);
                    metadata.put("exportTimestamp", System.currentTimeMillis());
                    metadata.put("exportDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                    String metaStr = metadata.toString();
                    java.util.zip.ZipEntry metaEntry = new java.util.zip.ZipEntry("backup_metadata.json");
                    zos.putNextEntry(metaEntry);
                    zos.write(metaStr.getBytes("UTF-8"));
                    zos.closeEntry();

                    long bytesWritten = 0;

                    // Add DB files
                    bytesWritten = addFileToZipWithProgress(dbFile, "jingxi_database", zos, bytesWritten, totalSize, "数据库");
                    bytesWritten = addFileToZipWithProgress(dbWalFile, "jingxi_database-wal", zos, bytesWritten, totalSize, "数据库WAL");
                    bytesWritten = addFileToZipWithProgress(dbShmFile, "jingxi_database-shm", zos, bytesWritten, totalSize, "数据库SHM");

                    // Add Prefs files
                    bytesWritten = addFileToZipWithProgress(prefsFile, "jingxi_prefs.xml", zos, bytesWritten, totalSize, "设置");
                    if (themePrefsFile.exists()) {
                        bytesWritten = addFileToZipWithProgress(themePrefsFile, "theme_prefs.xml", zos, bytesWritten, totalSize, "主题设置");
                    }
                    if (themeManagerPrefsFile.exists()) {
                        bytesWritten = addFileToZipWithProgress(themeManagerPrefsFile, "jingxi_theme_prefs.xml", zos, bytesWritten, totalSize, "主题管理设置");
                    }

                    // Add filesDir recursively
                    if (filesDir != null && filesDir.exists()) {
                        bytesWritten = addFolderToZipWithProgress(filesDir, "files", zos, bytesWritten, totalSize, "应用文件");
                    }

                    // Step 7: Add extFilesDir but EXCLUDE crash_logs and model
                    if (extFilesDir != null && extFilesDir.exists()) {
                        bytesWritten = addFolderToZipExcluding(extFilesDir, "extFiles", zos, bytesWritten, totalSize, excludeDirs, "外部文件");
                    }

                    zos.finish();
                    updateProgress("导出完成", 100);
                    showToast("导出成功！");
                }
                hideProgress();
            } catch (Exception e) {
                e.printStackTrace();
                hideProgress();
                showToast("导出失败：" + e.getMessage());
            }
        });
    }

    private long safeLength(File f) {
        return (f != null && f.exists()) ? f.length() : 0;
    }

    private long addFileToZipWithProgress(File file, String entryName,
                                          java.util.zip.ZipOutputStream zos,
                                          long bytesWritten, long totalSize,
                                          String label) throws Exception {
        if (file == null || !file.exists()) return bytesWritten;
        updateProgress("正在导出 " + label + "...", (int) (bytesWritten * 85 / Math.max(totalSize, 1)) + 10);

        java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
        zos.putNextEntry(entry);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
                bytesWritten += length;
            }
        }
        zos.closeEntry();
        return bytesWritten;
    }

    private long addFolderToZipWithProgress(File folder, String parentPath,
                                            java.util.zip.ZipOutputStream zos,
                                            long bytesWritten, long totalSize,
                                            String label) throws Exception {
        if (folder == null || !folder.exists()) return bytesWritten;
        File[] files = folder.listFiles();
        if (files == null) return bytesWritten;

        for (File file : files) {
            String entryPath = parentPath + "/" + file.getName();
            if (file.isDirectory()) {
                bytesWritten = addFolderToZipWithProgress(file, entryPath, zos, bytesWritten, totalSize, label);
            } else {
                bytesWritten = addFileToZipWithProgress(file, entryPath, zos, bytesWritten, totalSize, label);
            }
        }
        return bytesWritten;
    }

    /** Like addFolderToZipWithProgress but skips directories matching any name in excludeDirNames */
    private long addFolderToZipExcluding(File folder, String parentPath,
                                         java.util.zip.ZipOutputStream zos,
                                         long bytesWritten, long totalSize,
                                         Set<String> excludeDirNames, String label) throws Exception {
        if (folder == null || !folder.exists()) return bytesWritten;
        File[] files = folder.listFiles();
        if (files == null) return bytesWritten;

        for (File file : files) {
            if (file.isDirectory() && excludeDirNames.contains(file.getName())) {
                continue; // skip excluded directories
            }
            String entryPath = parentPath + "/" + file.getName();
            if (file.isDirectory()) {
                bytesWritten = addFolderToZipExcluding(file, entryPath, zos, bytesWritten, totalSize, excludeDirNames, label);
            } else {
                bytesWritten = addFileToZipWithProgress(file, entryPath, zos, bytesWritten, totalSize, label);
            }
        }
        return bytesWritten;
    }

    private long getFolderSizeExcluding(File folder, Set<String> excludeDirNames) {
        long size = 0;
        try {
            File[] fileList = folder.listFiles();
            if (fileList != null) {
                for (File f : fileList) {
                    if (f.isDirectory()) {
                        if (excludeDirNames.contains(f.getName())) continue;
                        size += getFolderSizeExcluding(f, excludeDirNames);
                    } else {
                        size += f.length();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return size;
    }

    // ========== Import confirmation & metadata reading ==========

    /**
     * Step 1: Read backup metadata from zip, then show confirmation dialog.
     */
    private void showImportConfirmDialog(Uri sourceUri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Read metadata from zip without extracting
                JSONObject metadata = null;
                int backupDbVersion = -1;

                try (InputStream in = getContentResolver().openInputStream(sourceUri);
                     java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(in)) {

                    java.util.zip.ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if ("backup_metadata.json".equals(entry.getName())) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(zis, "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line);
                            }
                            metadata = new JSONObject(sb.toString());
                            break;
                        }
                        zis.closeEntry();
                    }
                }

                // Build confirmation message
                StringBuilder msg = new StringBuilder();
                msg.append("此操作将完全覆盖当前所有的数据与设置！\n\n");

                if (metadata != null) {
                    backupDbVersion = metadata.optInt("dbVersion", -1);
                    String exportDate = metadata.optString("exportDate", "未知");
                    String appVersion = metadata.optString("appVersion", "未知");
                    msg.append("备份信息：\n");
                    msg.append("  • 导出时间：").append(exportDate).append("\n");
                    msg.append("  • 应用版本：").append(appVersion).append("\n");
                    msg.append("  • 数据库版本：").append(backupDbVersion).append("\n");
                } else {
                    msg.append("⚠ 未找到备份元数据，无法验证备份版本。\n");
                }

                // Step 3: Version compatibility check
                int currentDbVersion = com.yoyo.jingxi.data.AppDatabase.DB_VERSION;
                boolean canImport = true;
                String blockReason = null;

                if (metadata != null && backupDbVersion > 0) {
                    if (backupDbVersion > currentDbVersion) {
                        canImport = false;
                        blockReason = "备份来自更高版本的应用（DB v" + backupDbVersion
                                + " > 当前 v" + currentDbVersion + "），请先更新应用再导入。";
                    } else if (backupDbVersion < com.yoyo.jingxi.data.AppDatabase.MIN_DB_VERSION) {
                        canImport = false;
                        blockReason = "备份版本过旧（DB v" + backupDbVersion
                                + "，早于数据库管理系统最低版本 v"
                                + com.yoyo.jingxi.data.AppDatabase.MIN_DB_VERSION + "），无法安全导入。";
                    } else if (backupDbVersion < currentDbVersion) {
                        // Old version but within supported range — Room will migrate on restart
                        msg.append("\n✅ 备份数据库版本较低（v").append(backupDbVersion)
                            .append("），导入后系统将自动升级数据库到当前版本（v")
                            .append(currentDbVersion).append("），数据不会丢失。\n");
                    }
                }

                final JSONObject finalMeta = metadata;
                final boolean finalCanImport = canImport;
                final String finalBlockReason = blockReason;
                final String confirmMsg = msg.toString();

                mainHandler.post(() -> {
                    if (!finalCanImport) {
                        // Block import completely
                        new AlertDialog.Builder(DataSettingsActivity.this)
                                .setTitle("无法导入")
                                .setMessage(finalBlockReason)
                                .setPositiveButton("确定", null)
                                .show();
                        return;
                    }

                    // Show confirmation with metadata
                    AlertDialog dialog = new AlertDialog.Builder(DataSettingsActivity.this)
                            .setTitle("确认导入备份")
                            .setMessage(confirmMsg + "\n⚠ 注意：导入后应用将自动重启。\n确定要继续吗？")
                            .setPositiveButton("确认导入并覆盖", (d, which) -> {
                                performImport(sourceUri, finalMeta);
                            })
                            .setNegativeButton("取消", null)
                            .create();

                    dialog.setOnShowListener(d -> {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                    });

                    dialog.show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                // If metadata reading fails, still show confirmation without metadata
                mainHandler.post(() -> {
                    new AlertDialog.Builder(DataSettingsActivity.this)
                            .setTitle("确认导入备份")
                            .setMessage("⚠ 无法读取备份文件信息。\n\n导入数据会完全覆盖当前所有的数据与设置！\n\n确定要继续吗？")
                            .setPositiveButton("仍然导入", (d, which) -> {
                                performImport(sourceUri, null);
                            })
                            .setNegativeButton("取消", null)
                            .create()
                            .show();
                });
            }
        });
    }

    // ========== Import implementation ==========

    private void performImport(Uri sourceUri, JSONObject metadata) {
        Executors.newSingleThreadExecutor().execute(() -> {
            // Step 2: Create safety snapshot of current data
            boolean snapshotCreated = createPreImportSnapshot();
            if (!snapshotCreated) {
                showToast("导入失败：无法创建当前数据的安全快照。");
                return;
            }

            try {
                showProgress("正在准备导入...");

                // Step 5: Reset Room singleton so it re-opens the new db file
                com.yoyo.jingxi.data.AppDatabase.resetInstance();

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

                // Clear old DB temp files
                if (dbWalFile.exists()) dbWalFile.delete();
                if (dbShmFile.exists()) dbShmFile.delete();

                updateProgress("正在解压备份文件...", 5);

                try (InputStream in = getContentResolver().openInputStream(sourceUri);
                     java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(in)) {

                    java.util.zip.ZipEntry entry;
                    boolean hasDb = false;
                    int entryCount = 0;
                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();

                        // Step 3: Skip metadata — already read in confirmation dialog
                        if ("backup_metadata.json".equals(name)) {
                            zis.closeEntry();
                            continue;
                        }

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
                            String relativePath = name.substring("files/".length());
                            if (!relativePath.isEmpty()) {
                                targetFile = new File(getFilesDir(), relativePath);
                            }
                        } else if (name.startsWith("extFiles/")) {
                            String relativePath = name.substring("extFiles/".length());
                            if (!relativePath.isEmpty() && getExternalFilesDir(null) != null) {
                                targetFile = new File(getExternalFilesDir(null), relativePath);
                            }
                        }

                        if (targetFile != null) {
                            if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
                                targetFile.getParentFile().mkdirs();
                            }
                            if (!name.endsWith("/")) {
                                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                                    byte[] buffer = new byte[8192];
                                    int len;
                                    while ((len = zis.read(buffer)) > 0) {
                                        fos.write(buffer, 0, len);
                                    }
                                }
                            }
                        }
                        zis.closeEntry();
                        entryCount++;
                        if (entryCount % 5 == 0) {
                            updateProgress("正在导入数据... (" + entryCount + " 个文件)", 10 + entryCount * 2);
                        }
                    }

                    // Delete extracted WAL/SHM files — they belong to the export-time
                    // connection and will cause PRAGMA integrity_check to fail here.
                    // The FULL checkpoint before export ensures no uncommitted data is in WAL.
                    if (dbWalFile.exists()) dbWalFile.delete();
                    if (dbShmFile.exists()) dbShmFile.delete();

                    updateProgress("正在验证数据库完整性...", 80);

                    if (!hasDb) {
                        // Step 2: Rollback — database not found in zip
                        restoreFromSnapshot();
                        showToast("导入失败：压缩包内未找到数据库文件。数据已回滚。");
                        hideProgress();
                        return;
                    }

                    // Step 6: Verify extracted database is a valid SQLite file (magic header check)
                    boolean dbValid = isValidSqliteFile(dbFile);
                    if (!dbValid) {
                        restoreFromSnapshot();
                        showToast("导入失败：备份中的数据库文件损坏或格式不兼容。数据已回滚。");
                        hideProgress();
                        return;
                    }

                    // Step 6: Verify Room can open the imported database (handles WAL recovery properly)
                    boolean roomOk = verifyRoomCompatibility();
                    if (!roomOk) {
                        restoreFromSnapshot();
                        String errDetail = lastRoomError != null ? lastRoomError : "未知错误";
                        hideProgress();
                        mainHandler.post(() -> new AlertDialog.Builder(DataSettingsActivity.this)
                            .setTitle("导入失败")
                            .setMessage("备份数据库与当前版本不兼容。\n\n错误详情:\n" + errDetail)
                            .setPositiveButton("确定", null)
                            .show());
                        return;
                    }

                    // Step 2: Success — delete snapshot and restart
                    deleteSnapshot();

                    updateProgress("导入成功！应用即将重启...", 100);
                    showToast("导入成功，应用即将重启！");
                    mainHandler.postDelayed(() -> {
                        Intent intent = new Intent(this, DesktopActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        Runtime.getRuntime().exit(0);
                    }, 2000);
                }
            } catch (java.util.zip.ZipException e) {
                e.printStackTrace();
                restoreFromSnapshot();
                hideProgress();
                // ZIP 损坏通常是导出时数据库未完全刷新导致，提示用户重新导出
                showToast("导入失败：备份文件已损坏或不完整。\n请在原应用中重新执行「导出全部数据」，然后再试。");
            } catch (java.io.IOException e) {
                e.printStackTrace();
                restoreFromSnapshot();
                hideProgress();
                String msg = e.getMessage();
                if (msg != null && msg.contains("ZLIB")) {
                    showToast("导入失败：备份文件压缩数据损坏。\n请重新导出备份后再试。");
                } else {
                    showToast("导入失败：文件读取错误。\n" + (msg != null ? msg : "") + "\n数据已回滚。");
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Step 2: Rollback on any error
                restoreFromSnapshot();
                hideProgress();
                showToast("导入失败：" + e.getMessage() + "。数据已回滚。");
            }
        });
    }

    // ========== Safety Snapshot (Step 2) ==========

    /**
     * Create a safety snapshot of current database and preferences before import.
     * @return true if snapshot created successfully
     */
    private boolean createPreImportSnapshot() {
        try {
            File dbFile = getDatabasePath("jingxi_database");
            File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");

            // Snapshot database
            if (dbFile.exists()) {
                File dbSnapshot = new File(dbFile.getParentFile(), dbFile.getName() + SNAPSHOT_DB_SUFFIX);
                copyFile(dbFile, dbSnapshot);
            }

            // Snapshot shared preferences
            String[] prefsFiles = {"jingxi_prefs.xml", "theme_prefs.xml", "jingxi_theme_prefs.xml"};
            for (String prefName : prefsFiles) {
                File prefFile = new File(prefsDir, prefName);
                if (prefFile.exists()) {
                    File prefSnapshot = new File(prefsDir, prefName + SNAPSHOT_PREFS_SUFFIX);
                    copyFile(prefFile, prefSnapshot);
                }
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Restore data from safety snapshot. Called when import fails.
     */
    private void restoreFromSnapshot() {
        try {
            File dbFile = getDatabasePath("jingxi_database");
            File dbSnapshot = new File(dbFile.getParentFile(), dbFile.getName() + SNAPSHOT_DB_SUFFIX);

            if (dbSnapshot.exists()) {
                // Close any open connections and reset singleton
                com.yoyo.jingxi.data.AppDatabase.resetInstance();

                // Delete WAL/SHM before restoring
                File walFile = getDatabasePath("jingxi_database-wal");
                File shmFile = getDatabasePath("jingxi_database-shm");
                if (walFile.exists()) walFile.delete();
                if (shmFile.exists()) shmFile.delete();

                // Restore database
                copyFile(dbSnapshot, dbFile);

                // Reset singleton again so Room opens the restored db
                com.yoyo.jingxi.data.AppDatabase.resetInstance();
            }

            // Restore shared preferences
            File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
            String[] prefsFiles = {"jingxi_prefs.xml", "theme_prefs.xml", "jingxi_theme_prefs.xml"};
            for (String prefName : prefsFiles) {
                File prefSnapshot = new File(prefsDir, prefName + SNAPSHOT_PREFS_SUFFIX);
                if (prefSnapshot.exists()) {
                    File prefFile = new File(prefsDir, prefName);
                    copyFile(prefSnapshot, prefFile);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            showToast("⚠ 数据回滚失败！部分数据可能丢失。请尝试重新导入或联系支持。");
        }
    }

    /**
     * Delete safety snapshot after successful import.
     */
    private void deleteSnapshot() {
        try {
            File dbFile = getDatabasePath("jingxi_database");
            File dbSnapshot = new File(dbFile.getParentFile(), dbFile.getName() + SNAPSHOT_DB_SUFFIX);
            if (dbSnapshot.exists()) dbSnapshot.delete();

            File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
            String[] prefsFiles = {"jingxi_prefs.xml", "theme_prefs.xml", "jingxi_theme_prefs.xml"};
            for (String prefName : prefsFiles) {
                File prefSnapshot = new File(prefsDir, prefName + SNAPSHOT_PREFS_SUFFIX);
                if (prefSnapshot.exists()) prefSnapshot.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyFile(File source, File dest) throws Exception {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest);
             FileChannel inChannel = fis.getChannel();
             FileChannel outChannel = fos.getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }

    // ========== Database integrity verification (Step 6) ==========

    /**
     * Verify the file is a valid SQLite database by checking its magic header bytes.
     * This is non-invasive (no SQLite engine involvement), avoiding WAL-mode compatibility
     * issues that can occur with raw openDatabase() on newly-extracted database files.
     */
    private boolean isValidSqliteFile(File dbFile) {
        if (dbFile == null || !dbFile.exists() || dbFile.length() < 64) return false;
        try (FileInputStream fis = new FileInputStream(dbFile)) {
            byte[] header = new byte[16];
            if (fis.read(header) != 16) return false;
            // SQLite magic header is "SQLite format 3\000"
            String magic = new String(header, 0, 16, "UTF-8");
            return magic.startsWith("SQLite format 3");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Verify the imported database is compatible with Room by opening it and checking
     * that schema migration can proceed.
     */
    private String lastRoomError = null;

    private boolean verifyRoomCompatibility() {
        lastRoomError = null;
        try {
            // Reset singleton so Room creates a fresh instance against the imported db
            com.yoyo.jingxi.data.AppDatabase.resetInstance();

            // Open the database via Room — this triggers migration and validates schema
            com.yoyo.jingxi.data.AppDatabase db = com.yoyo.jingxi.data.AppDatabase.getDatabase(this);
            // If we got here without exception, the database is compatible
            // Check that the version matches what we expect after migration
            int actualVersion = db.getOpenHelper().getReadableDatabase().getVersion();
            int expectedVersion = com.yoyo.jingxi.data.AppDatabase.DB_VERSION;

            // Room may have already migrated the database on open, so version should match
            if (actualVersion != expectedVersion) {
                // If version still doesn't match, something is wrong
                lastRoomError = "数据库版本不匹配: 实际=" + actualVersion + " 期望=" + expectedVersion;
                com.yoyo.jingxi.data.AppDatabase.resetInstance();
                return false;
            }

            // Close and reset so import can proceed
            db.close();
            com.yoyo.jingxi.data.AppDatabase.resetInstance();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            lastRoomError = e.getClass().getSimpleName() + ": " + e.getMessage();
            com.yoyo.jingxi.data.AppDatabase.resetInstance();
            return false;
        }
    }

    // ========== Version helpers (safe) ==========

    private String getAppVersionSafe() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private int getAppVersionCodeSafe() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    // ========== Toast helper ==========

    private void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(DataSettingsActivity.this, msg, Toast.LENGTH_SHORT).show());
    }
}
