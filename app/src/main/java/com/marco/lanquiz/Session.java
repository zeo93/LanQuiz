package com.marco.lanquiz;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Una sessione di quiz in corso: le domande estratte e le risposte date. */
public class Session {

    /** Da quali domande del banco pescare. */
    public enum Filter { TUTTE, SBAGLIATE, CONTRASSEGNATE }

    /** Impostazioni scelte in home prima di partire. */
    public static class Config {
        public String mode = Store.MODE_STUDY;
        public int count;          // 0 = tutte
        public int timerMinutes;   // 0 = nessun timer
        public boolean shuffleQuestions = true;
        public boolean shuffleAnswers = true;
        public Filter filter = Filter.TUTTE;

        public static Config fromPrefs(Context c) {
            Config cfg = new Config();
            cfg.mode = Store.mode(c);
            cfg.count = Store.count(c);
            cfg.timerMinutes = Store.timer(c);
            cfg.shuffleQuestions = Store.shuffleQuestions(c);
            cfg.shuffleAnswers = Store.shuffleAnswers(c);
            return cfg;
        }
    }

    /** Una domanda dentro la sessione, con l ordine delle risposte e cosa hai scelto. */
    public static class Item {
        public Question question;
        public List<Integer> order = new ArrayList<>();   // indici originali, nell ordine mostrato
        public Set<Integer> selected = new LinkedHashSet<>(); // indici originali scelti
        public boolean flagged;
        public boolean revealed;   // in modalita studio: risposta gia svelata

        public boolean answered() {
            return !selected.isEmpty();
        }

        public boolean right() {
            return answered() && selected.size() == question.correct.size()
                    && selected.containsAll(question.correct);
        }

        public boolean isCorrect(int originalIndex) {
            return question.correct.contains(originalIndex);
        }
    }

    public String bankId;
    public String bankTitle;
    public String mode = Store.MODE_STUDY;
    public List<Item> items = new ArrayList<>();
    public int index;
    public int timerSeconds;      // durata totale, 0 = nessun timer
    public int secondsLeft;       // quanto resta (aggiornato dall activity)
    public int elapsedSeconds;    // tempo consumato, per il riepilogo
    public boolean timedOut;

    public boolean exam() {
        return Store.MODE_EXAM.equals(mode);
    }

    public Item current() {
        return items.isEmpty() ? null : items.get(Math.max(0, Math.min(index, items.size() - 1)));
    }

    public int answeredCount() {
        int n = 0;
        for (Item it : items) {
            if (it.answered()) {
                n++;
            }
        }
        return n;
    }

    public int correctCount() {
        int n = 0;
        for (Item it : items) {
            if (it.right()) {
                n++;
            }
        }
        return n;
    }

    public int percent() {
        return items.isEmpty() ? 0 : Math.round(correctCount() * 100f / items.size());
    }

    public List<String> rightIds() {
        List<String> out = new ArrayList<>();
        for (Item it : items) {
            if (it.right()) {
                out.add(it.question.id());
            }
        }
        return out;
    }

    /** Anche le domande lasciate in bianco contano come da ripassare. */
    public List<String> wrongIds() {
        List<String> out = new ArrayList<>();
        for (Item it : items) {
            if (!it.right()) {
                out.add(it.question.id());
            }
        }
        return out;
    }

    // ------------------------------------------------------------ creazione

    public static Session build(Context c, Bank bank, Config cfg) {
        List<Question> pool = new ArrayList<>(Banks.load(c, bank));
        if (cfg.filter == Filter.SBAGLIATE) {
            pool = keepOnly(pool, Store.wrongIds(c, bank.id));
        } else if (cfg.filter == Filter.CONTRASSEGNATE) {
            pool = keepOnly(pool, Store.flagIds(c, bank.id));
        }
        if (cfg.shuffleQuestions) {
            Collections.shuffle(pool);
        }
        if (cfg.count > 0 && cfg.count < pool.size()) {
            pool = new ArrayList<>(pool.subList(0, cfg.count));
        }

        Session s = new Session();
        s.bankId = bank.id;
        s.bankTitle = bank.title;
        s.mode = cfg.mode;
        s.timerSeconds = cfg.timerMinutes * 60;
        s.secondsLeft = s.timerSeconds;
        Set<String> flagged = Store.flagIds(c, bank.id);
        for (Question q : pool) {
            Item it = new Item();
            it.question = q;
            for (int i = 0; i < q.answers.size(); i++) {
                it.order.add(i);
            }
            if (cfg.shuffleAnswers) {
                Collections.shuffle(it.order);
            }
            it.flagged = flagged.contains(q.id());
            s.items.add(it);
        }
        return s;
    }

