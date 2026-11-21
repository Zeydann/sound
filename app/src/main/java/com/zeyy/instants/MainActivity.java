package com.zeyy.instants;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String HOME = "https://www.myinstants.com/";

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

    private WebView web;
    private ProgressBar bar;
    private View errorPanel;
    private String lastUrl = HOME;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        web = findViewById(R.id.web);
        bar = findViewById(R.id.progress);
        errorPanel = findViewById(R.id.error_panel);

        web.setBackgroundColor(color(R.color.bg));

        ImageButton refresh = findViewById(R.id.btn_refresh);
        refresh.setOnClickListener(v -> web.reload());

        ImageButton more = findViewById(R.id.btn_more);
        more.setOnClickListener(this::showMenu);

        findViewById(R.id.btn_retry).setOnClickListener(v -> retry());

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        // Site rendering follows the app theme so shell and content match in both modes.
        boolean night = (getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(night);
        } else if (Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(night ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        }

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
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
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) {
                lastUrl = url == null ? HOME : url;
                showError(false);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(HIDE_ACCOUNT_JS, null);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    bar.setVisibility(View.GONE);
                    showError(true);
                }
            }
        });

        if (b == null) web.loadUrl(HOME); else web.restoreState(b);

        showCreditOnce();
    }

    private int color(int id) {
        return getResources().getColor(id, getTheme());
    }

    private void showError(boolean show) {
        errorPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) web.setVisibility(View.INVISIBLE);
        else web.setVisibility(View.VISIBLE);
    }

    private void retry() {
        showError(false);
        web.loadUrl(lastUrl);
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.main_menu, menu.getMenu());
        menu.setOnMenuItemClickListener(this::onMenu);
        menu.show();
    }

    private boolean onMenu(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_reload) {
            web.reload();
            return true;
        }
        if (id == R.id.menu_home) {
            web.loadUrl(HOME);
            return true;
        }
        if (id == R.id.menu_credit) {
            new AlertDialog.Builder(this)
                .setMessage(R.string.credit_text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
            return true;
        }
        return false;
    }

    // Credit: sounds and website content belong to myinstants.com and its uploaders.
    private void showCreditOnce() {
        SharedPreferences p = getSharedPreferences("app", MODE_PRIVATE);
        if (!p.getBoolean("credit_shown", false)) {
            Toast.makeText(this, R.string.credit_text, Toast.LENGTH_LONG).show();
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
