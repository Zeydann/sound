package com.zeyy.instants;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String HOME = "https://www.myinstants.com/";
    private static final int BG = Color.parseColor("#121212");
    private static final int ACCENT = Color.parseColor("#E5484D");

    // Paths that lead to account pages: /login, /en/accounts/..., /signup, etc.
    private static final String ACCOUNT_PATH =
        "^/([a-z]{2}/)?(accounts|login|signin|signup|register)(/.*)?$";

    // Hides any link pointing at an account page, also on pages added later.
    private static final String HIDE_ACCOUNT_JS = """
        (function () {
          var re = /^(https?:\\/\\/[^\\/]*myinstants\\.com)?\\/([a-z]{2}\\/)?(accounts|login|signin|signup|register)(\\/|$|\\?)/i;
          var queued = false;
          function clean() {
            queued = false;
            document.querySelectorAll('a[href]').forEach(function (a) {
              if (re.test(a.getAttribute('href') || '')) {
                var item = a.closest('li') || a;
                item.style.display = 'none';
              }
            });
          }
          function schedule() {
            if (!queued) { queued = true; requestAnimationFrame(clean); }
          }
          clean();
          new MutationObserver(schedule).observe(document.documentElement,
            { childList: true, subtree: true });
        })();
        """;

    private static final String OFFLINE_HTML =
        "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
        + "<style>body{background:#121212;color:#d0d0d0;font-family:sans-serif;"
        + "display:flex;flex-direction:column;align-items:center;justify-content:center;"
        + "height:100vh;margin:0;padding:0 32px;text-align:center}"
        + "a{color:#E5484D;margin-top:20px;font-size:16px}</style></head><body>"
        + "<div>Tidak bisa terhubung ke myinstants.com</div>"
        + "<a href='" + HOME + "'>Coba lagi</a></body></html>";

    private WebView web;
    private ProgressBar bar;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        web = new WebView(this);
        web.setBackgroundColor(BG);
        root.addView(web, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.getProgressDrawable().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
        float dp = getResources().getDisplayMetrics().density;
        root.addView(bar, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.round(2 * dp)));

        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        // Dark rendering for the site. Remove these lines to get the original light look.
        if (Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(true);
        } else if (Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(WebSettings.FORCE_DARK_ON);
        }

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? ViewGroup.GONE : ViewGroup.VISIBLE);
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String host = u.getHost();
                if (host != null && host.endsWith("myinstants.com")) {
                    String path = u.getPath() == null ? "" : u.getPath();
                    return path.matches(ACCOUNT_PATH); // block account pages
                }
                startActivity(new Intent(Intent.ACTION_VIEW, u));
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(HIDE_ACCOUNT_JS, null);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    v.loadDataWithBaseURL(null, OFFLINE_HTML, "text/html", "utf-8", null);
                }
            }
        });

        if (b == null) web.loadUrl(HOME); else web.restoreState(b);

        showCreditOnce();
    }

    // Credit: sounds and website content belong to myinstants.com and its uploaders.
    private void showCreditOnce() {
        SharedPreferences p = getSharedPreferences("app", MODE_PRIVATE);
        if (!p.getBoolean("credit_shown", false)) {
            Toast.makeText(this,
                "Content by myinstants.com and its uploaders. Unofficial personal wrapper.",
                Toast.LENGTH_LONG).show();
            p.edit().putBoolean("credit_shown", true).apply();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
