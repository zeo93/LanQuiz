package com.marco.lanquiz;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lettore dei banchi di domande.
 *
 * <p>Formato storico (quello dell'app Flask), una domanda per riga:
 * <pre>domanda;risposta esatta;risposta errata;risposta errata;…</pre>
 *
 * <p>Estensioni compatibili all'indietro:
 * <ul>
 *   <li>una risposta preceduta da <code>*</code> è corretta: così una domanda
 *       può averne più di una. Se nessuna è marcata vale la regola storica
 *       (la prima risposta è quella esatta). Per una risposta che inizia
 *       davvero con un asterisco si scrive <code>\*</code>.</li>
 *   <li>un campo che inizia con <code>##</code> è la spiegazione della domanda,
 *       mostrata dopo la risposta.</li>
 *   <li>un campo che inizia con <code>@</code> elenca gli argomenti della
 *       domanda, separati da spazi o virgole: servono alle statistiche per
 *       tema e a ripassare un argomento alla volta.</li>
 *   <li>le righe che iniziano con <code>#</code> sono commenti.</li>
 *   <li>separatore: punto e virgola; in mancanza si prova tabulazione e virgola,
 *       così anche i CSV esportati da un foglio di calcolo funzionano.</li>
 *   <li>un file che inizia con <code>{</code> o <code>[</code> è JSON
 *       (il formato di esportazione dell'app).</li>
 * </ul>
 */
public final class Parser {

    private Parser() {
    }

    public static List<Question> parse(String content) {
        List<Question> out = new ArrayList<>();
        if (content == null) {
            return out;
        }
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            List<Question> json = parseJson(trimmed);
            if (!json.isEmpty()) {
                return json;
            }
        }
        for (String raw : content.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Question q = parseLine(line);
            if (q != null) {
                out.add(q);
            }
        }
        return out;
    }

    private static Question parseLine(String line) {
        String sep = line.contains(";") ? ";" : (line.contains("\t") ? "\t" : ",");
        String[] parts = line.split(java.util.regex.Pattern.quote(sep), -1);
        if (parts.length < 3) {
            return null;
        }
        String text = parts[0].trim();
        if (text.isEmpty()) {
            return null;
        }
        List<String> answers = new ArrayList<>();
        List<Integer> correct = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        String explanation = "";
        for (int i = 1; i < parts.length; i++) {
            String f = parts[i].trim();
            if (f.isEmpty()) {
                continue;
            }
            if (f.startsWith("##")) {
                explanation = f.substring(2).trim();
                continue;
            }
            if (f.startsWith("@")) {
                // solo la virgola separa: così "cloud storage" resta un argomento solo
                for (String raw : f.substring(1).split("[,;@]+")) {
                    String tag = normalizeTag(raw);
                    if (!tag.isEmpty() && !tags.contains(tag)) {
                        tags.add(tag);
                    }
                }
                continue;
            }
            boolean isCorrect = false;
            if (f.startsWith("*")) {
                isCorrect = true;
                f = f.substring(1).trim();
            } else if (f.startsWith("\\*")) {
                f = f.substring(1);
            }
            if (f.isEmpty()) {
                continue;
            }
            if (isCorrect) {
                correct.add(answers.size());
            }
            answers.add(f);
        }
        if (answers.size() < 2) {
            return null;
        }
        if (correct.isEmpty()) {
            correct.add(0); // regola storica: la prima risposta è quella esatta
        }
        return new Question(text, answers, correct, explanation, tags);
    }

    /** Gli argomenti si confrontano sempre in minuscolo e senza spazi. */
    public static String normalizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        return tag.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}+#._-]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private static List<Question> parseJson(String content) {
        List<Question> out = new ArrayList<>();
        try {
            JSONArray arr;
            if (content.startsWith("[")) {
                arr = new JSONArray(content);
            } else {
                arr = new JSONObject(content).optJSONArray("questions");
            }
            if (arr == null) {
                return out;
            }
            for (int i = 0; i < arr.length(); i++) {
                Question q = Question.fromJson(arr.getJSONObject(i));
                if (q.answers.size() >= 2 && !q.text.trim().isEmpty()) {
                    out.add(q);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Riscrive le domande nel formato a punto e virgola (per esportare e condividere). */
    public static String toText(List<Question> questions) {
        StringBuilder sb = new StringBuilder();
        for (Question q : questions) {
            sb.append(q.text.replace(";", ","));
            for (int i = 0; i < q.answers.size(); i++) {
                String a = q.answers.get(i).replace(";", ",");
                sb.append(';');
                if (q.correct.contains(i)) {
                    sb.append('*');
                } else if (a.startsWith("*")) {
                    sb.append('\\');
                }
                sb.append(a);
            }
            for (String tag : q.tags) {
                sb.append(";@").append(tag);
            }
            if (!q.explanation.isEmpty()) {
                sb.append(";##").append(q.explanation.replace(";", ","));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
