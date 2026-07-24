package com.yoyo.jingxi.network;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.util.List;

public class OpenAiRequest {
    public String model;
    public List<Message> messages;

    /**
     * 温度参数，Gson 序列化时自动四舍五入到 2 位小数。
     * 智谱 GLM 等严格 API 要求 temperature 最多 2 位小数。
     */
    @JsonAdapter(TemperatureAdapter.class)
    public double temperature = 0.7;

    public static class Message {
        public String role;
        public Object content; // 可以是 String 或者是 ContentPart 的 List (用于Vision)

        public Message(String role, Object content) {
            this.role = role;
            this.content = content;
        }
    }

    public static class ContentPart {
        public String type; // "text" or "image_url"
        public String text;
        public ImageUrl image_url;

        public static class ImageUrl {
            public String url;
            public ImageUrl(String url) {
                this.url = url;
            }
        }

        public static ContentPart text(String text) {
            ContentPart part = new ContentPart();
            part.type = "text";
            part.text = text;
            return part;
        }

        public static ContentPart imageUrl(String base64Image) {
            ContentPart part = new ContentPart();
            part.type = "image_url";
            part.image_url = new ImageUrl("data:image/jpeg;base64," + base64Image);
            return part;
        }
    }

    public ResponseFormat response_format;

    public static class ResponseFormat {
        public String type = "json_object";
    }

    /**
     * Gson TypeAdapter：将 temperature 序列化时四舍五入到 2 位小数。
     * 满足智谱 GLM 等 API 的精度限制要求。
     */
    public static class TemperatureAdapter extends TypeAdapter<Double> {
        @Override
        public void write(JsonWriter out, Double value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            double rounded = Math.round(value * 100.0) / 100.0;
            out.value(rounded);
        }

        @Override
        public Double read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return 0.7;
            }
            return in.nextDouble();
        }
    }
}
