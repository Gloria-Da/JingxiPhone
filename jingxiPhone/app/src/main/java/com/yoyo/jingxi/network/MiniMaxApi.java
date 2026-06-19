package com.yoyo.jingxi.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface MiniMaxApi {
    // T2A 接口 v2 (文本转语音)
    @POST("v1/t2a_v2")
    Call<MiniMaxTtsResponse> textToAudio(
        @Header("Authorization") String authHeader,
        @Body MiniMaxTtsRequest request
    );
}
