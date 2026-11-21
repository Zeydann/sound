package com.zeyy.instants;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Catalog lives in filesDir as JSON so the list opens without a network round trip.
class CatalogStore {
    private final File file;
    private final List<Sound> sounds = new ArrayList<>();

    CatalogStore(Context c) {
        file = new File(c.getFilesDir(), "catalog.json");
    }

    void load() {
        sounds.clear();
        if (!file.isFile()) return;
        try {
            JSONArray a = new JSONArray(read(file));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                sounds.add(new Sound(
                    o.getString("n"),
                    o.getString("p"),
                    o.getString("m"),
                    o.optString("c", "")));
            }
        } catch (Exception ignored) {
            sounds.clear();
        }
    }

    void replaceAll(List<Sound> next) throws IOException {
        sounds.clear();
        sounds.addAll(next);
        JSONArray a = new JSONArray();
        for (Sound s : sounds) {
            JSONObject o = new JSONObject();
            try {
                o.put("n", s.name);
                o.put("p", s.page);
                o.put("m", s.mp3);
                o.put("c", s.category);
            } catch (Exception ignored) {
                continue;
            }
            a.put(o);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(a.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    List<Sound> all() {
        return new ArrayList<>(sounds);
    }

    List<Sound> filter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return all();
        List<Sound> out = new ArrayList<>();
        for (Sound s : sounds) {
            if (s.name.toLowerCase(Locale.ROOT).contains(q)) out.add(s);
        }
        return out;
    }

    private static String read(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