    private static List<Question> keepOnly(List<Question> pool, Set<String> ids) {
        List<Question> out = new ArrayList<>();
        for (Question q : pool) {
            if (ids.contains(q.id())) {
                out.add(q);
            }
        }
        return out;
    }

    /** Quante domande resterebbero applicando questo filtro. */
    public static int poolSize(Context c, Bank bank, Filter filter) {
        List<Question> pool = Banks.load(c, bank);
        if (filter == Filter.SBAGLIATE) {
            return keepOnly(pool, Store.wrongIds(c, bank.id)).size();
        }
        if (filter == Filter.CONTRASSEGNATE) {
            return keepOnly(pool, Store.flagIds(c, bank.id)).size();
        }
        return pool.size();
    }

    /** Una nuova sessione con le sole domande sbagliate in quella appena finita. */
    public static Session retryWrong(Session done) {
        Session s = new Session();
        s.bankId = done.bankId;
        s.bankTitle = done.bankTitle;
        s.mode = done.mode;
        s.timerSeconds = 0;
        for (Item it : done.items) {
            if (!it.right()) {
                Item copy = new Item();
                copy.question = it.question;
                copy.order = new ArrayList<>(it.order);
                Collections.shuffle(copy.order);
                copy.flagged = it.flagged;
                s.items.add(copy);
            }
        }
        return s;
    }

    // ------------------------------------------------------- salva/riprendi

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("bank", bankId);
            o.put("title", bankTitle);
            o.put("mode", mode);
            o.put("index", index);
            o.put("timer", timerSeconds);
            o.put("left", secondsLeft);
            o.put("elapsed", elapsedSeconds);
            JSONArray arr = new JSONArray();
            for (Item it : items) {
                JSONObject j = new JSONObject();
                j.put("q", it.question.toJson());
                j.put("order", new JSONArray(it.order));
                j.put("sel", new JSONArray(new ArrayList<>(it.selected)));
                j.put("flag", it.flagged);
                j.put("rev", it.revealed);
                arr.put(j);
            }
            o.put("items", arr);
            return o.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static Session fromJson(String json) {
        try {
            JSONObject o = new JSONObject(json);
            Session s = new Session();
            s.bankId = o.optString("bank");
            s.bankTitle = o.optString("title");
            s.mode = o.optString("mode", Store.MODE_STUDY);
            s.index = o.optInt("index");
            s.timerSeconds = o.optInt("timer");
            s.secondsLeft = o.optInt("left");
            s.elapsedSeconds = o.optInt("elapsed");
            JSONArray arr = o.optJSONArray("items");
            if (arr == null || arr.length() == 0) {
                return null;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.getJSONObject(i);
                Item it = new Item();
                it.question = Question.fromJson(j.getJSONObject("q"));
                JSONArray ord = j.optJSONArray("order");
                for (int k = 0; ord != null && k < ord.length(); k++) {
                    it.order.add(ord.getInt(k));
                }
                if (it.order.isEmpty()) {
                    for (int k = 0; k < it.question.answers.size(); k++) {
                        it.order.add(k);
                    }
                }
                JSONArray sel = j.optJSONArray("sel");
                for (int k = 0; sel != null && k < sel.length(); k++) {
                    it.selected.add(sel.getInt(k));
                }
                it.flagged = j.optBoolean("flag");
                it.revealed = j.optBoolean("rev");
                s.items.add(it);
            }
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** Le sessioni vive vengono passate fra activity qui, non nell Intent:
     *  serializzarle a ogni rotazione dello schermo sarebbe solo peso inutile. */
    private static final java.util.Map<String, Session> LIVE = new java.util.HashMap<>();

    public static String park(Session s) {
        String key = "s" + System.nanoTime();
        LIVE.clear(); // ne serve una sola alla volta
        LIVE.put(key, s);
        return key;
    }

    public static Session pick(String key) {
        return key == null ? null : LIVE.get(key);
    }

    static Set<Integer> setOf(int... values) {
        Set<Integer> s = new HashSet<>();
        for (int v : values) {
            s.add(v);
        }
        return s;
    }
}
