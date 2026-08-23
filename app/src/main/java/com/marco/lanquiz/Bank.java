package com.marco.lanquiz;

import java.util.List;
import java.util.Locale;

/** Un banco di domande: un file di testo, preinstallato o importato. */
public class Bank {

    public final String id;        // nome del file, es. "Google_Leader_01.txt"
    public final String title;     // titolo leggibile, es. "Google Leader 01"
    public final String category;  // gruppo in home, es. "Google Leader"
    public final boolean bundled;  // true = arriva dagli asset dell'app
    public int count;              // numero di domande valide
    public List<Question> questions; // riempito solo quando serve

    public Bank(String id, boolean bundled) {
        this.id = id;
        this.bundled = bundled;
        String stem = id.replaceFirst("\\.[A-Za-z0-9]+$", "");
        this.title = stem.replace('_', ' ').trim();
        this.category = categoryOf(stem);
    }

    /**
     * Il gruppo si ricava dal nome del file togliendo i suffissi di numerazione:
     * Google_Leader_00_full e Google_Leader_03 finiscono entrambi in
     * "Google Leader". Un nome senza famiglia riconoscibile va in "Altri quiz".
     */
    static String categoryOf(String stem) {
        String[] parts = stem.split("[_\\-\\s]+");
        int end = parts.length;
        while (end > 0) {
            String p = parts[end - 1].toLowerCase(Locale.ROOT);
            if (p.matches("\\d+[a-z]?") || p.equals("full") || p.equals("part")
                    || p.matches("v\\d+")) {
                end--;
            } else {
                break;
            }
        }
        if (end < 2) {
            return "Altri quiz";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
