package com.marco.lanquiz;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Accesso ai banchi: quelli preinstallati negli asset e quelli importati. */
public final class Banks {

    private static final String ASSET_DIR = "banks";

    private Banks() {
    }

    public static File userDir(Context c) {
        File dir = new File(c.getFilesDir(), "banks");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Tutti i banchi disponibili, con il numero di domande gia contato. */
    public static List<Bank> all(Context c) {
        List<Bank> out = new ArrayList<>();
        List<String> hidden = Store.hiddenBanks(c);
        try {
            String[] names = c.getAssets().list(ASSET_DIR);
            if (names != null) {
                for (String n : names) {
                    if (!hidden.contains(n)) {
                        out.add(new Bank(n, true));
                    }
                }
            }
        } catch (IOException ignored) {
        }
        File[] files = userDir(c).listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    out.add(new Bank(f.getName(), false));
                }
            }
        }
        for (Bank b : out) {
            b.count = load(c, b).size();
        }
        Collections.sort(out, new Comparator<Bank>() {
            @Override
            public int compare(Bank a, Bank b) {
                int byCat = a.category.compareToIgnoreCase(b.category);
                return byCat != 0 ? byCat : a.title.compareToIgnoreCase(b.title);
            }
        });
        return out;
    }

    /** I banchi raggruppati per categoria, gia nell ordine di visualizzazione. */
    public static Map<String, List<Bank>> byCategory(List<Bank> banks) {
        Map<String, List<Bank>> map = new LinkedHashMap<>();
        for (Bank b : banks) {
            List<Bank> list = map.get(b.category);
            if (list == null) {
                list = new ArrayList<>();
                map.put(b.category, list);
            }
            list.add(b);
        }
        List<Bank> others = map.remove("Altri quiz");
        if (others != null) {
            map.put("Altri quiz", others); // il gruppo generico va in fondo
        }
        return map;
    }

    public static List<Question> load(Context c, Bank bank) {
        if (bank.questions == null) {
            bank.questions = Parser.parse(read(c, bank));
            // gli id delle domande sono cambiati nella 1.1: qui si recuperano
            // ripasso e segnalibri salvati con quelli vecchi
            Store.migrateBank(c, bank.id, bank.questions);
        }
        return bank.questions;
    }

    public static Bank find(Context c, String id) {
        for (Bank b : all(c)) {
            if (b.id.equals(id)) {
                return b;
            }
        }
        return null;
    }

    public static String read(Context c, Bank bank) {
        try {
            InputStream in = bank.bundled
                    ? c.getAssets().open(ASSET_DIR + "/" + bank.id)
                    : new FileInputStream(new File(userDir(c), bank.id));
            return readAll(in);
        } catch (Exception e) {
            return "";
        }
    }

    static String readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------- importa

    /** Salva un banco importato. Restituisce il nome del file creato. */
    public static String save(Context c, String proposedName, String content) throws IOException {
        if (Parser.parse(content).isEmpty()) {
            throw new IOException(c.getString(R.string.nessuna_domanda_valida));
        }
        String name = sanitize(proposedName);
        File dir = userDir(c);
        File target = new File(dir, name);
        int i = 2;
        while (target.exists()) {
            String stem = name.replaceFirst("\\.[A-Za-z0-9]+$", "");
            target = new File(dir, stem + "_" + i + ".txt");
            i++;
        }
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return target.getName();
    }

    public static String importUri(Context c, Uri uri, String fallbackName) throws IOException {
        String name = displayName(c, uri);
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("file non leggibile");
        }
        return save(c, name != null ? name : fallbackName, readAll(in));
    }

    public static String importUrl(Context c, String url, String fallbackName) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new IOException("HTTP " + code);
        }
        String body = readAll(conn.getInputStream());
        conn.disconnect();
        String name = url.substring(url.lastIndexOf('/') + 1);
        int q = name.indexOf('?');
        if (q >= 0) {
            name = name.substring(0, q);
        }
        return save(c, name.isEmpty() ? fallbackName : name, body);
    }

    private static String displayName(Context c, Uri uri) {
        try (Cursor cur = c.getContentResolver().query(uri, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) {
                    return cur.getString(i);
                }
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? null : last.substring(last.lastIndexOf('/') + 1);
    }

    static String sanitize(String name) {
        String n = (name == null ? "" : name).trim().replaceAll("[\\\\/:*?\"<>|]+", "_");
        if (n.isEmpty()) {
            n = "quiz";
        }
        String lower = n.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".txt") && !lower.endsWith(".csv") && !lower.endsWith(".json")) {
            n = n + ".txt";
        }
        return n;
    }

    // ------------------------------------------------------------- modifica

    public static boolean rename(Context c, Bank bank, String newName) {
        if (bank.bundled) {
            return false;
        }
        File from = new File(userDir(c), bank.id);
        File to = new File(userDir(c), sanitize(newName));
        if (to.exists() || !from.renameTo(to)) {
            return false;
        }
        Store.renameBank(c, bank.id, to.getName());
        return true;
    }

    /** I banchi importati vengono cancellati, quelli preinstallati solo nascosti. */
    public static void delete(Context c, Bank bank) {
        if (bank.bundled) {
            Store.hideBank(c, bank.id);
        } else {
            new File(userDir(c), bank.id).delete();
        }
        Store.forgetBank(c, bank.id);
    }

    public static void restoreBundled(Context c) {
        Store.clearHiddenBanks(c);
    }
}
