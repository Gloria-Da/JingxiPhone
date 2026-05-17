package com.yoyo.jingxi.network;

public class MiniMaxTtsResponse {
    public Data data;
    public String trace_id;
    public BaseResp base_resp;

    public static class Data {
        public String audio; // hex encoded audio data
        public int status;
        public String subtitle_file;
    }

    public static class BaseResp {
        public int status_code;
        public String status_msg;
    }
}
