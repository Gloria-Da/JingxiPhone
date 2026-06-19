package com.yoyo.jingxi.network;

import java.io.File;

public interface SttProvider {
    void recognize(File audioFile, Callback callback);
    String getProviderName();
    boolean isAvailable();

    interface Callback {
        void onResult(String text);
        void onError(int code, String message, boolean canFallback);
    }
}
