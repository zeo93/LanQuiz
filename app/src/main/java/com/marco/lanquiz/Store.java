package com.marco.lanquiz;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tutto ciò che l'app ricorda fra un avvio e l'altro: preferenze, storico dei
 * tentativi, stato di ripasso di ogni domanda, note, argomenti e segnalibri.
 * Sta tutto in SharedPreferences, in JSON: nessun database da migrare.
 */
public final class Store {

    private static final String PREFS = "lanquiz";
    private static final int MAX_HISTORY = 500;

    // chiavi impostazioni
    public static final String MODE = "mode";                 // "studio" | "esame"
    public static final String COUNT = "count";               // 0 = tutte
    public static final String TIMER = "timer";               // minuti, 0 = nessuno
    public static final String SHUFFLE_Q = "shuffle_q";
    public static final String SHUFFLE_A = "shuffle_a";
    public static final String PASS_PCT = "pass_pct";
    public static final String THEME = "theme";               // "sistema" | "chiaro" | "scuro"
    public static final String HAPTICS = "haptics";
    public static final String AUTO_NEXT = "auto_next";
    public static final String UPDATE_CHECK = "update_check";

    public static final String MODE_STUDY = "studio";
    public static final String MODE_EXAM = "esame";

    /**
     * Scatole di Leitner: quanti giorni passano prima che una domanda torni a
     * farsi vedere. Chi sbaglia torna alla scatola 0 (subito in scadenza), chi
     * risponde bene sale di una scatola e sparisce per un po'.
     */
    static final int[] GIORNI_PER_SCATOLA = {0, 1, 3, 7, 16, 35};
    public static final int SCATOLA_MAX = GIORNI_PER_SCATOLA.length - 1;

    private static final long GIORNO = 24L * 60 * 60 * 1000;

    private Store() {
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ impostazioni

    public static String mode(Context c) {
        return prefs(c).getString(MODE, MODE_STUDY);
    }

    public static int count(Context c) {
        return prefs(c).getInt(COUNT, 0);
    }

    public static int timer(Context c) {
        return prefs(c).getInt(TIMER, 0);
    }

    public static boolean shuffleQuestions(Context c) {
        return prefs(c).getBoolean(SHUFFLE_Q, true);
    }

    public static boolean shuffleAnswers(Context c) {
        return prefs(c).getBoolean(SHUFFLE_A, true);
    }

    public static int passPct(Context c) {
        return prefs(c).getInt(PASS_PCT, 70);
    }

    public static String theme(Context c) {
        return prefs(c).getString(THEME, "sistema");
    }

    public static boolean haptics(Context c) {
        return prefs(c).getBoolean(HAPTICS, true);
    }

    public static boolean autoNext(Context c) {
        return prefs(c).getBoolean(AUTO_NEXT, true);
    }

    public static boolean updateCheck(Context c) {
        return prefs(c).getBoolean(UPDATE_CHECK, true);
    }

    public static void set(Context c, String key, Object value) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (value instanceof Boolean) {
            e.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            e.putInt(key, (Integer) value);
        } else {
            e.putString(key, String.valueOf(value));
        }
        e.apply();
    }

    // ---------------------------------------------------- banchi nascosti

    public static List<String> hiddenBanks(Context c) {
        return toList(prefs(c).getString("hidden_banks", "[]"));
    }

    public static void hideBank(Context c, String id) {
        List<String> l = hiddenBanks(c);
        if (!l.contains(id)) {
            l.add(id);
            prefs(c).edit().putString("hidden_banks", new JSONArray(l).toString()).apply();
        }
    }

    public static void clearHiddenBanks(Context c) {
        prefs(c).edit().remove("hidden_banks").apply();
    }

    // ---------------------------------------------------------- ripasso

    /** Lo stato di ripasso di una domanda. */
    public static class Card {
        public int box;
        public long due;
        public long last;
        public int ok;
        public int ko;

        public boolean dovuta(long adesso) {
            return due <= adesso;
        }
    }

