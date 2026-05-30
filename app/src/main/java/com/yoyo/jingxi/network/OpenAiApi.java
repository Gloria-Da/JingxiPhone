package com.yoyo.jingxi.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Url;

public interface OpenAiApi {
    @POST
    @retrofit2.http.Headers("Content-Type: application/json")
    Call<OpenAiResponse> createChatCompletion(
            @Url String url,
            @Header("Authorization") String authorization,
            @Body OpenAiRequest request
    );

    @GET
    Call<ModelListResponse> getModels(
            @Url String url,
            @Header("Authorization") String authorization
    );

    @retrofit2.http.Multipart
    @POST
    Call<SttResponse> transcribeAudio(
            @Url String url,
            @Header("Authorization") String authorization,
            @retrofit2.http.Part okhttp3.MultipartBody.Part file,
            @retrofit2.http.Part("model") okhttp3.RequestBody model
    );

    @POST
    @retrofit2.http.Headers("Content-Type: application/json")
    Call<ImageGenerationResponse> generateImage(
            @Url String url,
            @Header("Authorization") String authorization,
            @Body ImageGenerationRequest request
    );
}
