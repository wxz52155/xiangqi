package com.qilian.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 棋练 Android TV 壳：全屏 WebView 加载 APK 内置的网页包，
 * 把遥控器按键（方向键 / OK / 返回）交给网页里的焦点导航处理。
 */
public class MainActivity extends Activity {
    private static final String TAG = "QilianTV";
    private static final String START_URL = AssetServer.ORIGIN + "/";
    /** 低于这个版本的系统 WebView 很可能显示不正常，提示用户升级。 */
    private static final int MIN_WEBVIEW_MAJOR = 80;
    private static final long EXIT_INTERVAL_MS = 2000;

    private WebView webView;
    private View splash;
    private AssetServer assetServer;
    private NativeBridge bridge;
    private long lastBackAt;
    private boolean backPending;
    private boolean pageLoaded;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();

        assetServer = new AssetServer(getAssets());
        int webViewMajor = detectWebViewMajor();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#F2EBDE"));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#F2EBDE"));
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        splash = buildSplash();
        root.addView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        // 页面 viewport 固定为 1280 宽，交给 WebView 按屏幕宽度整体缩放（10 英尺界面）。
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " QilianTV/" + BuildConfig.VERSION_NAME);

        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);

        bridge = new NativeBridge(this, webView, webViewMajor);
        webView.addJavascriptInterface(bridge, "QilianNative");
        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppChromeClient());

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        }
        if (webView.getUrl() == null) {
            webView.loadUrl(START_URL);
        }
        webView.requestFocus();

        if (webViewMajor > 0 && webViewMajor < MIN_WEBVIEW_MAJOR) {
            showOutdatedWebViewNotice(webViewMajor);
        }
    }

    private View buildSplash() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#F2EBDE"));

        TextView seal = new TextView(this);
        seal.setText("棋");
        seal.setTextColor(Color.parseColor("#FFF8EC"));
        seal.setTextSize(54);
        seal.setGravity(Gravity.CENTER);
        seal.setBackgroundColor(Color.parseColor("#B33A32"));
        int size = (int) (getResources().getDisplayMetrics().density * 104);
        layout.addView(seal, new LinearLayout.LayoutParams(size, size));

        TextView title = new TextView(this);
        title.setText("棋练 · 中国象棋练习场");
        title.setTextColor(Color.parseColor("#1D2420"));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, (int) (getResources().getDisplayMetrics().density * 18), 0, 0);
        layout.addView(title);
        return layout;
    }

    private void hideSplash() {
        if (splash == null || splash.getVisibility() != View.VISIBLE) return;
        splash.animate().alpha(0f).setDuration(220).withEndAction(new Runnable() {
            @Override
            public void run() {
                splash.setVisibility(View.GONE);
            }
        }).start();
    }

    private int detectWebViewMajor() {
        try {
            String ua = WebSettings.getDefaultUserAgent(this);
            Matcher matcher = Pattern.compile("Chrome/(\\d+)").matcher(ua == null ? "" : ua);
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
        } catch (RuntimeException error) {
            Log.w(TAG, "无法读取 WebView 版本", error);
        }
        return 0;
    }

    private void showOutdatedWebViewNotice(int major) {
        new AlertDialog.Builder(this)
                .setTitle("系统 WebView 版本较旧")
                .setMessage("这台设备的系统浏览器内核是 Chrome " + major + "，棋练需要 Chrome "
                        + MIN_WEBVIEW_MAJOR + " 及以上才能完整显示。\n\n"
                        + "可以在应用商店更新「Android System WebView」或「Chrome」后再打开。现在也可以继续尝试使用。")
                .setPositiveButton("继续使用", null)
                .show();
    }

    private void enterImmersive() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    // ---------------------------------------------------------------- 按键

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();

        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) handleBack();
            return true;
        }

        // 遥控器“确定”键统一成回车，网页里按钮 / 链接 / 输入框都按回车处理。
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_BUTTON_A
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            KeyEvent enter = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
                    KeyEvent.KEYCODE_ENTER, event.getRepeatCount(), event.getMetaState(),
                    event.getDeviceId(), event.getScanCode(), event.getFlags(), event.getSource());
            return super.dispatchKeyEvent(enter);
        }

        // 菜单键：打开设置页
        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS) {
            if (event.getAction() == KeyEvent.ACTION_UP && pageLoaded) {
                webView.evaluateJavascript(
                        "(function(){try{window.dispatchEvent(new CustomEvent('qilian:tv-menu'));}catch(e){}})()", null);
            }
            return true;
        }

        if (webView != null && !webView.hasFocus() && isDpad(code)) {
            webView.requestFocus();
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean isDpad(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_ENTER;
    }

    private void handleBack() {
        if (webView == null || !pageLoaded) {
            confirmExit();
            return;
        }
        if (backPending) return;
        backPending = true;
        webView.evaluateJavascript(
                "(function(){try{return window.__qilianTvBack?window.__qilianTvBack():null;}catch(e){return null;}})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        backPending = false;
                        if ("true".equals(value)) return;
                        if ("null".equals(value) || value == null) {
                            // 页面没有接管（例如打开了许可证纯文本），按浏览器历史后退。
                            if (webView.canGoBack()) {
                                webView.goBack();
                                return;
                            }
                        }
                        confirmExit();
                    }
                });
    }

    private void confirmExit() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastBackAt < EXIT_INTERVAL_MS) {
            finish();
            return;
        }
        lastBackAt = now;
        Toast.makeText(this, "再按一次返回键退出棋练", Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
        enterImmersive();
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (bridge != null) bridge.shutdown();
        if (webView != null) {
            webView.removeJavascriptInterface("QilianNative");
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- WebView 回调

    private final class AppWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (AssetServer.handles(uri)) return assetServer.serve(uri);
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (AssetServer.handles(uri)) return false;
            // 页面里的外链已改成扫码打开；这里兜底，避免整个应用跳到外部网站。
            return true;
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return !AssetServer.handles(Uri.parse(url));
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            pageLoaded = true;
            hideSplash();
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // 渲染进程崩溃（低内存电视常见）：重建界面而不是整个应用闪退。
            Log.w(TAG, "WebView renderer gone, recreating");
            if (webView == view) {
                ViewGroup parent = (ViewGroup) view.getParent();
                if (parent != null) parent.removeView(view);
                view.destroy();
                webView = null;
            }
            recreate();
            return true;
        }
    }

    private final class AppChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage message) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, message.message() + " @" + message.sourceId() + ":" + message.lineNumber());
            }
            return true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("知道了", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            result.confirm();
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            result.cancel();
                        }
                    })
                    .show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            result.confirm();
                        }
                    })
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            result.cancel();
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            result.cancel();
                        }
                    })
                    .show();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                                  final JsPromptResult result) {
            final EditText input = new EditText(MainActivity.this);
            input.setText(defaultValue);
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            result.confirm(input.getText().toString());
                        }
                    })
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            result.cancel();
                        }
                    })
                    .show();
            return true;
        }
    }
}
