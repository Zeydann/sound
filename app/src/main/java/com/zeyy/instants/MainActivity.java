package com.zeyy.instants;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String HOME = "https://www.myinstants.com/";

    private GridView list;
    private ProgressBar bar;
    private EditText searchField;
    private View searchDivider;
    private View statusPanel;
    private TextView statusTitle;
    private TextView statusBody;
    private Button statusAction;

    private CatalogStore store;
    private AudioCache cache;
    private SoundAdapter adapter;

    private MediaPlayer player;
    private Sound playing;
    private final Set<String> downloading = new HashSet<>();
    private boolean syncing;

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS) stopPlayback();
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        list = findViewById(R.id.list);
        bar = findViewById(R.id.progress);
        searchField = findViewById(R.id.search_field);
        searchDivider = findViewById(R.id.search_divider);
        statusPanel = findViewById(R.id.status_panel);
        statusTitle = findViewById(R.id.status_title);
        statusBody = findViewById(R.id.status_body);
        statusAction = findViewById(R.id.btn_status_action);

        store = new CatalogStore(this);
        store.load();
        cache = new AudioCache(this);
        adapter = new SoundAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> play(adapter.getItem(pos)));

        ImageButton menu = findViewById(R.id.btn_menu);
        menu.setOnClickListener(this::showMenu);

        ImageButton search = findViewById(R.id.btn_search);
        search.setOnClickListener(v -> toggleSearch());

        ImageButton sync = findViewById(R.id.btn_sync);
        sync.setOnClickListener(v -> startSync());

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int c, int d) {}

            @Override
            public void afterTextChanged(Editable e) {
                refreshList();
            }
        });

        refreshList();
    }

    @Override
    protected void onPostCreate(Bundle b) {
        super.onPostCreate(b);
        if (searchField.length() > 0) {
            searchField.setVisibility(View.VISIBLE);
            searchDivider.setVisibility(View.VISIBLE);
        }
    }

    private int color(int id) {
        return getResources().getColor(id, getTheme());
    }

    private void refreshList() {
        if (syncing) {
            list.setVisibility(View.VISIBLE);
            statusPanel.setVisibility(View.GONE);
            return;
        }
        List<Sound> items = store.filter(searchField.getText().toString());
        adapter.setItems(items);
        if (!items.isEmpty()) {
            list.setVisibility(View.VISIBLE);
            statusPanel.setVisibility(View.GONE);
            return;
        }
        if (store.all().isEmpty()) {
            showStatus(R.string.empty_title, R.string.empty_body,
                R.string.action_sync, this::startSync);
        } else {
            showStatus(R.string.no_match_title, R.string.no_match_body, 0, null);
        }
    }

    private void showStatus(int title, int body, int action, Runnable run) {
        statusTitle.setText(title);
        statusBody.setText(body);
        bindStatusAction(action, run);
        list.setVisibility(View.GONE);
        statusPanel.setVisibility(View.VISIBLE);
    }

    private void bindStatusAction(int action, Runnable run) {
        if (action == 0 || run == null) {
            statusAction.setVisibility(View.GONE);
            statusAction.setOnClickListener(null);
        } else {
            statusAction.setVisibility(View.VISIBLE);
            statusAction.setText(action);
            statusAction.setOnClickListener(v -> run.run());
        }
    }

    private void startSync() {
        if (syncing) return;
        syncing = true;
        bar.setProgress(0);
        bar.setVisibility(View.VISIBLE);
        list.setVisibility(View.VISIBLE);
        statusPanel.setVisibility(View.GONE);

        new Thread(() -> {
            List<Sound> fetched = null;
            try {
                fetched = CatalogSync.fetchAll((done, total) -> runOnUiThread(() ->
                    bar.setProgress(total == 0 ? 0 : done * 100 / total)));
            } catch (Exception e) {
                fetched = null;
            }
            final List<Sound> result = fetched;
            runOnUiThread(() -> {
                syncing = false;
                bar.setVisibility(View.GONE);
                if (result == null) {
                    if (store.all().isEmpty()) {
                        showStatus(R.string.sync_failed_title, R.string.sync_failed_body,
                            R.string.action_retry, this::startSync);
                    } else {
                        refreshList();
                        toast(R.string.sync_failed_body);
                    }
                    return;
                }
                if (result.isEmpty()) {
                    showStatus(R.string.sync_empty_title, R.string.sync_empty_body,
                        R.string.action_retry, this::startSync);
                    return;
                }
                try {
                    store.replaceAll(result);
                } catch (Exception ignored) {
                }
                refreshList();
            });
        }).start();
    }

    private void play(Sound s) {
        File cached = cache.file(s.mp3);
        if (cached.isFile() && cached.length() > 0) {
            startPlayback(s, cached);
            return;
        }
        if (downloading.contains(s.mp3)) return;
        downloading.add(s.mp3);
        adapter.notifyDataSetChanged();

        new Thread(() -> {
            File got = null;
            try {
                got = cache.fetch(s.mp3);
            } catch (Exception ignored) {
                got = null;
            }
            final File file = got;
            runOnUiThread(() -> {
                downloading.remove(s.mp3);
                adapter.notifyDataSetChanged();
                if (file == null) {
                    toast(R.string.need_network);
                    return;
                }
                startPlayback(s, file);
            });
        }).start();
    }

    private void startPlayback(Sound s, File file) {
        stopPlayback();
        try {
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            player.setOnCompletionListener(mp -> stopPlayback());
            player.start();
            playing = s;
            requestFocus();
        } catch (Exception e) {
            stopPlayback();
            toast(R.string.play_failed);
            return;
        }
        adapter.notifyDataSetChanged();
    }

    private void requestFocus() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void stopPlayback() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) am.abandonAudioFocus(focusListener);
        playing = null;
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void toggleSearch() {
        boolean show = searchField.getVisibility() != View.VISIBLE;
        searchField.setVisibility(show ? View.VISIBLE : View.GONE);
        searchDivider.setVisibility(show ? View.VISIBLE : View.GONE);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (show) {
            searchField.requestFocus();
            if (imm != null) imm.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
        } else {
            searchField.setText("");
            if (imm != null) imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        }
        refreshList();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.nav_menu, menu.getMenu());
        menu.setOnMenuItemClickListener(this::onMenu);
        menu.show();
    }

    private boolean onMenu(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_sync) {
            startSync();
            return true;
        }
        if (id == R.id.menu_site) {
            openSite();
            return true;
        }
        if (id == R.id.menu_clear_cache) {
            confirmClearCache();
            return true;
        }
        if (id == R.id.menu_credit) {
            showCredit();
            return true;
        }
        return false;
    }

    private void openSite() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(HOME)));
        } catch (Exception ignored) {
        }
    }

    private void confirmClearCache() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.clear_cache_title)
            .setMessage(R.string.clear_cache_body)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_clear_cache, (d, w) -> {
                cache.clear();
                toast(R.string.cache_cleared);
                adapter.notifyDataSetChanged();
            })
            .show();
    }

    private void showCredit() {
        String body = getString(R.string.credit_text)
            + "\n\n" + getString(R.string.cache_usage, cache.sizeLabel());
        new AlertDialog.Builder(this)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_LONG).show();
    }

    private String statusFor(Sound s) {
        if (playing != null && playing.mp3.equals(s.mp3)) return getString(R.string.status_playing);
        if (downloading.contains(s.mp3)) return getString(R.string.status_downloading);
        if (cache.has(s.mp3)) return getString(R.string.status_saved);
        return getString(R.string.status_not_saved);
    }

    @Override
    public void onBackPressed() {
        if (searchField.getVisibility() == View.VISIBLE) {
            toggleSearch();
            return;
        }
        if (playing != null) {
            stopPlayback();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        stopPlayback();
        super.onDestroy();
    }

    private class SoundAdapter extends BaseAdapter {
        private List<Sound> items = new ArrayList<>();

        void setItems(List<Sound> next) {
            items = next;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Sound getItem(int pos) {
            return items.get(pos);
        }

        @Override
        public long getItemId(int pos) {
            return items.get(pos).mp3.hashCode();
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            if (convert == null) {
                convert = getLayoutInflater().inflate(R.layout.item_sound, parent, false);
            }
            Sound s = items.get(pos);
            boolean isPlaying = playing != null && playing.mp3.equals(s.mp3);
            ImageView icon = convert.findViewById(R.id.icon);
            icon.setImageResource(isPlaying ? R.drawable.ic_stop : R.drawable.ic_play);
            icon.setColorFilter(color(isPlaying ? R.color.accent : R.color.text_secondary));
            ((TextView) convert.findViewById(R.id.name)).setText(s.name);
            ((TextView) convert.findViewById(R.id.status)).setText(statusFor(s));
            return convert;
        }
    }
}
