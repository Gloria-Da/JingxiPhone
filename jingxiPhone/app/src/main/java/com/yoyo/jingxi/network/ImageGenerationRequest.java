package com.yoyo.jingxi.network;

public class ImageGenerationRequest {
    public String model;
    public String prompt;
    public int n = 1;
    public String size = "1024x1024";

    public ImageGenerationRequest(String model, String prompt) {
        this.model = model;
        this.prompt = prompt;
    }
    
    public ImageGenerationRequest(String model, String prompt, String size) {
        this.model = model;
        this.prompt = prompt;
        if (size != null && !size.isEmpty()) {
            this.size = size;
        }
    }
    
    public String response_format;
}
