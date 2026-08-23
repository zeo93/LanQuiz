package com.marco.lanquiz;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Una domanda con le sue risposte. Può avere più di una risposta corretta. */
public class Question {

    public final String text;
    public final List<String> answers;
    /** Indici (in {@link #answers}) delle risposte corrette: almeno uno. */
    public final List<Integer> correct;
    /** Spiegazione facoltativa, mostrata dopo la risposta. Può essere vuota. */
    public final String explanation;

    private String id;

    public Question(String text, List<String> answers, List<Integer> correct, String explanation) {
        this.text = text;
        this.answers = answers;
        this.correct = correct;
        this.explanation = explanation == null ? "" : explanation;
    }

    /** Identificatore stabile: dipende solo dal testo, così le statistiche
     *  sopravvivono al rimescolamento delle risposte e ai reimport del file. */
    public String id() {
        if (id == null) {
            id = hash(text.trim().replaceAll("\\s+", " ").toLowerCase());
        }
        return id;
    }

    public boolean multi() {
        return correct.size() > 1;
    }

    static String hash(String s) {
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

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("q", text);
        o.put("a", new JSONArray(answers));
        o.put("correct", new JSONArray(correct));
        if (!explanation.isEmpty()) {
            o.put("explanation", explanation);
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
        return new Question(o.optString("q"), answers, correct, o.optString("explanation", ""));
    }
}
