package com.yoyo.jingxi.utils;

import android.text.TextUtils;

/**
 * Memory V2 的独立 API 配置管理。
 * 管理 Curator（记忆审查）和 Subconscious（潜意识搜索）的 API 端点/密钥/模型配置。
 * 从 MemoryManager 中提取，纯静态工具类。
 */
public class MemoryApiConfig {

    private MemoryApiConfig() {}

    public static class ApiConfig {
        public String endpoint;
        public String apiKey;
        public String model;
    }

    /**
     * 获取 Curator API 配置（记忆审查、画像审查、心绪审查）。
     * 如果 useMainApi 为 true 或自定义字段为空，则回退到主 API 配置。
     */
    public static ApiConfig getCuratorApiConfig() {
        ApiConfig config = new ApiConfig();
        boolean useMainApi = SpUtils.getBoolean("MEMORY_V2_CURATOR_USE_MAIN_API", true);
        String customEndpoint = SpUtils.getString("MEMORY_V2_CURATOR_ENDPOINT", "");
        String customKey = SpUtils.getString("MEMORY_V2_CURATOR_KEY", "");
        String customModel = SpUtils.getString("MEMORY_V2_CURATOR_MODEL", "");

        if (useMainApi || TextUtils.isEmpty(customEndpoint))
            config.endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/v1/");
        else config.endpoint = customEndpoint;

        if (useMainApi || TextUtils.isEmpty(customKey))
            config.apiKey = SpUtils.getString("OPENAI_API_KEY", "");
        else config.apiKey = customKey;

        config.model = !TextUtils.isEmpty(customModel) ? customModel : SpUtils.getString("API_MODEL", "gpt-4o-mini");
        if (!config.endpoint.endsWith("/")) config.endpoint += "/";
        return config;
    }

    /**
     * 获取 Subconscious API 配置（沉浸模式下的潜意识记忆检索）。
     * 如果 useMainApi 为 true 或自定义字段为空，则回退到主 API 配置。
     */
    public static ApiConfig getSubconsciousApiConfig() {
        ApiConfig config = new ApiConfig();
        boolean useMainApi = SpUtils.getBoolean("MEMORY_V2_SUBCONSCIOUS_USE_MAIN_API", true);
        String customEndpoint = SpUtils.getString("MEMORY_V2_SUBCONSCIOUS_ENDPOINT", "");
        String customKey = SpUtils.getString("MEMORY_V2_SUBCONSCIOUS_KEY", "");
        String customModel = SpUtils.getString("MEMORY_V2_SUBCONSCIOUS_MODEL", "");

        if (useMainApi || TextUtils.isEmpty(customEndpoint)) {
            config.endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/v1/");
        } else {
            config.endpoint = customEndpoint;
        }

        if (useMainApi || TextUtils.isEmpty(customKey)) {
            config.apiKey = SpUtils.getString("OPENAI_API_KEY", "");
        } else {
            config.apiKey = customKey;
        }

        config.model = !TextUtils.isEmpty(customModel) ? customModel : "";

        if (!config.endpoint.endsWith("/")) {
            config.endpoint += "/";
        }

        return config;
    }
}
