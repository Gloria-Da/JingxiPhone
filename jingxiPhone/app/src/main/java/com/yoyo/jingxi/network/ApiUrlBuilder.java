package com.yoyo.jingxi.network;

/**
 * 统一的 API URL 构建工具。
 *
 * 将版本号（v1/v4 等）交由 endpoint 配置管理，不再在代码中硬编码路径前缀。
 * 这样兼容：
 *   OpenAI:      https://api.openai.com/v1/         → .../v1/chat/completions
 *   智谱 GLM:    https://open.bigmodel.cn/api/paas/v4/ → .../v4/chat/completions
 *   SiliconFlow: https://api.siliconflow.cn/v1/     → .../v1/chat/completions
 */
public class ApiUrlBuilder {

    /**
     * 构建 chat/completions 端点 URL
     */
    public static String chatCompletions(String endpoint) {
        return ensureTrailingSlash(endpoint) + "chat/completions";
    }

    /**
     * 构建 models 端点 URL（用于拉取可用模型列表）
     */
    public static String models(String endpoint) {
        return ensureTrailingSlash(endpoint) + "models";
    }

    /**
     * 构建 audio/transcriptions 端点 URL（语音识别）
     */
    public static String audioTranscriptions(String endpoint) {
        return ensureTrailingSlash(endpoint) + "audio/transcriptions";
    }

    /**
     * 构建 images/generations 端点 URL（图片生成）
     */
    public static String imageGenerations(String endpoint) {
        return ensureTrailingSlash(endpoint) + "images/generations";
    }

    private static String ensureTrailingSlash(String url) {
        if (url == null) return null;
        return url.endsWith("/") ? url : url + "/";
    }
}
