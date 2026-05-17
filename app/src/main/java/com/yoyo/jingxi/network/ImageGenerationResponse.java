package com.yoyo.jingxi.network;

import java.util.List;

public class ImageGenerationResponse {
    public long created;
    public List<ImageData> data;

    public static class ImageData {
        public String url;
        public String b64_json;
        public String revised_prompt;
    }
}
