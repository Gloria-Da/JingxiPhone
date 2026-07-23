package com.yoyo.jingxi.utils;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从URL提取Open Graph / Twitter Card / 标准meta标签元数据。
 * 用于外部分享链接的富媒体卡片展示和AI上下文注入。
 * 所有方法均为同步方法，请在后台线程调用。
 */
public class LinkMetadataExtractor {
    private static final String TAG = "LinkMetadataExtractor";
    private static final int CONNECT_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 8000;

    // 多种 User-Agent，按优先级尝试
    private static final String[] USER_AGENTS = {
        // 微信内置浏览器（小红书/B站对其友好）
        "Mozilla/5.0 (Linux; Android 14; 23013RK75C) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Version/4.0 Chrome/122.0.6261.119 Mobile Safari/537.36 MicroMessenger/8.0.48",
        // 标准移动端 Chrome
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/122.0.6261.119 Mobile Safari/537.36",
    };

    static {
        // 全局 Cookie 管理，保持跨重定向的 cookie
        try {
            CookieManager cm = new CookieManager();
            cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            CookieHandler.setDefault(cm);
        } catch (Exception e) {
            // 某些 Android 版本可能不支持
        }
    }

    /** 提取的链接元数据 */
    public static class LinkMetadata {
        public String title;
        public String description;
        public String imageUrl;
        public String siteName;
        public String faviconUrl;
        /** 经过重定向解析后的最终 URL（如从 OAuth 中提取的真实地址） */
        public String resolvedUrl;
        /** 完整正文（小红书等图文内容） */
        public String fullText;
        /** 全部图片 URL 列表 */
        public java.util.List<String> imageUrls;
    }

    /**
     * 从URL提取元数据。同步方法，请在后台线程调用。
     */
    public static LinkMetadata extract(String url) {
        LinkMetadata meta = new LinkMetadata();
        try {
            URL urlObj = new URL(url);
            meta.faviconUrl = urlObj.getProtocol() + "://" + urlObj.getHost() + "/favicon.ico";
            meta.siteName = detectSiteName(urlObj.getHost());
        } catch (Exception e) {
            meta.title = url;
            meta.siteName = "网页";
            return meta;
        }

        // 尝试多种 UA
        for (String ua : USER_AGENTS) {
            try {
                if (fetchAndParse(url, ua, meta)) {
                    break; // 成功提取到有意义的内容
                }
            } catch (Exception e) {
                Log.w(TAG, "Attempt with UA failed: " + e.getMessage());
            }
        }

        // 降级
        if (meta.title == null || meta.title.isEmpty()) {
            meta.title = url;
        }
        if (meta.siteName == null || meta.siteName.isEmpty()) {
            meta.siteName = "网页";
        }
        return meta;
    }

