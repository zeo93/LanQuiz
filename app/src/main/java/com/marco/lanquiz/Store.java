package com.marco.lanquiz;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tutto cio che l app ricorda fra un avvio e l altro: preferenze, storico dei
 * tentativi, domande sbagliate, domande contrassegnate e quiz interrotti.
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

    // ------------------------------------------------- domande sbagliate

    /** Le domande che hai sbagliato almeno una volta e non hai ancora recuperato. */
    public static Set<String> wrongIds(Context c, String bankId) {
        return new HashSet<>(toList(prefs(c).getString("wrong_" + bankId, "[]")));
    }

    private static void setWrongIds(Context c, String bankId, Set<String> ids) {
        prefs(c).edit().putString("wrong_" + bankId,
                new JSONArray(new ArrayList<>(ids)).toString()).apply();
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

    /**
     * Registra un tentativo: aggiorna lo storico e la lista delle domande da
     * ripassare (una risposta esatta toglie la domanda dalla lista, una
     * sbagliata ce la mette).
     */
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

        Set<String> wrong = wrongIds(c, bankId);
        if (rightIds != null) {
            wrong.removeAll(rightIds);
        }
        if (wrongIds != null) {
            wrong.addAll(wrongIds);
        }
        setWrongIds(c, bankId, wrong);
    }

    public static List<Result> history(Context c) {
        List<Result> out = new ArrayList<>();
        try {
            JSONArray hist = new JSONArray(prefs(c).getString("history", "[]"));
            for (int i = 0; i < hist.length(); i++) {
                JSONObject o = hist.getJSONObject(i);
                Result r = new Result();
                r.bankId = o.optString("bank");
                r.title = o.optString("title");
                r.time = o.optLong("ts");
                r.correct = o.optInt("correct");
                r.total = o.optInt("total");
                r.mode = o.optString("mode", MODE_STUDY);
                r.seconds = o.optInt("seconds");
                out.add(r);
            }
        } catch (Exception ignored) {
        }
        Collections.reverse(out); // il piu recente per primo
        return out;
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

    public static void clearHistory(Context c) {
        SharedPreferences.Editor e = prefs(c).edit();
        e.remove("history");
        for (String k : prefs(c).getAll().keySet()) {
            if (k.startsWith("wrong_")) {
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
        e.remove("wrong_" + bankId);
        e.remove("flag_" + bankId);
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
        String wrong = p.getString("wrong_" + oldId, null);
        if (wrong != null) {
            e.putString("wrong_" + newId, wrong).remove("wrong_" + oldId);
        }
        String flags = p.getString("flag_" + oldId, null);
        if (flags != null) {
            e.putString("flag_" + newId, flags).remove("flag_" + oldId);
        }
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

    static List<String> asList(String... items) {
        return new ArrayList<>(Arrays.asList(items));
    }
}
