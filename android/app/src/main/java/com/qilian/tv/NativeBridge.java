package com.qilian.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 暴露给网页的 window.QilianNative。
 *
 * 只做网页在 WebView 里做不到（或在电视上不方便）的几件事：
 * 原生 HTTP（绕开跨域，并支持 SSE 流式响应）、唤起软键盘、打开外部浏览器、退出应用。
 */
final class NativeBridge {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final Activity activity;
    private final WebView webView;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, HttpURLConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, Boolean> aborted = new ConcurrentHashMap<>();
    private final int webViewMajor;

    NativeBridge(Activity activity, WebView webView, int webViewMajor) {
        this.activity = activity;
        this.webView = webView;
        this.webViewMajor = webViewMajor;
    }

    void shutdown() {
        for (HttpURLConnection connection : connections.values()) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
                // 已断开
            }
        }
        connections.clear();
        executor.shutdownNow();
    }

    // ---------------------------------------------------------------- HTTP

    @JavascriptInterface
    public void httpRequest(final String id, final String method, final String url,
                            final String headersJson, final String body) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                performRequest(id, method, url, headersJson, body);
            }
        });
    }

    @JavascriptInterface
    public void httpAbort(String id) {
        aborted.put(id, Boolean.TRUE);
        HttpURLConnection connection = connections.remove(id);
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
                // 已断开
            }
        }
    }

    private void performRequest(String id, String method, String url, String headersJson, String body) {
        HttpURLConnection connection = null;
        try {
            URL target = new URL(url);
            if (!"https".equalsIgnoreCase(target.getProtocol())) {
                fail(id, "只允许 https 请求");
                return;
            }
            connection = (HttpURLConnection) target.openConnection();
            connections.put(id, connection);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            String verb = method == null || method.isEmpty() ? "GET" : method.toUpperCase();
            connection.setRequestMethod(verb);
            connection.setRequestProperty("User-Agent", "QilianTV/" + BuildConfig.VERSION_NAME);

            if (headersJson != null && !headersJson.isEmpty()) {
                JSONObject headers = new JSONObject(headersJson);
                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    connection.setRequestProperty(key, headers.optString(key));
                }
            }

            boolean hasBody = body != null && !body.isEmpty() && !"GET".equals(verb) && !"HEAD".equals(verb);
            if (hasBody) {
                byte[] bytes = body.getBytes(UTF8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(bytes);
                } finally {
                    output.close();
                }
            }

            int status = connection.getResponseCode();
            if (isAborted(id)) return;

            JSONObject responseHeaders = new JSONObject();
            Map<String, List<String>> fields = connection.getHeaderFields();
            if (fields != null) {
                for (Map.Entry<String, List<String>> entry : fields.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) continue;
                    String name = entry.getKey().toLowerCase();
                    // 交给 JS 的 Response 只需要内容类型等普通头
                    if (name.startsWith("set-cookie")) continue;
                    responseHeaders.put(name, joinValues(entry.getValue()));
                }
            }
            call("head", id, String.valueOf(status), JSONObject.quote(responseHeaders.toString()));

            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (input != null) {
                try {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (isAborted(id)) return;
                        if (read == 0) continue;
                        String chunk = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP);
                        call("chunk", id, "'" + chunk + "'");
                    }
                } finally {
                    try {
                        input.close();
                    } catch (IOException ignored) {
                        // 忽略
                    }
                }
            }
            if (!isAborted(id)) call("end", id);
        } catch (IOException | JSONException | RuntimeException error) {
            if (!isAborted(id)) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                fail(id, message);
            }
        } finally {
            connections.remove(id);
            aborted.remove(id);
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception ignored) {
                    // 已断开
                }
            }
        }
    }

    private boolean isAborted(String id) {
        return aborted.containsKey(id);
    }

    private static String joinValues(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null) continue;
            if (builder.length() > 0) builder.append(", ");
            builder.append(value);
        }
        return builder.toString();
    }

    private void fail(String id, String message) {
        call("fail", id, JSONObject.quote(message == null ? "network error" : message));
    }

    /** 回调 window.__qilianNet[fn](id, ...args)；参数已是 JS 字面量。 */
    private void call(String fn, String id, String... args) {
        final StringBuilder script = new StringBuilder();
        script.append("window.__qilianNet&&window.__qilianNet.").append(fn).append('(')
                .append(JSONObject.quote(id));
        for (String arg : args) script.append(',').append(arg);
        script.append(");");
        webView.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript(script.toString(), null);
            }
        });
    }

    // ---------------------------------------------------------------- 键盘

    @JavascriptInterface
    public void showKeyboard() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webView.requestFocus();
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(webView, InputMethodManager.SHOW_FORCED);
            }
        });
    }

    @JavascriptInterface
    public void hideKeyboard() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(webView.getWindowToken(), 0);
            }
        });
    }

    // ---------------------------------------------------------------- 其它

    @JavascriptInterface
    public boolean openExternal(String url) {
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return false;
            final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(activity.getPackageManager()) == null) return false;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        activity.startActivity(intent);
                    } catch (ActivityNotFoundException ignored) {
                        // 设备上没有浏览器
                    }
                }
            });
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    @JavascriptInterface
    public void exitApp() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.finish();
            }
        });
    }

    @JavascriptInterface
    public String appVersion() {
        return BuildConfig.VERSION_NAME;
    }

    @JavascriptInterface
    public int webViewMajor() {
        return webViewMajor;
    }
}