    public static Map<String, Card> cards(Context c, String bankId) {
        Map<String, Card> out = new HashMap<>();
        try {
            JSONObject o = new JSONObject(prefs(c).getString("srs_" + bankId, "{}"));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String qid = it.next();
                JSONObject j = o.getJSONObject(qid);
                Card card = new Card();
                card.box = j.optInt("box");
                card.due = j.optLong("due");
                card.last = j.optLong("last");
                card.ok = j.optInt("ok");
                card.ko = j.optInt("ko");
                out.put(qid, card);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static void saveCards(Context c, String bankId, Map<String, Card> cards) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Card> e : cards.entrySet()) {
                Card card = e.getValue();
                JSONObject j = new JSONObject();
                j.put("box", card.box);
                j.put("due", card.due);
                j.put("last", card.last);
                j.put("ok", card.ok);
                j.put("ko", card.ko);
                o.put(e.getKey(), j);
            }
            prefs(c).edit().putString("srs_" + bankId, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static long scadenzaDopo(int box, long adesso) {
        int b = Math.max(0, Math.min(SCATOLA_MAX, box));
        return adesso + GIORNI_PER_SCATOLA[b] * GIORNO;
    }

    /**
     * Registra l'esito di una sessione: chi ha risposto bene sale di scatola e
     * torna più avanti nel tempo, chi ha sbagliato ricomincia dalla prima.
     */
    public static void grade(Context c, String bankId,
                             List<String> rightIds, List<String> wrongIds) {
        long adesso = System.currentTimeMillis();
        Map<String, Card> cards = cards(c, bankId);
        if (rightIds != null) {
            for (String id : rightIds) {
                Card card = cards.get(id);
                if (card == null) {
                    card = new Card();
                    cards.put(id, card);
                }
                card.box = Math.min(SCATOLA_MAX, card.box + 1);
                card.ok++;
                card.last = adesso;
                card.due = scadenzaDopo(card.box, adesso);
            }
        }
        if (wrongIds != null) {
            for (String id : wrongIds) {
                Card card = cards.get(id);
                if (card == null) {
                    card = new Card();
                    cards.put(id, card);
                }
                card.box = 0;
                card.ko++;
                card.last = adesso;
                card.due = adesso;
            }
        }
        saveCards(c, bankId, cards);
    }

    /** Le domande di questo banco che oggi tocca ripassare. */
    public static Set<String> dueIds(Context c, String bankId) {
        long adesso = System.currentTimeMillis();
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, Card> e : cards(c, bankId).entrySet()) {
            if (e.getValue().dovuta(adesso)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public static Set<String> seenIds(Context c, String bankId) {
        return new HashSet<>(cards(c, bankId).keySet());
    }

    /** Quando torna la prossima domanda non ancora in scadenza, 0 se nessuna. */
    public static long nextDue(Context c, String bankId, Collection<String> questionIds) {
        long adesso = System.currentTimeMillis();
        long prossima = 0;
        for (Map.Entry<String, Card> e : cards(c, bankId).entrySet()) {
            if (questionIds != null && !questionIds.contains(e.getKey())) {
                continue;
            }
            long due = e.getValue().due;
            if (due > adesso && (prossima == 0 || due < prossima)) {
                prossima = due;
            }
        }
        return prossima;
    }

    /**
     * Porta i dati della versione 1.0 nel nuovo formato. Gli identificatori
     * delle domande sono cambiati (ora sono gli stessi della web app), quindi
     * la conversione passa dal testo delle domande per ritrovarli.
     */
    static void migrateBank(Context c, String bankId, List<Question> questions) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean("migrato_" + bankId, false)) {
            return;
        }
        Map<String, String> vecchioNuovo = new HashMap<>();
        for (Question q : questions) {
            vecchioNuovo.put(q.legacyId(), q.id());
        }

        List<String> vecchieSbagliate = toList(p.getString("wrong_" + bankId, "[]"));
        if (!vecchieSbagliate.isEmpty()) {
            long adesso = System.currentTimeMillis();
            Map<String, Card> cards = cards(c, bankId);
            for (String vecchio : vecchieSbagliate) {
                String nuovo = vecchioNuovo.get(vecchio);
                if (nuovo != null && !cards.containsKey(nuovo)) {
                    Card card = new Card();
                    card.box = 0;
                    card.ko = 1;
                    card.due = adesso;
                    card.last = adesso;
                    cards.put(nuovo, card);
                }
            }
            saveCards(c, bankId, cards);
        }

        List<String> vecchiSegnalibri = toList(p.getString("flag_" + bankId, "[]"));
        List<String> nuoviSegnalibri = new ArrayList<>();
        for (String vecchio : vecchiSegnalibri) {
            String nuovo = vecchioNuovo.get(vecchio);
            nuoviSegnalibri.add(nuovo != null ? nuovo : vecchio);
        }

        p.edit()
                .remove("wrong_" + bankId)
                .putString("flag_" + bankId, new JSONArray(nuoviSegnalibri).toString())
                .putBoolean("migrato_" + bankId, true)
                .apply();
    }

    // ----------------------------------------------- domande contrassegnate

    public static Set<String> flagIds(Context c, String bankId) {
        return new HashSet<>(toList(prefs(c).getString("flag_" + bankId, "[]")));
    }

    public static boolean toggleFlag(Context c, String bankId, String questionId) {
        Set<String> ids = flagIds(c, bankId);
        boolean added = ids.add(questionId);
        if (!added) {
            ids.remove(questionId);
        }
        prefs(c).edit().putString("flag_" + bankId,
                new JSONArray(new ArrayList<>(ids)).toString()).apply();
        return added;
    }

    // --------------------------------------------------------------- note

    public static Map<String, String> notes(Context c, String bankId) {
        Map<String, String> out = new HashMap<>();
        try {
            JSONObject o = new JSONObject(prefs(c).getString("note_" + bankId, "{}"));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String qid = it.next();
                out.put(qid, o.optString(qid));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String note(Context c, String bankId, String questionId) {
        String n = notes(c, bankId).get(questionId);
        return n == null ? "" : n;
    }

    public static void setNote(Context c, String bankId, String questionId, String text) {
        try {
            JSONObject o = new JSONObject(prefs(c).getString("note_" + bankId, "{}"));
            if (text == null || text.trim().isEmpty()) {
                o.remove(questionId);
            } else {
                o.put(questionId, text.trim());
            }
            prefs(c).edit().putString("note_" + bankId, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------- argomenti

    /** Argomenti aggiunti a mano, oltre a quelli scritti nel file. */
    public static Map<String, List<String>> userTags(Context c, String bankId) {
        Map<String, List<String>> out = new HashMap<>();
        try {
            JSONObject o = new JSONObject(prefs(c).getString("tag_" + bankId, "{}"));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String qid = it.next();
                out.put(qid, toList(o.optJSONArray(qid) == null
                        ? "[]" : o.getJSONArray(qid).toString()));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void setUserTags(Context c, String bankId, String questionId,
                                   List<String> tags) {
        try {
            JSONObject o = new JSONObject(prefs(c).getString("tag_" + bankId, "{}"));
            if (tags == null || tags.isEmpty()) {
                o.remove(questionId);
            } else {
                o.put(questionId, new JSONArray(tags));
            }
            prefs(c).edit().putString("tag_" + bankId, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Gli argomenti che valgono per una domanda: quelli del file più i tuoi. */
    public static List<String> tagsOf(Question q, List<String> fromUser) {
        List<String> out = new ArrayList<>(q.tags);
        if (fromUser != null) {
            for (String t : fromUser) {
                if (!out.contains(t)) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------- storico

    /** Un tentativo concluso. */
    public static class Result {
        public String bankId;
        public String title;
        public long time;
        public int correct;
        public int total;
        public String mode;
        public int seconds;

        public int percent() {
            return total > 0 ? Math.round(correct * 100f / total) : 0;
        }
    }

    public static void recordResult(Context c, String bankId, String title, String mode,
                                    int correct, int total, int seconds,
                                    List<String> rightIds, List<String> wrongIds) {
        try {
            JSONArray hist = new JSONArray(prefs(c).getString("history", "[]"));
            JSONObject o = new JSONObject();
            o.put("bank", bankId);
            o.put("title", title);
            o.put("ts", System.currentTimeMillis());
            o.put("correct", correct);
            o.put("total", total);
            o.put("mode", mode);
            o.put("seconds", seconds);
            hist.put(o);
            while (hist.length() > MAX_HISTORY) {
                hist.remove(0);
            }
            prefs(c).edit().putString("history", hist.toString()).apply();
        } catch (Exception ignored) {
        }
        grade(c, bankId, rightIds, wrongIds);
    }

    public static List<Result> history(Context c) {
        List<Result> out = new ArrayList<>();
        try {
            JSONArray hist = new JSONArray(prefs(c).getString("history", "[]"));
            for (int i = 0; i < hist.length(); i++) {
                out.add(resultFrom(hist.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        Collections.reverse(out); // il più recente per primo
        return out;
    }

    private static Result resultFrom(JSONObject o) {
        Result r = new Result();
        r.bankId = o.optString("bank");
        r.title = o.optString("title");
        r.time = o.optLong("ts");
        r.correct = o.optInt("correct");
        r.total = o.optInt("total");
        r.mode = o.optString("mode", MODE_STUDY);
        r.seconds = o.optInt("seconds");
        return r;
    }

    public static List<Result> historyFor(Context c, String bankId) {
        List<Result> out = new ArrayList<>();
        for (Result r : history(c)) {
            if (r.bankId.equals(bankId)) {
                out.add(r);
            }
        }
        return out;
    }

    /** Azzera i numeri, non quello che hai scritto: note e argomenti restano. */
    public static void clearHistory(Context c) {
        SharedPreferences.Editor e = prefs(c).edit();
        e.remove("history");
        for (String k : prefs(c).getAll().keySet()) {
            if (k.startsWith("srs_")) {
                e.remove(k);
            }
        }
        e.apply();
    }

    // ------------------------------------------------------- quiz interrotto

    public static void saveResume(Context c, String json) {
        prefs(c).edit().putString("resume", json).apply();
    }

    public static String loadResume(Context c) {
        return prefs(c).getString("resume", null);
    }

    public static void clearResume(Context c) {
        prefs(c).edit().remove("resume").apply();
    }

    // ------------------------------------------------------------- pulizia

    /** Toglie ogni traccia di un banco cancellato. */
    public static void forgetBank(Context c, String bankId) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (String prefix : new String[]{"srs_", "flag_", "note_", "tag_", "migrato_"}) {
            e.remove(prefix + bankId);
        }
        try {
            JSONArray hist = new JSONArray(prefs(c).getString("history", "[]"));
            JSONArray kept = new JSONArray();
            for (int i = 0; i < hist.length(); i++) {
                if (!bankId.equals(hist.getJSONObject(i).optString("bank"))) {
                    kept.put(hist.getJSONObject(i));
                }
            }
            e.putString("history", kept.toString());
        } catch (Exception ignored) {
        }
        e.apply();
        String resume = loadResume(c);
        if (resume != null && resume.contains(bankId)) {
            clearResume(c);
        }
    }

    static void renameBank(Context c, String oldId, String newId) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        for (String prefix : new String[]{"srs_", "flag_", "note_", "tag_"}) {
            String value = p.getString(prefix + oldId, null);
            if (value != null) {
                e.putString(prefix + newId, value).remove(prefix + oldId);
            }
        }
        e.putBoolean("migrato_" + newId, true).remove("migrato_" + oldId);
        try {
            JSONArray hist = new JSONArray(p.getString("history", "[]"));
            for (int i = 0; i < hist.length(); i++) {
                JSONObject o = hist.getJSONObject(i);
                if (oldId.equals(o.optString("bank"))) {
                    o.put("bank", newId);
                    o.put("title", newId.replaceFirst("\\.[A-Za-z0-9]+$", "").replace('_', ' '));
                }
            }
            e.putString("history", hist.toString());
        } catch (Exception ignored) {
        }
        e.apply();
    }

    // -------------------------------------------------------------- backup

    /**
     * Tutto quello che vale la pena portarsi da un dispositivo all'altro, nello
     * stesso formato letto e scritto dalla web app.
     */
    public static JSONObject exportAll(Context c) throws Exception {
        JSONObject out = new JSONObject();
        out.put("app", "LanQuiz");
        out.put("formato", 1);
        out.put("esportato", System.currentTimeMillis());

        JSONObject settings = new JSONObject();
        settings.put("mode", mode(c));
        settings.put("count", count(c));
        settings.put("timer", timer(c));
        settings.put("shuffleQ", shuffleQuestions(c));
        settings.put("shuffleA", shuffleAnswers(c));
        settings.put("passPct", passPct(c));
        settings.put("theme", theme(c));
        settings.put("autoNext", autoNext(c));
        out.put("settings", settings);

        out.put("history", new JSONArray(prefs(c).getString("history", "[]")));
        out.put("hidden", new JSONArray(hiddenBanks(c)));

        JSONObject srs = new JSONObject();
        JSONObject flags = new JSONObject();
        JSONObject notes = new JSONObject();
        JSONObject tags = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs(c).getAll().entrySet()) {
            String k = entry.getKey();
            if (k.startsWith("srs_")) {
                srs.put(k.substring(4), new JSONObject(String.valueOf(entry.getValue())));
            } else if (k.startsWith("flag_")) {
                flags.put(k.substring(5), new JSONArray(String.valueOf(entry.getValue())));
            } else if (k.startsWith("note_")) {
                notes.put(k.substring(5), new JSONObject(String.valueOf(entry.getValue())));
            } else if (k.startsWith("tag_")) {
                tags.put(k.substring(4), new JSONObject(String.valueOf(entry.getValue())));
            }
        }
        out.put("srs", srs);
        out.put("flags", flags);
        out.put("notes", notes);
        out.put("tags", tags);

        JSONObject banks = new JSONObject();
        java.io.File[] files = Banks.userDir(c).listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isFile()) {
                    banks.put(f.getName(), Banks.readAll(new java.io.FileInputStream(f)));
                }
            }
        }
        out.put("banks", banks);
        return out;
    }

    /**
     * Rimette dentro un backup. Con {@code sostituisci} il contenuto del file
     * prende il posto di quello che c'è; altrimenti si fondono, e sul ripasso
     * vince la voce aggiornata più di recente.
     */
    public static int importAll(Context c, JSONObject in, boolean sostituisci)
            throws Exception {
        if (!"LanQuiz".equals(in.optString("app"))) {
            throw new IllegalArgumentException("non è un backup di LanQuiz");
        }
        if (sostituisci) {
            SharedPreferences.Editor wipe = prefs(c).edit();
            for (String k : new ArrayList<>(prefs(c).getAll().keySet())) {
                wipe.remove(k);
            }
            wipe.apply();
            java.io.File[] files = Banks.userDir(c).listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    f.delete();
                }
            }
        }

        JSONObject settings = in.optJSONObject("settings");
        if (settings != null) {
            SharedPreferences.Editor e = prefs(c).edit();
            e.putString(MODE, settings.optString("mode", MODE_STUDY));
            e.putInt(COUNT, settings.optInt("count", 0));
            e.putInt(TIMER, settings.optInt("timer", 0));
            e.putBoolean(SHUFFLE_Q, settings.optBoolean("shuffleQ", true));
            e.putBoolean(SHUFFLE_A, settings.optBoolean("shuffleA", true));
            e.putInt(PASS_PCT, settings.optInt("passPct", 70));
            e.putString(THEME, settings.optString("theme", "sistema"));
            e.putBoolean(AUTO_NEXT, settings.optBoolean("autoNext", true));
            e.apply();
        }

        int banchi = 0;
        JSONObject banks = in.optJSONObject("banks");
        if (banks != null) {
            for (Iterator<String> it = banks.keys(); it.hasNext(); ) {
                String name = it.next();
                java.io.File target = new java.io.File(Banks.userDir(c), name);
                if (!target.exists()) {
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                        out.write(banks.getString(name)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    banchi++;
                }
            }
        }

        mergeHistory(c, in.optJSONArray("history"));
        mergeSrs(c, in.optJSONObject("srs"));
        mergeStringMap(c, "note_", in.optJSONObject("notes"));
        mergeTagMap(c, in.optJSONObject("tags"));
        mergeFlags(c, in.optJSONObject("flags"));

        JSONArray hidden = in.optJSONArray("hidden");
        if (hidden != null) {
            Set<String> merged = new HashSet<>(hiddenBanks(c));
            for (int i = 0; i < hidden.length(); i++) {
                merged.add(hidden.getString(i));
            }
            prefs(c).edit().putString("hidden_banks",
                    new JSONArray(new ArrayList<>(merged)).toString()).apply();
        }
        // i banchi importati partono già con gli id nuovi: niente da convertire
        SharedPreferences.Editor done = prefs(c).edit();
        if (banks != null) {
            for (Iterator<String> it = banks.keys(); it.hasNext(); ) {
                done.putBoolean("migrato_" + it.next(), true);
            }
        }
        done.apply();
        return banchi;
    }

    private static void mergeHistory(Context c, JSONArray incoming) throws Exception {
        if (incoming == null) {
            return;
        }
        JSONArray mine = new JSONArray(prefs(c).getString("history", "[]"));
        Set<String> visti = new HashSet<>();
        for (int i = 0; i < mine.length(); i++) {
            JSONObject o = mine.getJSONObject(i);
            visti.add(o.optString("bank") + "@" + o.optLong("ts"));
        }
        List<JSONObject> tutti = new ArrayList<>();
        for (int i = 0; i < mine.length(); i++) {
            tutti.add(mine.getJSONObject(i));
        }
        for (int i = 0; i < incoming.length(); i++) {
            JSONObject o = incoming.getJSONObject(i);
            if (visti.add(o.optString("bank") + "@" + o.optLong("ts"))) {
                tutti.add(o);
            }
        }
        Collections.sort(tutti, (a, b) -> Long.compare(a.optLong("ts"), b.optLong("ts")));
        JSONArray out = new JSONArray();
        int from = Math.max(0, tutti.size() - MAX_HISTORY);
        for (int i = from; i < tutti.size(); i++) {
            out.put(tutti.get(i));
        }
        prefs(c).edit().putString("history", out.toString()).apply();
    }

    private static void mergeSrs(Context c, JSONObject incoming) throws Exception {
        if (incoming == null) {
            return;
        }
        for (Iterator<String> it = incoming.keys(); it.hasNext(); ) {
            String bankId = it.next();
            JSONObject fromFile = incoming.getJSONObject(bankId);
            Map<String, Card> mine = cards(c, bankId);
            for (Iterator<String> qit = fromFile.keys(); qit.hasNext(); ) {
                String qid = qit.next();
                JSONObject j = fromFile.getJSONObject(qid);
                Card altrui = new Card();
                altrui.box = j.optInt("box");
                altrui.due = j.optLong("due");
                altrui.last = j.optLong("last");
                altrui.ok = j.optInt("ok");
                altrui.ko = j.optInt("ko");
                Card mia = mine.get(qid);
                if (mia == null || altrui.last > mia.last) {
                    mine.put(qid, altrui);
                }
            }
            saveCards(c, bankId, mine);
        }
    }

    private static void mergeStringMap(Context c, String prefix, JSONObject incoming)
            throws Exception {
        if (incoming == null) {
            return;
        }
        for (Iterator<String> it = incoming.keys(); it.hasNext(); ) {
            String bankId = it.next();
            JSONObject fromFile = incoming.getJSONObject(bankId);
            JSONObject mine = new JSONObject(prefs(c).getString(prefix + bankId, "{}"));
            for (Iterator<String> qit = fromFile.keys(); qit.hasNext(); ) {
                String qid = qit.next();
                if (!mine.has(qid)) {
                    mine.put(qid, fromFile.get(qid));
                }
            }
            prefs(c).edit().putString(prefix + bankId, mine.toString()).apply();
        }
    }

    private static void mergeTagMap(Context c, JSONObject incoming) throws Exception {
        if (incoming == null) {
            return;
        }
        for (Iterator<String> it = incoming.keys(); it.hasNext(); ) {
            String bankId = it.next();
            JSONObject fromFile = incoming.getJSONObject(bankId);
            JSONObject mine = new JSONObject(prefs(c).getString("tag_" + bankId, "{}"));
            for (Iterator<String> qit = fromFile.keys(); qit.hasNext(); ) {
                String qid = qit.next();
                Set<String> unione = new java.util.LinkedHashSet<>(
                        toList(mine.optJSONArray(qid) == null
                                ? "[]" : mine.getJSONArray(qid).toString()));
                unione.addAll(toList(fromFile.getJSONArray(qid).toString()));
                mine.put(qid, new JSONArray(new ArrayList<>(unione)));
            }
            prefs(c).edit().putString("tag_" + bankId, mine.toString()).apply();
        }
    }

    private static void mergeFlags(Context c, JSONObject incoming) throws Exception {
        if (incoming == null) {
            return;
        }
        for (Iterator<String> it = incoming.keys(); it.hasNext(); ) {
            String bankId = it.next();
            Set<String> unione = flagIds(c, bankId);
            unione.addAll(toList(incoming.getJSONArray(bankId).toString()));
            prefs(c).edit().putString("flag_" + bankId,
                    new JSONArray(new ArrayList<>(unione)).toString()).apply();
        }
    }

    // --------------------------------------------------------------- utili

    private static List<String> toList(String json) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                out.add(a.getString(i));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Mappa vuota nell'ordine di inserimento, comoda per i riepiloghi. */
    static <K, V> Map<K, V> ordered() {
        return new LinkedHashMap<>();
    }
}
