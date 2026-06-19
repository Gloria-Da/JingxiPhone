package com.yoyo.jingxi.network;

import android.text.TextUtils;

import com.yoyo.jingxi.utils.SpUtils;

import java.io.File;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OpenAiApiProvider implements SttProvider {

    private OpenAiApi api;

    public OpenAiApiProvider() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openai.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(OpenAiApi.class);
    }

    @Override
    public void recognize(File audioFile, Callback callback) {
        String sttBaseUrl = SpUtils.getString("stt_base_url", "https://api.siliconflow.cn/");
        String sttApiKey = SpUtils.getString("stt_api_key", "");
        String sttModel = SpUtils.getString("stt_model", "FunAudioLLM/SenseVoiceSmall");

        if (TextUtils.isEmpty(sttApiKey)) {
            callback.onError(401, "请先在API设置中配置STT API Key", false);
            return;
        }

        if (!sttBaseUrl.endsWith("/")) {
            sttBaseUrl += "/";
        }

        RequestBody requestFile = RequestBody.create(MediaType.parse("audio/mpeg"), audioFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", audioFile.getName(), requestFile);
        RequestBody modelBody = RequestBody.create(MediaType.parse("text/plain"), sttModel);

        String url = sttBaseUrl + "v1/audio/transcriptions";
        String auth = "Bearer " + sttApiKey;

        api.transcribeAudio(url, auth, body, modelBody).enqueue(new retrofit2.Callback<SttResponse>() {
            @Override
            public void onResponse(Call<SttResponse> call, Response<SttResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().text != null) {
                    callback.onResult(response.body().text);
                } else {
                    boolean canFallback = response.code() >= 500 || response.code() == 429;
                    callback.onError(response.code(), "识别失败: " + response.code(), canFallback);
                }
            }

            @Override
            public void onFailure(Call<SttResponse> call, Throwable t) {
                callback.onError(1001, "语音识别网络错误", true);
            }
        });
    }

    @Override
    public String getProviderName() {
        String model = SpUtils.getString("stt_model", "FunAudioLLM/SenseVoiceSmall");
        return "API (" + model + ")";
    }

    @Override
    public boolean isAvailable() {
        String apiKey = SpUtils.getString("stt_api_key", "");
        return !TextUtils.isEmpty(apiKey);
    }
}
