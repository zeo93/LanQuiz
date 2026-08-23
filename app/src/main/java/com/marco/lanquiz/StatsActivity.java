package com.marco.lanquiz;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Statistiche: quanto hai fatto, dove vai peggio, come stai migliorando. */
public class StatsActivity extends AppCompatActivity {

    private static final SimpleDateFormat WHEN =
            new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ITALY);

    private LinearLayout box;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_page);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.statistiche);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        box = findViewById(R.id.container);
        Ui.limitWidth(box);
        render();
    }

    private void render() {
        box.removeAllViews();
        List<Store.Result> history = Store.history(this);

        if (history.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.nessuna_statistica);
            empty.setTextColor(getColor(R.color.muted));
            empty.setPadding(0, Ui.dp(this, 24), 0, 0);
            box.addView(empty);
            return;
        }

        int attempts = history.size();
        int questions = 0;
        int correct = 0;
        int seconds = 0;
        for (Store.Result r : history) {
            questions += r.total;
            correct += r.correct;
            seconds += r.seconds;
        }
        int average = questions > 0 ? Math.round(correct * 100f / questions) : 0;
        int pass = Store.passPct(this);

        LinearLayout summary = Ui.card(this, box);
        Ui.text(this, summary, getString(R.string.riepilogo_generale), 17,
                getColor(R.color.accent), true);
        Ui.row(this, summary, getString(R.string.tentativi_totali), String.valueOf(attempts), 0);
        Ui.row(this, summary, getString(R.string.domande_risposte), String.valueOf(questions), 0);
        Ui.row(this, summary, getString(R.string.media_generale), average + "%",
                Ui.scoreColor(this, average, pass));
        Ui.row(this, summary, getString(R.string.tempo_totale), Ui.duration(seconds), 0);

        // --- per banco -----------------------------------------------------
        Map<String, List<Store.Result>> perBank = new LinkedHashMap<>();
        for (Store.Result r : history) {
            List<Store.Result> list = perBank.get(r.bankId);
            if (list == null) {
                list = new ArrayList<>();
                perBank.put(r.bankId, list);
            }
            list.add(r);
        }

        Ui.sectionTitle(this, box, getString(R.string.per_banco));
        for (Map.Entry<String, List<Store.Result>> e : perBank.entrySet()) {
            List<Store.Result> list = e.getValue();
            int sumPct = 0;
            int best = 0;
            for (Store.Result r : list) {
                sumPct += r.percent();
                best = Math.max(best, r.percent());
            }
            int avg = sumPct / list.size();
            int wrong = Store.dueIds(this, e.getKey()).size();

            LinearLayout card = Ui.card(this, box);
            Ui.text(this, card, list.get(0).title, 16, getColor(R.color.on_surface), true);
            Ui.text(this, card, getString(R.string.stat_riga, list.size(), avg, best), 13,
                    getColor(R.color.muted), false);

            LinearProgressIndicator bar = new LinearProgressIndicator(this);
            bar.setMax(100);
            bar.setProgress(avg);
            bar.setTrackThickness(Ui.dp(this, 8));
            bar.setTrackCornerRadius(Ui.dp(this, 4));
            bar.setIndicatorColor(Ui.scoreColor(this, avg, pass));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 8);
            card.addView(bar, lp);

            if (wrong > 0) {
                Ui.text(this, card, getString(R.string.in_scadenza_oggi, wrong), 13,
                        getColor(R.color.warn), false);
            }
        }

        // --- per argomento --------------------------------------------------
        // Riempito dopo: leggere tutti i banchi costa, e non deve bloccare lo scorrimento.
        LinearLayout perTag = new LinearLayout(this);
        perTag.setOrientation(LinearLayout.VERTICAL);
        box.addView(perTag, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        loadTagStats(perTag, pass);

        // --- ultimi tentativi ----------------------------------------------
        Ui.sectionTitle(this, box, getString(R.string.ultimi_tentativi));
        LinearLayout recent = Ui.card(this, box);
        int shown = 0;
        for (Store.Result r : history) {
            if (shown++ >= 15) {
                break;
            }
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Ui.text(this, left, r.title, 14, getColor(R.color.on_surface), false);
            Ui.text(this, left, WHEN.format(new Date(r.time)) + " · "
                    + (Store.MODE_EXAM.equals(r.mode)
                    ? getString(R.string.modalita_esame) : getString(R.string.modalita_studio))
                    + " · " + Ui.duration(r.seconds), 12, getColor(R.color.muted), false);

            TextView pct = new TextView(this);
            pct.setText(r.percent() + "%");
            pct.setTextSize(16);
            pct.setTypeface(null, android.graphics.Typeface.BOLD);
            pct.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            pct.setTextColor(Ui.scoreColor(this, r.percent(), pass));

            line.addView(left);
            line.addView(pct);
            recent.addView(line);
        }

        // --- azioni ---------------------------------------------------------
        MaterialButton export = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        export.setText(R.string.esporta_csv);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = Ui.dp(this, 16);
        export.setLayoutParams(elp);
        export.setOnClickListener(v -> exportCsv(history));
        box.addView(export);

        MaterialButton reset = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        reset.setText(R.string.azzera_statistiche);
        reset.setTextColor(getColor(R.color.ko));
        reset.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.ko)));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(this, 8);
        rlp.bottomMargin = Ui.dp(this, 24);
        reset.setLayoutParams(rlp);
        reset.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.azzera_conferma)
                        + "\n\n" + getString(R.string.azzera_nota))
                .setPositiveButton(R.string.azzera, (d, w) -> {
                    Store.clearHistory(this);
                    render();
                })
                .setNegativeButton(R.string.annulla, null)
                .show());
        box.addView(reset);
    }

    /** Quante risposte hai dato per ogni argomento e quante ne hai azzeccate. */
    private static class TagStat {
        int ok;
        int ko;

        int total() {
            return ok + ko;
        }

        int percent() {
            return total() > 0 ? Math.round(ok * 100f / total()) : 0;
        }
    }

    private void loadTagStats(LinearLayout target, int pass) {
        new Thread(() -> {
            Map<String, TagStat> perTag = new LinkedHashMap<>();
            for (Bank bank : Banks.all(this)) {
                Map<String, Store.Card> cards = Store.cards(this, bank.id);
                if (cards.isEmpty()) {
                    continue;
                }
                Map<String, List<String>> mine = Store.userTags(this, bank.id);
                for (Question q : Banks.load(this, bank)) {
                    Store.Card card = cards.get(q.id());
                    if (card == null) {
                        continue;
                    }
                    for (String tag : Store.tagsOf(q, mine.get(q.id()))) {
                        TagStat stat = perTag.get(tag);
                        if (stat == null) {
                            stat = new TagStat();
                            perTag.put(tag, stat);
                        }
                        stat.ok += card.ok;
                        stat.ko += card.ko;
                    }
                }
            }
            List<Map.Entry<String, TagStat>> sorted = new ArrayList<>(perTag.entrySet());
            // prima quelli che vanno peggio: sono quelli da ripassare
            Collections.sort(sorted, (a, b) ->
                    Integer.compare(a.getValue().percent(), b.getValue().percent()));
            runOnUiThread(() -> paintTagStats(target, sorted, pass));
        }).start();
    }

    private void paintTagStats(LinearLayout target,
                               List<Map.Entry<String, TagStat>> sorted, int pass) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        target.removeAllViews();
        Ui.sectionTitle(this, target, getString(R.string.per_argomento));
        if (sorted.isEmpty()) {
            LinearLayout card = Ui.card(this, target);
            Ui.text(this, card, getString(R.string.nessun_argomento), 14,
                    getColor(R.color.muted), false);
            return;
        }
        LinearLayout card = Ui.card(this, target);
        for (Map.Entry<String, TagStat> e : sorted) {
            TagStat stat = e.getValue();
            Ui.row(this, card, "#" + e.getKey(),
                    getString(R.string.argomento_riga, stat.total(), stat.percent()),
                    Ui.scoreColor(this, stat.percent(), pass));
        }
    }

    private void exportCsv(List<Store.Result> history) {
        StringBuilder sb = new StringBuilder("quiz;data;modalita;corrette;totale;percentuale;secondi\n");
        for (Store.Result r : history) {
            sb.append(r.title.replace(';', ',')).append(';')
                    .append(WHEN.format(new Date(r.time))).append(';')
                    .append(r.mode).append(';')
                    .append(r.correct).append(';')
                    .append(r.total).append(';')
                    .append(r.percent()).append(';')
                    .append(r.seconds).append('\n');
        }
        try {
            File dir = new File(getCacheDir(), "condivisi");
            dir.mkdirs();
            File out = new File(dir, "lanquiz-statistiche.csv");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", out);
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                    .setType("text/csv")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    getString(R.string.esporta_csv)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.errore_download, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }
}
