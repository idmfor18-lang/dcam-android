package ua.pp.camdm.dcam;

import android.annotation.SuppressLint;
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
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PRIMARY_URL = "https://camdm.pp.ua";
    private static final String FALLBACK_URL = "https://dcam-web.fly.dev";

    private FrameLayout rootLayout;
    private WebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean fallbackUsed = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(
                    WebView view, WebResourceRequest request, WebResourceError error
            ) {
                if (request.isForMainFrame()
                        && !fallbackUsed
                        && request.getUrl().toString().startsWith(PRIMARY_URL)) {
                    fallbackUsed = true;
                    view.loadUrl(FALLBACK_URL);
                }
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

    private class AndroidBridge {
        @JavascriptInterface
        public void enterPiP() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PictureInPictureParams params = new PictureInPictureParams.Builder()
                            .setAspectRatio(new Rational(16, 9))
                            .build();
                    enterPictureInPictureMode(params);
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
    protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (customView != null && webView.getWebChromeClient() != null) {
            webView.getWebChromeClient().onHideCustomView();
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }
}
