package com.yoyo.jingxi.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class OpenAiRequest {
    public String model;
    public List<Message> messages;
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
}
