package com.marco.lanquiz;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Una domanda con le sue risposte. Può avere più di una risposta corretta. */
public class Question {

    public final String text;
    public final List<String> answers;
    /** Indici (in {@link #answers}) delle risposte corrette: almeno uno. */
    public final List<Integer> correct;
    /** Spiegazione facoltativa, mostrata dopo la risposta. Può essere vuota. */
    public final String explanation;
    /** Argomenti scritti nel file con @tag. L'utente può aggiungerne altri. */
    public final List<String> tags;

    private String id;
    private String legacyId;

    public Question(String text, List<String> answers, List<Integer> correct,
                    String explanation, List<String> tags) {
        this.text = text;
        this.answers = answers;
        this.correct = correct;
        this.explanation = explanation == null ? "" : explanation;
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    /**
     * Identificatore stabile: dipende solo dal testo, così ripasso, note e
     * argomenti sopravvivono al rimescolamento delle risposte e ai reimport.
     *
     * <p>È FNV-1a a 64 bit sui byte UTF-8 del testo normalizzato, lo stesso
     * calcolo della web app (`qid` in docs/app.js): un backup travasato da un
     * dispositivo all'altro deve ritrovare le stesse domande.
     */
    public String id() {
        if (id == null) {
            id = fnv1a64(normalize(text));
        }
        return id;
    }

    /** L'id usato fino alla versione 1.0, solo per travasare i dati vecchi. */
    public String legacyId() {
        if (legacyId == null) {
            legacyId = sha1Short(normalize(text));
        }
        return legacyId;
    }

    static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static String fnv1a64(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        StringBuilder sb = new StringBuilder(Long.toHexString(h));
        while (sb.length() < 16) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    static String sha1Short(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    public boolean multi() {
        return correct.size() > 1;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("q", text);
        o.put("a", new JSONArray(answers));
        o.put("correct", new JSONArray(correct));
        if (!explanation.isEmpty()) {
            o.put("explanation", explanation);
        }
        if (!tags.isEmpty()) {
            o.put("tags", new JSONArray(tags));
        }
        return o;
    }

    public static Question fromJson(JSONObject o) throws JSONException {
        List<String> answers = new ArrayList<>();
        JSONArray a = o.optJSONArray("a");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                answers.add(a.getString(i));
            }
        }
        List<Integer> correct = new ArrayList<>();
        JSONArray c = o.optJSONArray("correct");
        if (c != null) {
            for (int i = 0; i < c.length(); i++) {
                correct.add(c.getInt(i));
            }
        }
        if (correct.isEmpty() && !answers.isEmpty()) {
            correct.add(0);
        }
        List<String> tags = new ArrayList<>();
        JSONArray t = o.optJSONArray("tags");
        if (t != null) {
            for (int i = 0; i < t.length(); i++) {
                tags.add(t.getString(i));
            }
        }
        return new Question(o.optString("q"), answers, correct,
                o.optString("explanation", ""), tags);
    }
}
