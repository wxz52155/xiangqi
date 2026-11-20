package com.qilian.tv;

import android.content.res.AssetManager;
import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 把 APK 里 assets/web/ 下的网页包当成一个 https 站点提供给 WebView。
 *
 * 用 https://appassets.androidplatform.net/ 这个保留域名（与 androidx.webkit 的 WebViewAssetLoader 相同），
 * 页面就是“安全上下文”：IndexedDB、Web Worker、WebAssembly 都能正常使用。
 * 单页应用：没有扩展名的路径一律回落到 index.html，由前端路由处理。
 */
final class AssetServer {
    static final String HOST = "appassets.androidplatform.net";
    static final String ORIGIN = "https://" + HOST;
    private static final String ROOT = "web";

    private static final Map<String, String> MIME = new HashMap<>();

    static {
        MIME.put("html", "text/html");
        MIME.put("htm", "text/html");
        MIME.put("js", "text/javascript");
        MIME.put("mjs", "text/javascript");
        MIME.put("css", "text/css");
        MIME.put("json", "application/json");
        MIME.put("webmanifest", "application/manifest+json");
        MIME.put("wasm", "application/wasm");
        MIME.put("svg", "image/svg+xml");
        MIME.put("png", "image/png");
        MIME.put("jpg", "image/jpeg");
        MIME.put("jpeg", "image/jpeg");
        MIME.put("gif", "image/gif");
        MIME.put("webp", "image/webp");
        MIME.put("ico", "image/x-icon");
        MIME.put("woff", "font/woff");
        MIME.put("woff2", "font/woff2");
        MIME.put("ttf", "font/ttf");
        MIME.put("txt", "text/plain");
        MIME.put("md", "text/plain");
        MIME.put("xml", "application/xml");
        MIME.put("mp3", "audio/mpeg");
        MIME.put("ogg", "audio/ogg");
        MIME.put("wav", "audio/wav");
    }

    private final AssetManager assets;

    AssetServer(AssetManager assets) {
        this.assets = assets;
    }

    static boolean handles(Uri uri) {
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && HOST.equalsIgnoreCase(uri.getHost());
    }

    WebResourceResponse serve(Uri uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) path = "/index.html";
        // 防止 ../ 越界
        if (path.contains("..")) return notFound();

        String relative = path.startsWith("/") ? path.substring(1) : path;
        String extension = extensionOf(relative);

        InputStream stream = open(ROOT + "/" + relative);
        if (stream == null && extension.isEmpty()) {
            // 前端路由（/play、/train/tactics/xxx ……）
            stream = open(ROOT + "/index.html");
            extension = "html";
        }
        if (stream == null) return notFound();

        String mime = MIME.get(extension);
        if (mime == null) mime = "application/octet-stream";
        String encoding = mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json")
                || mime.endsWith("xml") || mime.equals("image/svg+xml") ? "utf-8" : null;

        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "html".equals(extension) ? "no-cache" : "max-age=31536000");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("X-Content-Type-Options", "nosniff");
        return new WebResourceResponse(mime, encoding, 200, "OK", headers, stream);
    }

    private InputStream open(String assetPath) {
        try {
            return assets.open(assetPath, AssetManager.ACCESS_STREAMING);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String extensionOf(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash) return "";
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static WebResourceResponse notFound() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-cache");
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", headers,
                new java.io.ByteArrayInputStream("Not Found".getBytes()));
    }
}
