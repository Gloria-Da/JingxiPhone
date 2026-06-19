package com.yoyo.jingxi.network;

import android.content.Context;
import android.net.Uri;

import com.yoyo.jingxi.JingxiApplication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SttModelManager {

    private static final String MODEL_DIR = "stt_models";
    private static final String MODEL_ONNX = "model_q8.onnx";
    private static final String TOKENS_TXT = "tokens.txt";

    // User-configurable model download URL base (default: GitHub Releases)
    // Set via SpUtils key "stt_model_url_base"
    private static final String DEFAULT_MODEL_URL_BASE =
            "https://github.com/Gloria-Da/JingxiPhone/releases/download/models-v1/";

    private static volatile SttModelManager instance;
    private final File modelDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient;

    public enum Status {
        NOT_DOWNLOADED,
        DOWNLOADING,
        READY,
        ERROR
    }

    private volatile Status status = Status.NOT_DOWNLOADED;
    private volatile int downloadProgress;
    private volatile String errorMessage;
    private DownloadCallback downloadCallback;

    public interface DownloadCallback {
        void onProgress(int percent);
        void onComplete(boolean success, String message);
    }

    private SttModelManager() {
        Context context = JingxiApplication.getInstance();
        modelDir = new File(context.getExternalFilesDir(null), MODEL_DIR);
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    public static SttModelManager getInstance() {
        if (instance == null) {
            synchronized (SttModelManager.class) {
                if (instance == null) {
                    instance = new SttModelManager();
                }
            }
        }
        return instance;
    }

    public Status getStatus() {
        if (status == Status.NOT_DOWNLOADED && isModelReady()) {
            status = Status.READY;
        }
        return status;
    }

    public int getDownloadProgress() {
        return downloadProgress;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isModelReady() {
        return new File(modelDir, MODEL_ONNX).exists()
                && new File(modelDir, TOKENS_TXT).exists();
    }

    public String getModelDirPath() {
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }
        ensureTokens();
        return modelDir.getAbsolutePath();
    }

    public void startDownload(DownloadCallback callback) {
        this.downloadCallback = callback;
        this.errorMessage = null;

        if (isModelReady()) {
            status = Status.READY;
            if (callback != null) {
                callback.onComplete(true, "模型已就绪");
            }
            return;
        }

        status = Status.DOWNLOADING;
        downloadProgress = 0;

        executor.execute(() -> {
            try {
                performDownload();
            } catch (Exception e) {
                status = Status.ERROR;
                errorMessage = e.getMessage() != null ? e.getMessage() : "下载失败";
                if (downloadCallback != null) {
                    downloadCallback.onComplete(false, errorMessage);
                }
            }
        });
    }

    private String getModelUrlBase() {
        return com.yoyo.jingxi.utils.SpUtils.getString("stt_model_url_base", DEFAULT_MODEL_URL_BASE);
    }

    private void performDownload() throws IOException {
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }

        String baseUrl = getModelUrlBase();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        // Download model.onnx and tokens.txt individually
        downloadFile(baseUrl + MODEL_ONNX, new File(modelDir, MODEL_ONNX), 0, 90);
        downloadFile(baseUrl + TOKENS_TXT, new File(modelDir, TOKENS_TXT), 90, 100);

        if (isModelReady()) {
            status = Status.READY;
            com.yoyo.jingxi.utils.SpUtils.putString("stt_model_path", modelDir.getAbsolutePath());
            if (downloadCallback != null) {
                downloadCallback.onComplete(true, "模型下载完成");
            }
        } else {
            status = Status.ERROR;
            errorMessage = "模型文件下载不完整";
            if (downloadCallback != null) {
                downloadCallback.onComplete(false, errorMessage);
            }
        }
    }

    private void downloadFile(String url, File destFile, int progressStart, int progressEnd) throws IOException {
        // Skip if already exists and has content
        if (destFile.exists() && destFile.length() > 0) {
            downloadProgress = progressEnd;
            if (downloadCallback != null) {
                downloadCallback.onProgress(progressEnd);
            }
            return;
        }

        Request request = new Request.Builder().url(url).build();
        Response response = httpClient.newCall(request).execute();

        if (!response.isSuccessful()) {
            throw new IOException("下载失败 HTTP " + response.code() + " for " + url);
        }

        long contentLength = response.body().contentLength();
        File tempFile = new File(destFile.getAbsolutePath() + ".tmp");

        try (InputStream in = response.body().byteStream();
             FileOutputStream out = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[8192];
            long totalRead = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalRead += read;
                if (contentLength > 0) {
                    float fileProgress = (float) totalRead / contentLength;
                    int overallProgress = progressStart + (int) (fileProgress * (progressEnd - progressStart));
                    downloadProgress = Math.min(overallProgress, progressEnd);
                    if (downloadCallback != null) {
                        downloadCallback.onProgress(downloadProgress);
                    }
                }
            }
        }

        if (destFile.exists()) {
            destFile.delete();
        }
        tempFile.renameTo(destFile);
    }

    // Install model from a ZIP file already on device (e.g., downloaded manually)
    public boolean installFromZip(File zipFile) {
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }

        try (ZipInputStream zis = new ZipInputStream(new java.io.FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = new File(entry.getName()).getName();
                if (name.equals(MODEL_ONNX) || name.equals(TOKENS_TXT)) {
                    File outFile = new File(modelDir, name);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            errorMessage = "解压失败: " + e.getMessage();
            return false;
        }

        if (isModelReady()) {
            status = Status.READY;
            com.yoyo.jingxi.utils.SpUtils.putString("stt_model_path", modelDir.getAbsolutePath());
            return true;
        }
        return false;
    }

    // Copy tokens.txt from APK assets to model directory (always available)
    public void ensureTokens() {
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }
        File tokensFile = new File(modelDir, TOKENS_TXT);
        if (tokensFile.exists()) return;

        Context context = JingxiApplication.getInstance();
        try (InputStream in = context.getAssets().open("stt_models/" + TOKENS_TXT);
             FileOutputStream out = new FileOutputStream(tokensFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            errorMessage = "拷贝tokens.txt失败: " + e.getMessage();
        }
    }

    // Import model file from file picker (content URI)
    public boolean importModelFile(Context context, Uri uri) {
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }
        ensureTokens();

        File destFile = new File(modelDir, MODEL_ONNX);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(destFile)) {
            if (in == null) {
                errorMessage = "无法打开文件";
                return false;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            errorMessage = "导入失败: " + e.getMessage();
            return false;
        }

        if (destFile.exists() && destFile.length() > 0) {
            status = Status.READY;
            com.yoyo.jingxi.utils.SpUtils.putString("stt_model_path", modelDir.getAbsolutePath());
            return true;
        }
        return false;
    }

    public void clearModel() {
        if (modelDir.exists()) {
            File[] files = modelDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
        status = Status.NOT_DOWNLOADED;
        downloadProgress = 0;
        errorMessage = null;
    }

    public long getModelSize() {
        long size = 0;
        if (modelDir.exists()) {
            File[] files = modelDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    size += f.length();
                }
            }
        }
        return size;
    }
}
