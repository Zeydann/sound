package com.zeyy.instants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads the site's public listing pages (name plus mp3 path per sound, 36 per page).
// ponytail: 3 pages per source keeps the first sync near 1700 sounds. Raise PAGES when needed.
class CatalogSync {
    interface Listener {
        void onProgress(int done, int total);
    }

    private static final int PAGES = 3;
    private static final String UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36";

    private static final String[][] SOURCES = {
        {"Beranda", "https://www.myinstants.com/"},
        {"Baru ditambahkan", "https://www.myinstants.com/en/recent/"},
        {"Anime & Manga", "https://www.myinstants.com/en/categories/Anime%20&%20Manga/"},
        {"Games", "https://www.myinstants.com/en/categories/Games/"},
        {"Memes", "https://www.myinstants.com/en/categories/Memes/"},
        {"Movies", "https://www.myinstants.com/en/categories/Movies/"},
        {"Music", "https://www.myinstants.com/en/categories/Music/"},
        {"Politics", "https://www.myinstants.com/en/categories/Politics/"},
        {"Pranks", "https://www.myinstants.com/en/categories/Pranks/"},
        {"Reactions", "https://www.myinstants.com/en/categories/Reactions/"},
        {"Sound Effects", "https://www.myinstants.com/en/categories/Sound%20Effects/"},
        {"Sports", "https://www.myinstants.com/en/categories/Sports/"},
        {"Television", "https://www.myinstants.com/en/categories/Television/"},
        {"Tiktok Trends", "https://www.myinstants.com/en/categories/Tiktok%20Trends/"},
        {"Viral", "https://www.myinstants.com/en/categories/Viral/"},
        {"Whatsapp Audios", "https://www.myinstants.com/en/categories/Whatsapp%20Audios/"}
    };

    private static final Pattern PLAY = Pattern.compile("play\\('([^']+)'");
    private static final Pattern LINK =
        Pattern.compile("<a href=\"([^\"]+)\" class=\"instant-link[^\"]*\">([^<]*)</a>");

    private CatalogSync() {}

    static List<Sound> fetchAll(Listener l) throws IOException {
        Map<String, Sound> found = new LinkedHashMap<>();
        int total = SOURCES.length * PAGES;
        int done = 0;
        int reached = 0;
        for (String[] source : SOURCES) {
            for (int page = 1; page <= PAGES; page++) {
                // Small categories run out of pages before PAGES; a missing page must not kill the run.
                String html = null;
                try {
                    html = fetch(source[1] + "?page=" + page);
                } catch (IOException e) {
                    html = null;
                }
                if (html != null) {
                    reached++;
                    for (Sound s : parse(html, source[0])) {
                        if (!found.containsKey(s.mp3)) found.put(s.mp3, s);
                    }
                }
                done++;
                if (l != null) l.onProgress(done, total);
            }
        }
        if (reached == 0) throw new IOException("no listing page could be fetched");
        return new ArrayList<>(found.values());
    }

    static List<Sound> parse(String html, String category) {
        List<Sound> out = new ArrayList<>();
        String[] chunks = html.split("<div class=\"instant\">");
        for (int i = 1; i < chunks.length; i++) {
            Matcher play = PLAY.matcher(chunks[i]);
            Matcher link = LINK.matcher(chunks[i]);
            if (!play.find() || !link.find()) continue;
            String name = unescape(link.group(2)).trim();
            if (name.isEmpty()) continue;
            out.add(new Sound(name, absolute(link.group(1)), absolute(play.group(1)), category));
        }
        return out;
    }

    private static String fetch(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "en");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private static String absolute(String path) {
        if (path.startsWith("http")) return path;
        return "https://www.myinstants.com" + (path.startsWith("/") ? path : "/" + path);
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">");
    }
}