    /**
     * 发起 HTTP 请求并解析 HTML。支持重定向追踪（HTTP 302 + meta refresh）。
     * @return true 表示提取到有效内容
     */
    private static boolean fetchAndParse(String url, String userAgent, LinkMetadata meta)
            throws Exception {
        String currentUrl = url;
        int maxHops = 5;

        for (int hop = 0; hop < maxHops; hop++) {
            URL urlObj = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestProperty("User-Agent", userAgent);
            conn.setRequestProperty("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(true); // 自动跟随 HTTP 302

            int status = conn.getResponseCode();

            // ★ 关键：更新 currentUrl 为实际请求的最终 URL（跟随重定向后的地址）
            currentUrl = conn.getURL().toString();

            if (status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM) {

                // 如果还是重定向（可能 setInstanceFollowRedirects 没生效），手动跟
                String location = conn.getHeaderField("Location");
                if (location != null && !location.isEmpty()) {
                    currentUrl = resolveUrl(currentUrl, location);
                    conn.disconnect();
                    continue;
                }

                // Jsoup 解析（自动处理 charset）
                Document doc = Jsoup.parse(conn.getInputStream(),
                        conn.getContentEncoding(), currentUrl);

                // 检测 WeChat OAuth 跳转页，提取 redirect_uri 参数
                String oauthRedirect = extractWechatOauthRedirect(doc, currentUrl);
                if (oauthRedirect != null) {
                    meta.resolvedUrl = oauthRedirect;
                    currentUrl = oauthRedirect;
                    conn.disconnect();
                    continue; // 用真实 URL 重新请求
                }

                // 检查是否被反爬（空白页、验证页）
                if (isBlockedPage(doc)) {
                    conn.disconnect();
                    return false; // 尝试下一个 UA
                }

                // 检查 meta refresh 重定向
                String refreshUrl = extractMetaRefresh(doc);
                if (refreshUrl != null && !refreshUrl.isEmpty()) {
                    currentUrl = resolveUrl(currentUrl, refreshUrl);
                    conn.disconnect();
                    continue;
                }

                // 提取元数据
                extractFromDoc(doc, urlObj, currentUrl, meta);
                conn.disconnect();
                return !isNullOrEmpty(meta.title) || !isNullOrEmpty(meta.description);
            }

            conn.disconnect();
            return false;
        }

        return false;
    }

    /** 从 Document 提取各类元数据 */
    private static void extractFromDoc(Document doc, URL urlObj, String finalUrl,
                                        LinkMetadata meta) {
        // OG tags
        meta.title = getMetaContent(doc, "og:title");
        meta.description = getMetaContent(doc, "og:description");
        meta.imageUrl = getMetaContent(doc, "og:image");

        // Twitter Card
        if (isNullOrEmpty(meta.title)) meta.title = getMetaContent(doc, "twitter:title");
        if (isNullOrEmpty(meta.description)) meta.description = getMetaContent(doc, "twitter:description");
        if (isNullOrEmpty(meta.imageUrl)) meta.imageUrl = getMetaContent(doc, "twitter:image");

        // 标准 meta
        if (isNullOrEmpty(meta.description)) meta.description = getMetaContent(doc, "description");

        // JSON-LD 结构化数据
        if (isNullOrEmpty(meta.title) || isNullOrEmpty(meta.description)) {
            extractJsonLd(doc, meta);
        }

        // 页面 title 作为最后兜底
        if (isNullOrEmpty(meta.title)) {
            String pageTitle = doc.title();
            if (pageTitle != null && !pageTitle.isEmpty()) {
                meta.title = pageTitle;
            }
        }

        // OG site_name
        String ogSiteName = getMetaContent(doc, "og:site_name");
        if (!isNullOrEmpty(ogSiteName)) {
            meta.siteName = ogSiteName;
        }

        // 根据最终 URL 更新站点名
        try {
            URL finalUrlObj = new URL(finalUrl);
            String finalHost = finalUrlObj.getHost();
            String detected = detectSiteName(finalHost);
            if (ogSiteName == null || ogSiteName.isEmpty()) {
                meta.siteName = detected;
            }
            // 更新 favicon
            meta.faviconUrl = finalUrlObj.getProtocol() + "://" + finalHost + "/favicon.ico";
        } catch (Exception ignored) {}

        // 从 link 标签提取 favicon
        extractFavicon(doc, urlObj, meta);
    }

    /** 解析 JSON-LD 结构化数据 */
    private static void extractJsonLd(Document doc, LinkMetadata meta) {
        try {
            Elements scripts = doc.select("script[type='application/ld+json']");
            for (Element script : scripts) {
                String json = script.html();
                if (json == null || json.isEmpty()) continue;
                // 简单正则提取（避免引入 JSON 解析库）
                if (isNullOrEmpty(meta.title)) {
                    meta.title = extractJsonValue(json, "name");
                    if (isNullOrEmpty(meta.title)) meta.title = extractJsonValue(json, "headline");
                }
                if (isNullOrEmpty(meta.description)) {
                    meta.description = extractJsonValue(json, "description");
                }
                if (isNullOrEmpty(meta.imageUrl)) {
                    meta.imageUrl = extractJsonValue(json, "image");
                    if (isNullOrEmpty(meta.imageUrl)) meta.imageUrl = extractJsonValue(json, "thumbnailUrl");
                }
            }
        } catch (Exception ignored) {}
    }

    /** 从 JSON 字符串中简单提取 key 对应的 value */
    private static String extractJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            String val = m.group(1);
            return val.replace("\\\"", "\"").replace("\\n", "\n");
        }
        return null;
    }

    /** 检测反爬/空白页 */
    private static boolean isBlockedPage(Document doc) {
        if (doc.body() == null) return true;
        String text = doc.body().text().trim();
        // 页面几乎没有文字内容
        if (text.length() < 10) return true;
        // 常见反爬关键词
        String lower = text.toLowerCase();
        if (lower.contains("captcha") || lower.contains("verify") || lower.contains("滑块")) {
            return true;
        }
        return false;
    }

    /** 提取 HTML meta refresh 重定向 URL */
    private static String extractMetaRefresh(Document doc) {
        try {
            Element meta = doc.selectFirst("meta[http-equiv='refresh']");
            if (meta != null) {
                String content = meta.attr("content");
                if (content != null) {
                    // 格式: "0;url=http://example.com" 或 "0; url=http://example.com"
                    Pattern p = Pattern.compile("url\\s*=\\s*['\"]?([^'\";\\s]+)['\"]?", Pattern.CASE_INSENSITIVE);
                    Matcher m = p.matcher(content);
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** 解析相对 URL */
    private static String resolveUrl(String base, String path) {
        try {
            if (path.startsWith("http://") || path.startsWith("https://")) return path;
            URL baseUrl = new URL(base);
            if (path.startsWith("//")) return baseUrl.getProtocol() + ":" + path;
            if (path.startsWith("/")) return baseUrl.getProtocol() + "://" + baseUrl.getHost() + path;
            return new URL(baseUrl, path).toString();
        } catch (Exception e) {
            return path;
        }
    }

    /** 公开方法：根据 URL 推测站点名（供 WebViewMetadataExtractor 降级时使用） */
    public static String fallbackSiteName(String url) {
        try {
            return detectSiteName(new java.net.URL(url).getHost());
        } catch (Exception e) {
            return "网页";
        }
    }

    private static String detectSiteName(String host) {
        if (host == null) return "网页";
        String lower = host.toLowerCase();
        if (lower.contains("bilibili.com") || lower.contains("b23.tv")) return "Bilibili";
        if (lower.contains("xiaohongshu.com") || lower.contains("xhslink.com") || lower.contains("xhslink.cn")) return "小红书";
        if (lower.contains("douyin.com")) return "抖音";
        if (lower.contains("tiktok.com")) return "TikTok";
        if (lower.contains("weibo.com")) return "微博";
        if (lower.contains("zhihu.com")) return "知乎";
        if (lower.contains("baidu.com")) return "百度";
        if (lower.contains("qq.com")) return "腾讯";
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) return "YouTube";
        if (lower.contains("twitter.com") || lower.contains("x.com")) return "Twitter/X";
        if (lower.contains("taobao.com")) return "淘宝";
        if (lower.contains("jd.com")) return "京东";
        if (host.startsWith("www.")) return host.substring(4);
        return host;
    }

    private static void extractFavicon(Document doc, URL urlObj, LinkMetadata meta) {
        try {
            Element faviconLink = doc.selectFirst(
                    "link[rel='icon'], link[rel='shortcut icon'], link[rel='apple-touch-icon']");
            if (faviconLink != null) {
                String href = faviconLink.attr("href");
                if (href != null && !href.isEmpty()) {
                    meta.faviconUrl = resolveUrl(urlObj.toString(), href);
                }
            }
        } catch (Exception ignored) {}
    }

    private static String getMetaContent(Document doc, String attrValue) {
        try {
            Element el = doc.selectFirst("meta[property='" + attrValue + "']");
            if (el != null) {
                String content = el.attr("content");
                if (!isNullOrEmpty(content)) return content;
            }
            el = doc.selectFirst("meta[name='" + attrValue + "']");
            if (el != null) {
                String content = el.attr("content");
                if (!isNullOrEmpty(content)) return content;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** 从 WeChat OAuth 页面 URL 中提取 redirect_uri 参数的目标地址 */
    private static String extractWechatOauthRedirect(Document doc, String currentUrl) {
        try {
            if (currentUrl != null && currentUrl.contains("open.weixin.qq.com")
                    && currentUrl.contains("redirect_uri=")) {
                Pattern p = Pattern.compile("redirect_uri=([^&]+)");
                Matcher m = p.matcher(currentUrl);
                if (m.find()) {
                    return java.net.URLDecoder.decode(m.group(1), "UTF-8");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
