package com.yoyo.jingxi.network;

import android.util.Log;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.yoyo.jingxi.utils.SpUtils;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalSttProvider implements SttProvider {

    private static final String TAG = "LocalSttProvider";
    private static final int TARGET_SAMPLE_RATE = 16000;

    private OfflineRecognizer recognizer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean initialized = false;
    private volatile String initError;

    @Override
    public void recognize(File audioFile, Callback callback) {
        if (!isAvailable()) {
            callback.onError(2001, "本地模型未就绪: " + (initError != null ? initError : "模型未下载"), true);
            return;
        }

        executor.execute(() -> {
            try {
                float[] samples = SherpaResampler.decodeToPcm(audioFile, TARGET_SAMPLE_RATE);
                if (samples == null || samples.length == 0) {
                    callback.onError(2002, "音频解码失败", true);
                    return;
                }

                OfflineStream stream = recognizer.createStream();
                stream.acceptWaveform(samples, TARGET_SAMPLE_RATE);
                recognizer.decode(stream);
                String text = recognizer.getResult(stream).getText();

                if (text != null && !text.trim().isEmpty()) {
                    callback.onResult(text.trim());
                } else {
                    callback.onError(2002, "未识别到语音内容", true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Local STT error", e);
                callback.onError(2002, "本地识别失败: " + e.getMessage(), true);
            }
        });
    }

    @Override
    public String getProviderName() {
        return "本地识别 (SenseVoiceSmall)";
    }

    @Override
    public boolean isAvailable() {
        if (!SpUtils.getBoolean("stt_use_local", false)) {
            return false;
        }

        if (!SttModelManager.getInstance().isModelReady()) {
            initError = "模型未下载";
            return false;
        }

        if (!initialized) {
            initRecognizer();
        }

        return initialized;
    }

    private synchronized void initRecognizer() {
        if (initialized) return;

        try {
            String modelDir = SttModelManager.getInstance().getModelDirPath();

            OfflineSenseVoiceModelConfig senseVoiceConfig =
                    OfflineSenseVoiceModelConfig.builder()
                            .setModel(modelDir + "/model_q8.onnx")
                            .setLanguage("zh")
                            .build();

            OfflineModelConfig modelConfig = OfflineModelConfig.builder()
                    .setSenseVoice(senseVoiceConfig)
                    .setTokens(modelDir + "/tokens.txt")
                    .setNumThreads(2)
                    .setDebug(false)
                    .setProvider("cpu")
                    .build();

            OfflineRecognizerConfig config = OfflineRecognizerConfig.builder()
                    .setOfflineModelConfig(modelConfig)
                    .build();

            recognizer = new OfflineRecognizer(config);
            initialized = true;
            Log.i(TAG, "Local STT recognizer initialized successfully");
        } catch (Exception e) {
            initError = "模型加载失败: " + e.getMessage();
            Log.e(TAG, "Failed to init recognizer", e);
        }
    }
}
