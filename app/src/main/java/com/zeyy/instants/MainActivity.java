package com.zeyy.instants;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String HOME = "https://www.myinstants.com/";
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String host = r.getUrl().getHost();
                if (host != null && host.endsWith("myinstants.com")) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl()));
                return true;
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
