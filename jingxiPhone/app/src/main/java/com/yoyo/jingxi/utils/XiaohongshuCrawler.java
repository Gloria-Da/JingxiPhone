package com.yoyo.jingxi.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 小红书链接爬虫（纯 HTTP + JSON 解析，无需 WebView）。
 *
 * 流程：
 * 1. OkHttp 追踪短链重定向 → 从微信 OAuth URL 提取 xiaohongshu.com 真实地址
 * 2. iPhone Safari UA 请求真实地址
 * 3. 解析 HTML 中的 __SETUP_SERVER_STATE__ JSON → 提取 noteData
 *
 * 后台线程调用，无需主线程。
 */
public class XiaohongshuCrawler {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private static final Gson gson = new Gson();

    private static final String IPHONE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
                    + "AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "Version/17.0 Mobile/15E148 Safari/604.1";

    public interface Callback {
        void onResult(LinkMetadataExtractor.LinkMetadata meta);
    }

    /**
     * 解析小红书链接（后台线程安全）。
     */
    public static void crawl(Callback callback, String shortUrl) {
        new Thread(() -> {
            LinkMetadataExtractor.LinkMetadata meta = doCrawl(shortUrl);
            callback.onResult(meta);
        }).start();
    }

    /** @deprecated 保留旧签名兼容 */
    public static void crawl(android.content.Context ctx, String url, Callback cb) {
        crawl(cb, url);
    }

