package com.zeyy.instants;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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
    private static final String RECENT = "https://www.myinstants.com/en/recent/";
    private static final String UPLOAD = "https://www.myinstants.com/en/new/";

    // Category labels and paths exactly as the site publishes them.
    private static final String[][] CATEGORIES = {
        {"Anime & Manga", "https://www.myinstants.com/en/categories/anime%20&%20manga/"},
        {"Games", "https://www.myinstants.com/en/categories/games/"},
        {"Memes", "https://www.myinstants.com/en/categories/memes/"},
        {"Movies", "https://www.myinstants.com/en/categories/movies/"},
        {"Music", "https://www.myinstants.com/en/categories/music/"},
        {"Politics", "https://www.myinstants.com/en/categories/politics/"},
        {"Pranks", "https://www.myinstants.com/en/categories/pranks/"},
        {"Reactions", "https://www.myinstants.com/en/categories/reactions/"},
        {"Sound Effects", "https://www.myinstants.com/en/categories/sound%20effects/"},
        {"Sports", "https://www.myinstants.com/en/categories/sports/"},
        {"Television", "https://www.myinstants.com/en/categories/television/"},
        {"Tiktok Trends", "https://www.myinstants.com/en/categories/tiktok%20trends/"},
        {"Viral", "https://www.myinstants.com/en/categories/viral/"},
        {"Whatsapp Audios", "https://www.myinstants.com/en/categories/whatsapp%20audios/"}
    };

    // Paths that lead to account pages: /login, /en/accounts/..., /signup, etc.
    private static final String ACCOUNT_PATH =
        "^/([a-z]{2}/)?(accounts|login|signin|signup|register)(/.*)?$";

    // Hides the site chrome (nav bar, Install app, account links) and parks the
    // site's own search form at the top of the body so the native search button can reveal it.
    private static final String SHELL_JS = """
        (function () {
          try {
            if (!document.getElementById('instants-shell')) {
              var css = document.createElement('style');
              css.id = 'instants-shell';
              css.textContent =
                'nav.navbar,header,.header,#install-app-nav{display:none!important}' +
                'body{margin-top:0!important;padding-top:0!important}' +
                'html{scroll-padding-top:0!important}' +
                '#instants-search-host{padding:8px 12px 4px}' +
                '#instants-search-host form{margin:0;display:flex}' +
                '#instants-search-host img{display:none!important}' +
                '#instants-search-host input{background:@SURFACE@!important;color:@TEXT@!important;' +
                'border:1px solid @BORDER@!important;border-radius:8px!important;padding:10px 12px!important}' +
                '#instants-search-host input::placeholder{color:@TEXT@;opacity:0.55}';
              (document.head || document.documentElement).appendChild(css);
            }
            if (!document.getElementById('instants-search-host')) {
              var form = document.getElementById('searchbar');
              if (form && document.body) {
                var host = document.createElement('div');
                host.id = 'instants-search-host';
                host.style.display = 'none';
                document.body.insertBefore(host, document.body.firstChild);
                host.appendChild(form);
                form.className = 'search-form';
              }
            }
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
            if (!window.__instantsObserver) {
              window.__instantsObserver = new MutationObserver(schedule);
              window.__instantsObserver.observe(document.documentElement,
                { childList: true, subtree: true });
            }
          } catch (e) {}
        })();
        """;

    private static final String TOGGLE_SEARCH_JS = """
        (function () {
          try {
            var host = document.getElementById('instants-search-host');
            if (!host) { location.href = '/en/search/'; return; }
            var open = host.style.display === 'none';
            host.style.display = open ? 'block' : 'none';
            if (open) {
              var input = host.querySelector('input');
              if (input) input.focus();
              window.scrollTo(0, 0);
            }
          } catch (e) {}
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

        ImageButton menu = findViewById(R.id.btn_menu);
        menu.setOnClickListener(this::showNavMenu);

        ImageButton search = findViewById(R.id.btn_search);
        search.setOnClickListener(v -> {
            web.requestFocus();
            web.evaluateJavascript(TOGGLE_SEARCH_JS, null);
            InputMethodManager imm =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT);
        });

        ImageButton refresh = findViewById(R.id.btn_refresh);
        refresh.setOnClickListener(v -> web.reload());

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

            // Inject before first paint where possible, so the site bar does not flash.
            @Override
            public void onPageCommitVisible(WebView v, String url) {
                v.evaluateJavascript(shellJs(), null);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(shellJs(), null);
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

    private String hex(int id) {
        return String.format("#%06X", 0xFFFFFF & color(id));
    }

    private String shellJs() {
        return SHELL_JS
            .replace("@SURFACE@", hex(R.color.surface))
            .replace("@TEXT@", hex(R.color.text_primary))
            .replace("@BORDER@", hex(R.color.divider));
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

    private void showNavMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.nav_menu, menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                web.loadUrl(HOME);
                return true;
            }
            if (id == R.id.nav_recent) {
                web.loadUrl(RECENT);
                return true;
            }
            if (id == R.id.nav_upload) {
                web.loadUrl(UPLOAD);
                return true;
            }
            if (id == R.id.nav_categories) {
                showCategories(anchor);
                return true;
            }
            if (id == R.id.nav_credit) {
                showCredit();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showCategories(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        for (int i = 0; i < CATEGORIES.length; i++) {
            menu.getMenu().add(Menu.NONE, i, i, CATEGORIES[i][0]);
        }
        menu.setOnMenuItemClickListener(item -> {
            int i = item.getItemId();
            if (i < 0 || i >= CATEGORIES.length) return false;
            web.loadUrl(CATEGORIES[i][1]);
            return true;
        });
        menu.show();
    }

    private void showCredit() {
        new AlertDialog.Builder(this)
            .setMessage(R.string.credit_text)
            .setPositiveButton(android.R.string.ok, null)
            .show();
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
