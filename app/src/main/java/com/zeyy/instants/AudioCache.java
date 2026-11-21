package com.zeyy.instants;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

// One file per sound under filesDir/sounds, trimmed to a fixed budget by last use.
class AudioCache {
    // ponytail: 200MB holds roughly 9000 sounds at 21KB. Raise if storage allows.
    private static final long CAP = 200L * 1024 * 1024;
    private static final String UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36";

    private final File dir;

    AudioCache(Context c) {
        dir = new File(c.getFilesDir(), "sounds");
        dir.mkdirs();
    }

    File file(String mp3) {
        String path = mp3;
        int cut = path.indexOf('?');
        if (cut >= 0) path = path.substring(0, cut);
        String base = path.substring(path.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isEmpty()) base = "sound";
        return new File(dir, base + "-" + Integer.toHexString(mp3.hashCode()));
    }

    boolean has(String mp3) {
        File f = file(mp3);
        return f.isFile() && f.length() > 0;
    }

    File fetch(String mp3) throws IOException {
        File target = file(mp3);
        if (target.isFile() && target.length() > 0) {
            target.setLastModified(System.currentTimeMillis());
            return target;
        }
        File part = new File(dir, target.getName() + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(mp3).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        } catch (IOException e) {
            part.delete();
            throw e;
        } finally {
            c.disconnect();
        }
        if (part.length() == 0 || !part.renameTo(target)) {
            part.delete();
            throw new IOException("empty or unreadable download");
        }
        target.setLastModified(System.currentTimeMillis());
        trim();
        return target;
    }

    long size() {
        long total = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) total += f.length();
        return total;
    }

    String sizeLabel() {
        return String.format(Locale.US, "%.1f MB", size() / (1024.0 * 1024.0));
    }

    void clear() {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) f.delete();
    }

    private void trim() {
        while (size() > CAP) {
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) return;
            File oldest = files[0];
            for (File f : files) {
                if (f.lastModified() < oldest.lastModified()) oldest = f;
            }
            if (!oldest.delete()) return;
        }
    }
}