    /** 同步爬取（公开给 AiReplyHelper 兜底调用） */
    public static LinkMetadataExtractor.LinkMetadata doCrawl(String url) {
        LinkMetadataExtractor.LinkMetadata meta = new LinkMetadataExtractor.LinkMetadata();
        meta.siteName = "小红书";

        try {
            // Step 1: 如果是短链，先追重定向拿到真实 URL
            String realUrl = url;
            if (url.contains("xhslink.com") || url.contains("xhslink.cn")) {
                realUrl = resolveRealUrl(url);
                if (realUrl == null) {
                    meta.title = url;
                    return meta;
                }
            }
            meta.resolvedUrl = realUrl;

            // Step 2: GET 页面 HTML
            Request req = new Request.Builder()
                    .url(realUrl)
                    .header("User-Agent", IPHONE_UA)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .build();

            Response resp = client.newCall(req).execute();
            String html = resp.body() != null ? resp.body().string() : "";
            resp.close();

            if (html.isEmpty()) {
                meta.title = url;
                return meta;
            }

            // Step 3: 解析 __SETUP_SERVER_STATE__ → noteData + commentData
            JsonObject[] noteAndComments = extractNoteData(html);
            JsonObject noteData = noteAndComments[0];
            JsonObject commentData = noteAndComments[1];
            if (noteData == null) {
                noteData = extractNormalPreload(html);
            }

            if (noteData != null) {
                meta.title = optString(noteData, "title");
                meta.description = optString(noteData, "desc");
                meta.fullText = meta.description; // 小红书正文即 desc

                // 图片列表
                JsonArray imageList = noteData.getAsJsonArray("imageList");
                if (imageList != null && imageList.size() > 0) {
                    meta.imageUrls = new ArrayList<>();
                    for (JsonElement el : imageList) {
                        JsonObject img = el.getAsJsonObject();
                        String imgUrl = fixUrl(optString(img, "url"));
                        if (imgUrl != null && !imgUrl.isEmpty()) {
                            meta.imageUrls.add(imgUrl);
                        }
                    }
                    // 第一张作为封面
                    if (!meta.imageUrls.isEmpty()) {
                        meta.imageUrl = meta.imageUrls.get(0);
                    }
                } else {
                    // fallback: normalNotePreloadData 的 imagesList
                    JsonArray imagesList = noteData.getAsJsonArray("imagesList");
                    if (imagesList != null && imagesList.size() > 0) {
                        meta.imageUrls = new ArrayList<>();
                        for (JsonElement el : imagesList) {
                            JsonObject img = el.getAsJsonObject();
                            String imgUrl = fixUrl(optString(img, "url"));
                            if (imgUrl != null && !imgUrl.isEmpty()) {
                                meta.imageUrls.add(imgUrl);
                            }
                        }
                        if (!meta.imageUrls.isEmpty()) {
                            meta.imageUrl = meta.imageUrls.get(0);
                        }
                    }
                }

                // 用户信息
                JsonObject user = noteData.getAsJsonObject("user");
                if (user != null) {
                    String nick = optString(user, "nickName");
                    if (nick == null) nick = optString(user, "nickname");
                    if (nick != null && meta.description == null) {
                        meta.description = "作者: " + nick;
                    }
                }

                // 互动数据
                JsonObject interact = noteData.getAsJsonObject("interactInfo");
                if (interact != null) {
                    StringBuilder extra = new StringBuilder();
                    String likes = optString(interact, "likedCount");
                    String collects = optString(interact, "collectedCount");
                    String comments = optString(interact, "commentCount");
                    if (likes != null) extra.append(" ").append(likes).append("赞");
                    if (collects != null) extra.append(" ").append(collects).append("收藏");
                    if (comments != null) extra.append(" ").append(comments).append("评论");
                    if (extra.length() > 0 && meta.description != null) {
                        meta.description += "\n" + extra.toString().trim();
                    }
                }

                // 标签
                JsonArray tags = noteData.getAsJsonArray("tagList");
                if (tags != null && tags.size() > 0) {
                    StringBuilder tagStr = new StringBuilder();
                    for (JsonElement el : tags) {
                        String name = optString(el.getAsJsonObject(), "name");
                        if (name != null) tagStr.append("#").append(name).append(" ");
                    }
                    if (tagStr.length() > 0 && meta.fullText != null) {
                        meta.fullText += "\n" + tagStr.toString().trim();
                    }
                }

                // 热门评论（前5条 + 子评论）
                if (commentData != null) {
                    JsonArray comments = commentData.getAsJsonArray("comments");
                    if (comments != null && comments.size() > 0) {
                        StringBuilder commentStr = new StringBuilder("\n---\n热门评论:");
                        int maxComments = Math.min(comments.size(), 5);
                        for (int i = 0; i < maxComments; i++) {
                            JsonObject c = comments.get(i).getAsJsonObject();
                            String cContent = optString(c, "content");
                            String cUser = "用户";
                            JsonObject cUserObj = c.getAsJsonObject("user");
                            if (cUserObj != null) {
                                String nick = optString(cUserObj, "nickname");
                                if (nick != null) cUser = nick;
                            }
                            String cLikes = optString(c, "likeCount");
                            if (cLikes == null) cLikes = "0";
                            if (cContent != null) {
                                commentStr.append("\n[").append(cLikes).append("赞] ")
                                        .append(cUser).append(": ").append(cContent);
                            }
                            // 子评论（前2条）
                            JsonArray subComments = c.getAsJsonArray("subComments");
                            if (subComments != null) {
                                int maxSub = Math.min(subComments.size(), 2);
                                for (int j = 0; j < maxSub; j++) {
                                    JsonObject sc = subComments.get(j).getAsJsonObject();
                                    String scContent = optString(sc, "content");
                                    String scUser = "用户";
                                    JsonObject scUserObj = sc.getAsJsonObject("user");
                                    if (scUserObj != null) {
                                        String nick = optString(scUserObj, "nickname");
                                        if (nick != null) scUser = nick;
                                    }
                                    String scLikes = optString(sc, "likeCount");
                                    if (scContent != null) {
                                        commentStr.append("\n  [" + (scLikes != null ? scLikes : "0")
                                                + "赞] ").append(scUser).append("回复: ")
                                                .append(scContent);
                                    }
                                }
                            }
                        }
                        if (meta.fullText != null) {
                            meta.fullText += commentStr.toString();
                        } else {
                            meta.fullText = commentStr.toString().trim();
                        }
                    }
                }
            }

            // 清理 title 中的 " - 小红书" 后缀
            if (meta.title != null) {
                meta.title = meta.title.replaceAll("\\s*[-–—|]\\s*小红书\\s*$", "").trim();
            }
            if (meta.title == null || meta.title.isEmpty()) {
                meta.title = url;
            }

        } catch (Exception e) {
            meta.title = url;
        }

        return meta;
    }

