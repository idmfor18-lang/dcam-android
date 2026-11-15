package ua.pp.camdm.dcam;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Rational;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PRIMARY_URL = "https://camdm.pp.ua";
    private static final String FALLBACK_URL = "https://dcam-web.fly.dev";

    private FrameLayout rootLayout;
    private WebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean fallbackUsed = false;
    private boolean liveActive = false;
    private boolean webViewPaused = false;
    private boolean mainFrameFailed = false;
    private LinearLayout offlineView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#06080D"));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#06080D"));
        rootLayout.addView(
                webView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );
        offlineView = buildOfflineView();
        rootLayout.addView(
                offlineView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );
        setContentView(rootLayout);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
                if ("camdm.pp.ua".equals(host) || "dcam-web.fly.dev".equals(host)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mainFrameFailed = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                if (!mainFrameFailed) {
                    hideOffline();
                }
            }

            @Override
            public void onReceivedError(
                    WebView view, WebResourceRequest request, WebResourceError error
            ) {
                if (!request.isForMainFrame()) {
                    return;
                }
                mainFrameFailed = true;
                if (!fallbackUsed && request.getUrl().toString().startsWith(PRIMARY_URL)) {
                    fallbackUsed = true;
                    view.loadUrl(FALLBACK_URL);
                    return;
                }
                showOffline();
            }

            @Override
            public void onReceivedSslError(
                    WebView view, SslErrorHandler handler, SslError error
            ) {
                handler.cancel();
                String failedUrl = error != null && error.getUrl() != null ? error.getUrl() : "";
                if (!fallbackUsed && failedUrl.startsWith(PRIMARY_URL)) {
                    fallbackUsed = true;
                    view.loadUrl(FALLBACK_URL);
                } else {
                    mainFrameFailed = true;
                    showOffline();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                rootLayout.addView(
                        customView,
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        )
                );
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) {
                    return;
                }
                rootLayout.removeView(customView);
                customView = null;
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                webView.setVisibility(View.VISIBLE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) {
                    req.addRequestHeader("Cookie", cookie);
                }
                if (userAgent != null) {
                    req.addRequestHeader("User-Agent", userAgent);
                }
                req.setMimeType(mimetype);
                req.setTitle(filename);
                req.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );
                try {
                    req.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS, filename
                    );
                } catch (SecurityException ignored) {
                    req.setDestinationInExternalFilesDir(
                            this, Environment.DIRECTORY_DOWNLOADS, filename
                    );
                }
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(req);
                    Toast.makeText(this, "Сохранение: " + filename, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                }
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidApp");
        webView.loadUrl(PRIMARY_URL);
    }

    private LinearLayout buildOfflineView() {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#06080D"));
        box.setPadding((int) (24 * d), 0, (int) (24 * d), 0);
        box.setClickable(true);

        TextView title = new TextView(this);
        title.setText("Нет связи");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Проверьте интернет и попробуйте ещё раз.");
        sub.setTextColor(Color.parseColor("#9AA4B2"));
        sub.setTextSize(15);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, (int) (8 * d), 0, (int) (20 * d));
        box.addView(sub);

        Button retry = new Button(this);
        retry.setText("Повторить");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> retryLoad());
        box.addView(retry);

        box.setVisibility(View.GONE);
        return box;
    }

    private void showOffline() {
        webView.setVisibility(View.INVISIBLE);
        offlineView.setVisibility(View.VISIBLE);
        setLiveActive(false);
    }

    private void hideOffline() {
        offlineView.setVisibility(View.GONE);
        if (customView == null) {
            webView.setVisibility(View.VISIBLE);
        }
    }

    private void retryLoad() {
        fallbackUsed = false;
        mainFrameFailed = false;
        offlineView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(PRIMARY_URL);
    }

    private void setLiveActive(boolean live) {
        liveActive = live;
        if (live) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                setPictureInPictureParams(buildPipParams());
            } catch (Exception ignored) {
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private PictureInPictureParams buildPipParams() {
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(4, 3));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setAutoEnterEnabled(liveActive);
            b.setSeamlessResizeEnabled(false);
        }
        return b.build();
    }

    private void enterPipNow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            enterPictureInPictureMode(buildPipParams());
        } catch (Exception ignored) {
        }
    }

    private boolean inPip() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
    }

    private void pauseWebView() {
        if (webView != null && !webViewPaused) {
            webView.onPause();
            webView.pauseTimers();
            webViewPaused = true;
        }
    }

    private void resumeWebView() {
        if (webView != null && webViewPaused) {
            webView.resumeTimers();
            webView.onResume();
            webViewPaused = false;
        }
    }

    private class AndroidBridge {
        @JavascriptInterface
        public void setLive(boolean live) {
            runOnUiThread(() -> setLiveActive(live));
        }

        @JavascriptInterface
        public void enterPiP() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    enterPipNow();
                } else {
                    Toast.makeText(
                            MainActivity.this,
                            "PiP требует Android 8.0 или выше",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode, Configuration newConfig
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (webView != null) {
            webView.evaluateJavascript(
                    "document.body.classList.toggle('in-pip'," + isInPictureInPictureMode + ");",
                    null
            );
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Android 12+ enters PiP automatically via setAutoEnterEnabled.
        if (liveActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPipNow();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeWebView();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
        if (!inPip()) {
            pauseWebView();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // PiP window was closed or the app went to background: stop video and timers.
        pauseWebView();
    }

    @Override
    protected void onStart() {
        super.onStart();
        resumeWebView();
    }

    private void defaultBack() {
        super.onBackPressed();
    }

    @Override
    public void onBackPressed() {
        if (customView != null && webView.getWebChromeClient() != null) {
            webView.getWebChromeClient().onHideCustomView();
            return;
        }
        if (offlineView.getVisibility() == View.VISIBLE) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript(
                "(function(){try{return !!(window.dcamAndroidBack && window.dcamAndroidBack());}"
                        + "catch(e){return false;}})()",
                handled -> {
                    if ("true".equals(handled)) {
                        return;
                    }
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        defaultBack();
                    }
                }
        );
    }
}
