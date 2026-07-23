package com.yoyo.jingxi.utils;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * 使用隐藏 WebView 加载 JS 渲染页面（小红书等），提取页面标题和 OG 元数据。
 * 适用于 HttpURLConnection+Jsoup 无法处理的纯 JS 站点。
 * 必须在主线程调用。
 */
public class WebViewMetadataExtractor {

    private static final int LOAD_TIMEOUT_MS = 8000;

    /**
     * 提取回调
     */
    public interface Callback {
        void onResult(LinkMetadataExtractor.LinkMetadata meta);
    }

    /**
     * 用隐藏 WebView 加载 URL，提取渲染后的页面元数据。
     * 必须在主线程调用。
     */
    public static void extract(android.content.Context context, String url, Callback callback) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> extract(context, url, callback));
            return;
        }

        final WebView webView = new WebView(context.getApplicationContext());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUserAgentString(
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Version/4.0 Chrome/122.0.6261.119 Mobile Safari/537.36 "
                        + "MicroMessenger/8.0.48");

        final boolean[] done = {false};

        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = () -> {
            if (!done[0]) {
                done[0] = true;
                webView.stopLoading();
                webView.destroy();
                LinkMetadataExtractor.LinkMetadata fallback = new LinkMetadataExtractor.LinkMetadata();
                fallback.title = url;
                fallback.siteName = LinkMetadataExtractor.fallbackSiteName(url);
                callback.onResult(fallback);
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MS);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                if (done[0]) return;
                // 提取标题和 OG 标签
                String js = "(function(){"
                        + "var r={};"
                        + "r.title=document.title||'';"
                        + "var ogTitle=document.querySelector('meta[property=\"og:title\"]');"
                        + "if(ogTitle)r.ogTitle=ogTitle.getAttribute('content')||'';"
                        + "var ogDesc=document.querySelector('meta[property=\"og:description\"]');"
                        + "if(ogDesc)r.ogDesc=ogDesc.getAttribute('content')||'';"
                        + "var ogImage=document.querySelector('meta[property=\"og:image\"]');"
                        + "if(ogImage)r.ogImage=ogImage.getAttribute('content')||'';"
                        + "var desc=document.querySelector('meta[name=\"description\"]');"
                        + "if(desc)r.desc=desc.getAttribute('content')||'';"
                        + "return JSON.stringify(r);"
                        + "})()";

                view.evaluateJavascript(js, result -> {
                    if (done[0]) return;
                    done[0] = true;
                    timeoutHandler.removeCallbacks(timeoutRunnable);

                    LinkMetadataExtractor.LinkMetadata meta = new LinkMetadataExtractor.LinkMetadata();
                    meta.siteName = LinkMetadataExtractor.fallbackSiteName(url);

                    if (result != null && !"null".equals(result)) {
                        try {
                            String json = result;
                            if (json.startsWith("\"") && json.endsWith("\"")) {
                                json = json.substring(1, json.length() - 1)
                                        .replace("\\\"", "\"")
                                        .replace("\\\\", "\\");
                            }
                            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
                            if (obj.has("ogTitle") && !obj.get("ogTitle").getAsString().isEmpty()) {
                                meta.title = obj.get("ogTitle").getAsString();
                            } else if (obj.has("title")) {
                                meta.title = obj.get("title").getAsString();
                            }
                            if (obj.has("ogDesc") && !obj.get("ogDesc").getAsString().isEmpty()) {
                                meta.description = obj.get("ogDesc").getAsString();
                            } else if (obj.has("desc")) {
                                meta.description = obj.get("desc").getAsString();
                            }
                            if (obj.has("ogImage")) {
                                meta.imageUrl = obj.get("ogImage").getAsString();
                            }
                        } catch (Exception e) {
                            // parse failed, fallback below
                        }
                    }

                    if (meta.title == null || meta.title.isEmpty()) {
                        meta.title = view.getTitle();
                    }
                    if (meta.title == null || meta.title.isEmpty()) {
                        meta.title = url;
                    }

                    webView.destroy();
                    callback.onResult(meta);
                });
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                                         String description, String failingUrl) {
                if (done[0]) return;
                done[0] = true;
                timeoutHandler.removeCallbacks(timeoutRunnable);
                webView.destroy();
                LinkMetadataExtractor.LinkMetadata fallback = new LinkMetadataExtractor.LinkMetadata();
                fallback.title = url;
                fallback.siteName = LinkMetadataExtractor.fallbackSiteName(url);
                callback.onResult(fallback);
            }
        });

        webView.loadUrl(url);
    }
}