    /** OkHttp 追重定向，从 OAuth 中提取真实 URL */
    private static String resolveRealUrl(String url) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", IPHONE_UA)
                .build();
        Response resp = client.newCall(req).execute();
        String finalUrl = resp.request().url().toString();
        String body = resp.body() != null ? resp.body().string() : "";
        resp.close();

        // 从 OAuth URL 提取 redirect_uri
        if (finalUrl.contains("open.weixin.qq.com") && finalUrl.contains("redirect_uri=")) {
            Matcher m = Pattern.compile("redirect_uri=([^&\"]+)").matcher(finalUrl);
            if (m.find()) {
                return URLDecoder.decode(m.group(1), "UTF-8");
            }
        }
        // 从 body 中提取
        Matcher m = Pattern.compile("redirect_uri=([^&\"'\\s]+)").matcher(body);
        if (m.find()) {
            return URLDecoder.decode(m.group(1), "UTF-8");
        }
        return finalUrl;
    }

    /** 从 HTML 提取 __SETUP_SERVER_STATE__ 中的 noteData */
    /** 从 HTML 提取 __SETUP_SERVER_STATE__ → [noteData, commentData] */
    private static JsonObject[] extractNoteData(String html) {
        try {
            Matcher m = Pattern.compile(
                    "window\\.__SETUP_SERVER_STATE__\\s*=\\s*").matcher(html);
            if (!m.find()) return new JsonObject[]{null, null};

            int jsonStart = m.end();
            int depth = 0;
            int jsonEnd = jsonStart;
            for (int i = jsonStart; i < html.length(); i++) {
                char c = html.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { jsonEnd = i + 1; break; }
                }
            }

            String setupJson = html.substring(jsonStart, jsonEnd);
            setupJson = setupJson.replace("\\u002F", "/").replace("\\/", "/");
            JsonObject root = new JsonParser().parse(setupJson).getAsJsonObject();

            JsonObject launcher = root.getAsJsonObject("LAUNCHER_SSR_STORE_PAGE_DATA");
            if (launcher != null) {
                JsonObject nd = launcher.getAsJsonObject("noteData");
                JsonObject cd = launcher.getAsJsonObject("commentData");
                return new JsonObject[]{nd, cd};
            }
            return new JsonObject[]{null, null};
        } catch (Exception e) {
            return new JsonObject[]{null, null};
        }
    }

    /** 从 HTML 提取 __INITIAL_STATE__.normalNotePreloadData */
    private static JsonObject extractNormalPreload(String html) {
        try {
            Matcher m = Pattern.compile(
                    "\"normalNotePreloadData\"\\s*:\\s*\\{").matcher(html);
            if (!m.find()) return null;

            int start = m.start() + "\"normalNotePreloadData\":".length();
            while (start < html.length() && html.charAt(start) != '{') start++;

            int depth = 0;
            int end = start;
            for (int i = start; i < html.length(); i++) {
                char c = html.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { end = i + 1; break; }
                }
            }

            String json = html.substring(start, end);
            json = json.replace("\\u002F", "/").replace("\\/", "/");
            return new JsonParser().parse(json).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /** 修复图片 URL：补 https: 前缀，替换转义斜杠 */
    private static String fixUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        if (url.startsWith("//")) url = "https:" + url;
        if (url.startsWith("http:") && url.contains("xhscdn.com")) {
            // 小红书 CDN 支持 https
        }
        url = url.replace("\\u002F", "/").replace("\\/", "/");
        return url;
    }

    private static String optString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        String s = el.getAsString();
        return (s != null && !s.isEmpty()) ? s : null;
    }
}
