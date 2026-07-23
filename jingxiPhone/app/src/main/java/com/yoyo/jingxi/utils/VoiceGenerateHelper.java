package com.yoyo.jingxi.utils;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.network.MiniMaxTtsRequest;
import com.yoyo.jingxi.network.MiniMaxTtsResponse;
import com.yoyo.jingxi.network.OpenAIManager;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;

import retrofit2.Response;

/**
 * MiniMax TTS 语音生成工具类，封装语音合成的完整流程。
 * 供 CallActivity（通话中实时生成）、CallMessageAdapter / ChatAdapter（点击补生成）复用。
 */
public class VoiceGenerateHelper {

    private final Context context;
    private final AppDatabase db;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final OpenAIManager aiManager;

    public VoiceGenerateHelper(Context context, AppDatabase db, ExecutorService executor, Handler mainHandler) {
        this.context = context.getApplicationContext();
        this.db = db;
        this.executor = executor;
        this.mainHandler = mainHandler;
        this.aiManager = new OpenAIManager();
    }

    /**
     * 同步生成语音文件（供 CallActivity 的队列模式使用）。
     *
     * @param character 角色实体（含 voiceId / voicePitch 等配置）
     * @param text      要合成的文本
     * @param emotion   情绪标签（可为 null）
     * @return 生成的音频文件绝对路径，失败返回 null
     */
    private static final String TAG = "VoiceGenerateHelper";

    public String generateVoiceSync(Character character, String text, String emotion) {
        if (character == null || TextUtils.isEmpty(character.voiceId)) {
            Log.w(TAG, "generateVoiceSync: character is null or voiceId is empty");
            return null;
        }

        String apiKey = SpUtils.getString("minimax_api_key", "");
        if (TextUtils.isEmpty(apiKey)) {
            Log.w(TAG, "generateVoiceSync: MiniMax API key is empty");
            return null;
        }

        String model = SpUtils.getString("minimax_model", "speech-01-turbo");
        Log.d(TAG, "generateVoiceSync: model=" + model + ", voiceId=" + character.voiceId + ", textLen=" + (text != null ? text.length() : 0));

        try {
            MiniMaxTtsRequest request = new MiniMaxTtsRequest(
                    model, text, character.voiceId,
                    character.voicePitch, character.voiceIntensity, character.voiceTimbre,
                    character.soundEffect,
                    character.voiceSpeed > 0 ? character.voiceSpeed : SpUtils.getFloat("voice_speed", 1.0f),
                    emotion
            );

            Response<MiniMaxTtsResponse> ttsResponse = aiManager.getMiniMaxApi()
                    .textToAudio("Bearer " + apiKey, request).execute();

            Log.d(TAG, "generateVoiceSync: response isSuccessful=" + ttsResponse.isSuccessful()
                    + ", body=" + (ttsResponse.body() != null)
                    + ", data=" + (ttsResponse.body() != null && ttsResponse.body().data != null)
                    + ", code=" + ttsResponse.code());

            if (ttsResponse.isSuccessful() && ttsResponse.body() != null
                    && ttsResponse.body().data != null
                    && !TextUtils.isEmpty(ttsResponse.body().data.audio)) {

                File cacheDir = context.getExternalCacheDir();
                if (cacheDir == null) {
                    Log.e(TAG, "generateVoiceSync: externalCacheDir is null");
                    return null;
                }
                File audioFile = new File(cacheDir,
                        "call_voice_" + System.currentTimeMillis() + ".mp3");
                String hexAudio = ttsResponse.body().data.audio;
                byte[] audioBytes = new byte[hexAudio.length() / 2];
                for (int j = 0; j < audioBytes.length; j++) {
                    int index = j * 2;
                    int v = Integer.parseInt(hexAudio.substring(index, index + 2), 16);
                    audioBytes[j] = (byte) v;
                }
                try (FileOutputStream fos = new FileOutputStream(audioFile)) {
                    fos.write(audioBytes);
                    Log.d(TAG, "generateVoiceSync: audio file written to " + audioFile.getAbsolutePath());
                    return audioFile.getAbsolutePath();
                }
            } else {
                if (!ttsResponse.isSuccessful()) {
                    Log.w(TAG, "generateVoiceSync: API error code=" + ttsResponse.code() + " msg=" + ttsResponse.message());
                    try {
                        if (ttsResponse.errorBody() != null) {
                            Log.w(TAG, "generateVoiceSync: errorBody=" + ttsResponse.errorBody().string());
                        }
                    } catch (Exception ignored) {}
                } else {
                    Log.w(TAG, "generateVoiceSync: response success but audio data is null or empty");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "generateVoiceSync: exception", e);
        }

        return null;
    }

    /**
     * 异步生成语音文件（供 Adapter 点击补生成使用）。
     *
     * @param characterId 角色 ID
     * @param text        要合成的文本
     * @param emotion     情绪标签（可为 null）
     * @param callback    结果回调（主线程）
     */
    public void generateVoiceAsync(int characterId, String text, String emotion,
                                   GenerateCallback callback) {
        executor.execute(() -> {
            try {
                Character character = db.characterDao().getCharacterByIdSync(characterId);
                if (character == null) {
                    postError(callback, "角色信息不存在");
                    return;
                }
                if (TextUtils.isEmpty(character.voiceId)) {
                    postError(callback, "该角色未配置语音");
                    return;
                }

                String apiKey = SpUtils.getString("minimax_api_key", "");
                if (TextUtils.isEmpty(apiKey)) {
                    postError(callback, "请先配置MiniMax API密钥");
                    return;
                }

                String result = generateVoiceSync(character, text, emotion);
                if (result != null) {
                    postSuccess(callback, result);
                } else {
                    postError(callback, "语音生成失败，请检查网络或API配置");
                }
            } catch (Exception e) {
                e.printStackTrace();
                postError(callback, "语音生成失败: " + e.getMessage());
            }
        });
    }

    private void postSuccess(GenerateCallback callback, String audioFilePath) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(audioFilePath));
        }
    }

    private void postError(GenerateCallback callback, String reason) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(reason));
        }
    }

    public interface GenerateCallback {
        void onSuccess(String audioFilePath);
        void onError(String reason);
    }
}
