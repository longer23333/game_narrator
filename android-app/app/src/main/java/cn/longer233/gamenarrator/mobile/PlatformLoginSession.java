package cn.longer233.gamenarrator.mobile;

import android.app.Dialog;
import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * In-memory Bilibili login session. Cookies live only in the WebView cookie
 * manager for the dialog lifetime and are removed on close; nothing is written
 * to the app database, logs or archives.
 */
public final class PlatformLoginSession {
    public static final String LOGIN_URL = "https://passport.bilibili.com/login";
    private WebView webView;
    private Dialog dialog;
    private boolean sessionSeen;
    private Runnable onSessionReady;
    private String nativeCookies;

    public boolean hasSession() {
        return sessionSeen;
    }

    public WebView createWebView(Context context) {
        clearSession();
        WebView view = new WebView(context);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false);
        view.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                String cookie = CookieManager.getInstance().getCookie("https://www.bilibili.com");
                if (!sessionSeen && cookie != null && cookie.contains("SESSDATA")) {
                    sessionSeen = true;
                    if (onSessionReady != null) {
                        Runnable ready = onSessionReady;
                        onSessionReady = null;
                        ready.run();
                    }
                }
            }
        });
        webView = view;
        return view;
    }

    public void showLogin(Context context, Runnable onReady, Runnable onClose) {
        WebView view = createWebView(context);
        onSessionReady = onReady;
        dialog = new Dialog(context);
        dialog.setTitle("Bilibili 官方登录");
        dialog.setContentView(view);
        dialog.setOnDismissListener(d -> {
            if (!sessionSeen) clearSession();
            if (onClose != null) onClose.run();
        });
        view.loadUrl(LOGIN_URL);
        dialog.show();
    }

    public void clearSession() {
        sessionSeen = false;
        onSessionReady = null;
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        if (dialog != null) {
            dialog = null;
        }
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
    }

    public String cookiesFor(String url) {
        String webCookie = CookieManager.getInstance().getCookie(url);
        if (nativeCookies != null && !nativeCookies.isBlank()) {
            if (webCookie == null || webCookie.isBlank()) return nativeCookies;
            return webCookie + "; " + nativeCookies;
        }
        return webCookie;
    }

    public void setNativeCookies(String cookies) {
        nativeCookies = cookies;
    }
}
