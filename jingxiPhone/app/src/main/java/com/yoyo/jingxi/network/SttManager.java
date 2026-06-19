package com.yoyo.jingxi.network;

import android.os.Handler;
import android.os.Looper;

import com.yoyo.jingxi.utils.SpUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SttManager {

    private static volatile SttManager instance;
    private final List<SttProvider> providers = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SttManager() {
        rebuildProviderChain();
    }

    public static SttManager getInstance() {
        if (instance == null) {
            synchronized (SttManager.class) {
                if (instance == null) {
                    instance = new SttManager();
                }
            }
        }
        return instance;
    }

    public void rebuildProviderChain() {
        providers.clear();

        boolean useLocal = SpUtils.getBoolean("stt_use_local", false);
        if (useLocal) {
            providers.add(new LocalSttProvider());
        }

        providers.add(new OpenAiApiProvider());
    }

    public void recognize(File audioFile, SttProvider.Callback callback) {
        executor.execute(() -> recognizeWithFallback(audioFile, 0, callback));
    }

    private void recognizeWithFallback(File audioFile, int index, SttProvider.Callback callback) {
        if (index >= providers.size()) {
            mainHandler.post(() -> callback.onError(-1, "所有识别方式均失败", true));
            return;
        }

        SttProvider provider = providers.get(index);
        if (!provider.isAvailable()) {
            recognizeWithFallback(audioFile, index + 1, callback);
            return;
        }

        provider.recognize(audioFile, new SttProvider.Callback() {
            @Override
            public void onResult(String text) {
                mainHandler.post(() -> callback.onResult(text));
            }

            @Override
            public void onError(int code, String message, boolean canFallback) {
                if (canFallback && index + 1 < providers.size()) {
                    recognizeWithFallback(audioFile, index + 1, callback);
                } else {
                    mainHandler.post(() -> callback.onError(code, message, canFallback && index + 1 >= providers.size()));
                }
            }
        });
    }
}
