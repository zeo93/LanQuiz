package com.marco.lanquiz;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Home: impostazioni della sessione e catalogo dei quiz. */
public class MainActivity extends AppCompatActivity {

    /** Un banco con i numeri che servono a mostrarlo in lista. */
    private static class Row {
        Bank bank;
        int best = -1;
        int wrong;
        int flags;
    }

    private LinearLayout banksBox;
    private TextInputEditText search;
    private final List<Row> rows = new ArrayList<>();
    private String query = "";

    private ActivityResultLauncher<String[]> pickFile;
    private ActivityResultLauncher<String> askNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));

        banksBox = findViewById(R.id.banks);
        search = findViewById(R.id.input_search);
        View contentRoot = findViewById(R.id.content_root);
        if (contentRoot != null) {
            Ui.limitWidth(contentRoot);
        }

        pickFile = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                importInBackground(uri, null, null);
            }
        });
        askNotifications = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { });

        bindSettings();
        bindSearch();
        bindResume();

        handleIncoming(getIntent());
        requestNotificationsIfNeeded();
        UpdateChecker.checkOnStartup(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        bindResume();
    }

    // ------------------------------------------------------------ menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_stats) {
            startActivity(new Intent(this, StatsActivity.class));
            return true;
        }
        if (id == R.id.menu_import) {
            showImportMenu();
            return true;
        }
        if (id == R.id.menu_format) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.formato_titolo)
                    .setMessage(R.string.formato_testo)
                    .setPositiveButton(R.string.chiudi, null)
                    .show();
            return true;
        }
        if (id == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // -------------------------------------------- impostazioni sessione

    private void bindSettings() {
        ChipGroup modeGroup = findViewById(R.id.group_mode);
        Chip studio = findViewById(R.id.chip_studio);
        Chip esame = findViewById(R.id.chip_esame);
        TextView modeHint = findViewById(R.id.mode_hint);

        boolean exam = Store.MODE_EXAM.equals(Store.mode(this));
        (exam ? esame : studio).setChecked(true);
        modeHint.setText(exam ? R.string.modalita_esame_desc : R.string.modalita_studio_desc);
        modeGroup.setOnCheckedStateChangeListener((g, ids) -> {
            boolean isExam = g.getCheckedChipId() == R.id.chip_esame;
            Store.set(this, Store.MODE, isExam ? Store.MODE_EXAM : Store.MODE_STUDY);
            modeHint.setText(isExam ? R.string.modalita_esame_desc : R.string.modalita_studio_desc);
        });

        ChipGroup countGroup = findViewById(R.id.group_count);
        Chip countCustom = findViewById(R.id.chip_count_custom);
        applyCountSelection(countGroup, countCustom, Store.count(this));
        countGroup.setOnCheckedStateChangeListener((g, ids) -> {
            int checked = g.getCheckedChipId();
            if (checked == R.id.chip_count_custom) {
                askNumber(R.string.quante_domande, Math.max(1, Store.count(this)), value -> {
                    Store.set(this, Store.COUNT, value);
                    applyCountSelection(countGroup, countCustom, value);
                });
            } else if (checked == R.id.chip_all) {
                Store.set(this, Store.COUNT, 0);
                countCustom.setText(R.string.personalizza);
            } else if (checked == R.id.chip_10) {
                Store.set(this, Store.COUNT, 10);
                countCustom.setText(R.string.personalizza);
            } else if (checked == R.id.chip_25) {
                Store.set(this, Store.COUNT, 25);
                countCustom.setText(R.string.personalizza);
            } else if (checked == R.id.chip_50) {
                // "Prova esame" replica la scorciatoia dell'app originale:
                // 50 domande, 90 minuti e nessun riscontro fino alla consegna.
                Store.set(this, Store.COUNT, 50);
                Store.set(this, Store.TIMER, 90);
                Store.set(this, Store.MODE, Store.MODE_EXAM);
                countCustom.setText(R.string.personalizza);
                esame.setChecked(true);
                applyTimerSelection(findViewById(R.id.group_timer),
                        findViewById(R.id.chip_timer_custom), 90);
            }
        });

        ChipGroup timerGroup = findViewById(R.id.group_timer);
        Chip timerCustom = findViewById(R.id.chip_timer_custom);
        applyTimerSelection(timerGroup, timerCustom, Store.timer(this));
        timerGroup.setOnCheckedStateChangeListener((g, ids) -> {
            int checked = g.getCheckedChipId();
            if (checked == R.id.chip_timer_custom) {
                askNumber(R.string.timer_personalizzato, Math.max(1, Store.timer(this)), value -> {
                    Store.set(this, Store.TIMER, value);
                    applyTimerSelection(timerGroup, timerCustom, value);
                });
            } else {
                int minutes = checked == R.id.chip_t15 ? 15
                        : checked == R.id.chip_t30 ? 30
                        : checked == R.id.chip_t90 ? 90 : 0;
                Store.set(this, Store.TIMER, minutes);
                timerCustom.setText(R.string.personalizza);
            }
        });

        MaterialSwitch shuffleQ = findViewById(R.id.switch_shuffle_q);
        shuffleQ.setChecked(Store.shuffleQuestions(this));
        shuffleQ.setOnCheckedChangeListener((b, v) -> Store.set(this, Store.SHUFFLE_Q, v));

        MaterialSwitch shuffleA = findViewById(R.id.switch_shuffle_a);
        shuffleA.setChecked(Store.shuffleAnswers(this));
        shuffleA.setOnCheckedChangeListener((b, v) -> Store.set(this, Store.SHUFFLE_A, v));
    }

    private void applyCountSelection(ChipGroup group, Chip custom, int value) {
        int id = value == 0 ? R.id.chip_all
                : value == 10 ? R.id.chip_10
                : value == 25 ? R.id.chip_25
                : value == 50 ? R.id.chip_50 : R.id.chip_count_custom;
        if (id == R.id.chip_count_custom) {
            custom.setText(String.valueOf(value));
        } else {
            custom.setText(R.string.personalizza);
        }
        group.check(id);
    }

    private void applyTimerSelection(ChipGroup group, Chip custom, int minutes) {
        int id = minutes == 0 ? R.id.chip_t0
                : minutes == 15 ? R.id.chip_t15
                : minutes == 30 ? R.id.chip_t30
                : minutes == 90 ? R.id.chip_t90 : R.id.chip_timer_custom;
        if (id == R.id.chip_timer_custom) {
            custom.setText(getString(R.string.minuti_n, minutes));
        } else {
            custom.setText(R.string.personalizza);
        }
        group.check(id);
    }

    private void bindSearch() {
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                render();
            }
        });
    }

    // ------------------------------------------------------ quiz interrotto

    private void bindResume() {
        MaterialCardView card = findViewById(R.id.resume_card);
        String json = Store.loadResume(this);
        Session parked = json == null ? null : Session.fromJson(json);
        if (parked == null) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.resume_text)).setText(getString(R.string.riprendi_msg,
                parked.bankTitle, parked.index + 1, parked.items.size()));
        findViewById(R.id.btn_resume).setOnClickListener(v -> {
            Store.clearResume(this);
            openQuiz(parked);
        });
        findViewById(R.id.btn_discard).setOnClickListener(v -> {
            Store.clearResume(this);
            card.setVisibility(View.GONE);
        });
    }

    // ------------------------------------------------------------- catalogo

    private void load() {
        new Thread(() -> {
            List<Row> fresh = new ArrayList<>();
            for (Bank b : Banks.all(this)) {
                Row r = new Row();
                r.bank = b;
                r.wrong = Store.wrongIds(this, b.id).size();
                r.flags = Store.flagIds(this, b.id).size();
                for (Store.Result res : Store.historyFor(this, b.id)) {
                    r.best = Math.max(r.best, res.percent());
                }
                fresh.add(r);
            }
            runOnUiThread(() -> {
                rows.clear();
                rows.addAll(fresh);
                render();
            });
        }).start();
    }

    private void render() {
        if (banksBox == null) {
            return;
        }
        banksBox.removeAllViews();

        List<Bank> visible = new ArrayList<>();
        List<Row> visibleRows = new ArrayList<>();
        String q = query.toLowerCase(Locale.ITALY);
        for (Row r : rows) {
            if (q.isEmpty() || r.bank.title.toLowerCase(Locale.ITALY).contains(q)
                    || r.bank.category.toLowerCase(Locale.ITALY).contains(q)) {
                visible.add(r.bank);
                visibleRows.add(r);
            }
        }

        if (visible.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(rows.isEmpty() ? getString(R.string.nessun_quiz)
                    : getString(R.string.nessun_risultato_ricerca, query));
            empty.setTextColor(getColor(R.color.muted));
            empty.setPadding(0, Ui.dp(this, 24), 0, 0);
            banksBox.addView(empty);
            return;
        }

        int columns = getResources().getInteger(R.integer.bank_columns);
        for (Map.Entry<String, List<Bank>> group : Banks.byCategory(visible).entrySet()) {
            Ui.sectionTitle(this, banksBox, group.getKey());
            LinearLayout line = null;
            int inLine = 0;
            for (Bank bank : group.getValue()) {
                if (columns == 1 || inLine == 0) {
                    line = new LinearLayout(this);
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    banksBox.addView(line, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                addBankCard(line, findRow(visibleRows, bank), columns);
                inLine = columns == 1 ? 0 : (inLine + 1) % columns;
            }
            if (columns > 1 && inLine != 0 && line != null) {
                // riempie la riga incompleta, così le card non si allargano
                for (int i = inLine; i < columns; i++) {
                    View spacer = new View(this);
                    line.addView(spacer, new LinearLayout.LayoutParams(0,
                            1, 1f));
                }
            }
        }
    }

    private Row findRow(List<Row> list, Bank bank) {
        for (Row r : list) {
            if (r.bank == bank) {
                return r;
            }
        }
        return null;
    }

    private void addBankCard(LinearLayout line, Row row, int columns) {
        if (row == null) {
            return;
        }
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                columns == 1 ? ViewGroup.LayoutParams.MATCH_PARENT : 0,
                ViewGroup.LayoutParams.WRAP_CONTENT, columns == 1 ? 0f : 1f);
        int m = Ui.dp(this, 4);
        lp.setMargins(m, m, m, m);
        card.setLayoutParams(lp);
        card.setRadius(Ui.dp(this, 14));
        card.setCardElevation(Ui.dp(this, 1));
        card.setContentPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> onBankClicked(row));
        card.setOnLongClickListener(v -> {
            showBankMenu(row.bank);
            return true;
        });

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        card.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(row.bank.title);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(getColor(R.color.on_surface));
        col.addView(title);

        List<String> bits = new ArrayList<>();
        bits.add(row.bank.count == 1 ? getString(R.string.una_domanda)
                : getString(R.string.n_domande, row.bank.count));
        if (row.best >= 0) {
            bits.add(getString(R.string.record_pct, row.best));
        }
        if (row.wrong > 0) {
            bits.add(getString(R.string.da_ripassare, row.wrong));
        }
        if (row.flags > 0) {
            bits.add(getString(R.string.contrassegnate_n, row.flags));
        }
        TextView sub = new TextView(this);
        sub.setText(TextUtils.join(" · ", bits));
        sub.setTextSize(13);
        sub.setTextColor(getColor(R.color.muted));
        col.addView(sub);

        line.addView(card);
    }

    // -------------------------------------------------------- avvio del quiz

    private void onBankClicked(Row row) {
        if (row.bank.count == 0) {
            Toast.makeText(this, R.string.banco_vuoto, Toast.LENGTH_SHORT).show();
            return;
        }
        if (row.wrong == 0 && row.flags == 0) {
            start(row.bank, Session.Filter.TUTTE);
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Session.Filter> filters = new ArrayList<>();
        labels.add(getString(R.string.tutte_le_domande, row.bank.count));
        filters.add(Session.Filter.TUTTE);
        if (row.wrong > 0) {
            labels.add(getString(R.string.solo_sbagliate, row.wrong));
            filters.add(Session.Filter.SBAGLIATE);
        }
        if (row.flags > 0) {
            labels.add(getString(R.string.solo_contrassegnate, row.flags));
            filters.add(Session.Filter.CONTRASSEGNATE);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(row.bank.title)
                .setItems(labels.toArray(new String[0]),
                        (d, which) -> start(row.bank, filters.get(which)))
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    private void start(Bank bank, Session.Filter filter) {
        Session.Config cfg = Session.Config.fromPrefs(this);
        cfg.filter = filter;
        Session session = Session.build(this, bank, cfg);
        if (session.items.isEmpty()) {
            Toast.makeText(this, R.string.niente_da_ripassare, Toast.LENGTH_SHORT).show();
            return;
        }
        openQuiz(session);
    }

    private void openQuiz(Session session) {
        startActivity(new Intent(this, QuizActivity.class)
                .putExtra(QuizActivity.EXTRA_SESSION, Session.park(session)));
    }

    // --------------------------------------------------- gestione dei banchi

    private void showBankMenu(Bank bank) {
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.esporta));
        if (!bank.bundled) {
            actions.add(getString(R.string.rinomina));
        }
        actions.add(getString(bank.bundled ? R.string.nascondi : R.string.elimina));
        new MaterialAlertDialogBuilder(this)
                .setTitle(bank.title)
                .setItems(actions.toArray(new String[0]), (d, which) -> {
                    String choice = actions.get(which);
                    if (choice.equals(getString(R.string.esporta))) {
                        exportBank(bank);
                    } else if (choice.equals(getString(R.string.rinomina))) {
                        askText(R.string.rinomina, R.string.nome_del_quiz, bank.title, name -> {
                            if (Banks.rename(this, bank, name)) {
                                load();
                            } else {
                                Toast.makeText(this, R.string.rinomina_fallita,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        confirmDelete(bank);
                    }
                })
                .show();
    }

    private void confirmDelete(Bank bank) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(bank.bundled ? R.string.nascondi_conferma
                        : R.string.elimina_conferma, bank.title))
                .setPositiveButton(bank.bundled ? R.string.nascondi : R.string.elimina, (d, w) -> {
                    Banks.delete(this, bank);
                    load();
                })
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    private void exportBank(Bank bank) {
        try {
            File dir = new File(getCacheDir(), "condivisi");
            dir.mkdirs();
            File out = new File(dir, bank.id.endsWith(".txt") ? bank.id : bank.id + ".txt");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(Banks.read(this, bank).getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", out);
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    getString(R.string.condividi_quiz)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.import_fallito, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ importa

    private void showImportMenu() {
        String[] options = {
                getString(R.string.importa_da_file),
                getString(R.string.importa_da_testo),
                getString(R.string.importa_da_url),
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.importa)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        pickFile.launch(new String[]{"text/*", "application/json", "*/*"});
                    } else if (which == 1) {
                        askTwo(R.string.importa_da_testo, R.string.nome_del_quiz,
                                R.string.incolla_testo, (name, text) ->
                                        importInBackground(null, text, name));
                    } else {
                        askTwo(R.string.importa_da_url, R.string.nome_del_quiz,
                                R.string.indirizzo_web, (name, url) ->
                                        importInBackground(null, null, url, name));
                    }
                })
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    private void importInBackground(Uri uri, String text, String name) {
        importInBackground(uri, text, null, name);
    }

    private void importInBackground(Uri uri, String text, String url, String name) {
        new Thread(() -> {
            String message;
            try {
                String saved;
                if (uri != null) {
                    saved = Banks.importUri(this, uri, name);
                } else if (url != null && !url.trim().isEmpty()) {
                    saved = Banks.importUrl(this, url.trim(), name);
                } else {
                    saved = Banks.save(this, name, text == null ? "" : text);
                }
                Bank bank = new Bank(saved, false);
                int n = Banks.load(this, bank).size();
                message = getString(R.string.importato_n, bank.title, n);
            } catch (Exception e) {
                message = getString(R.string.import_fallito,
                        e.getMessage() == null ? "?" : e.getMessage());
            }
            String finalMessage = message;
            runOnUiThread(() -> {
                Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show();
                load();
            });
        }).start();
    }

    /** Un file di quiz aperto o condiviso da un'altra app finisce qui. */
    private void handleIncoming(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            importInBackground(intent.getData(), null, "quiz.txt");
            intent.setAction(null);
        } else if (Intent.ACTION_SEND.equals(action)) {
            Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (stream != null) {
                importInBackground(stream, null, "quiz.txt");
            } else if (text != null) {
                importInBackground(null, text, "quiz.txt");
            }
            intent.setAction(null);
        }
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // ------------------------------------------------------------- dialoghi

    private interface OnNumber {
        void take(int value);
    }

    private interface OnText {
        void take(String value);
    }

    private interface OnTwo {
        void take(String first, String second);
    }

    private void askNumber(int titleRes, int current, OnNumber cb) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        TextInputLayout wrap = v.findViewById(R.id.wrap);
        TextInputEditText input = v.findViewById(R.id.input);
        wrap.setHint(getString(titleRes));
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(current));
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setView(v)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    try {
                        cb.take(Math.max(0, Integer.parseInt(input.getText().toString().trim())));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    private void askText(int titleRes, int hintRes, String current, OnText cb) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        TextInputLayout wrap = v.findViewById(R.id.wrap);
        TextInputEditText input = v.findViewById(R.id.input);
        wrap.setHint(getString(hintRes));
        input.setText(current);
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setView(v)
                .setPositiveButton(R.string.salva, (d, w) -> {
                    String s = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!s.isEmpty()) {
                        cb.take(s);
                    }
                })
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    private void askTwo(int titleRes, int hint1, int hint2, OnTwo cb) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        TextInputLayout wrap = v.findViewById(R.id.wrap);
        TextInputEditText input = v.findViewById(R.id.input);
        TextInputLayout wrap2 = v.findViewById(R.id.wrap2);
        TextInputEditText input2 = v.findViewById(R.id.input2);
        wrap.setHint(getString(hint1));
        wrap2.setHint(getString(hint2));
        wrap2.setVisibility(View.VISIBLE);
        if (hint2 == R.string.incolla_testo) {
            input2.setSingleLine(false);
            input2.setMinLines(4);
            input2.setMaxLines(8);
            input2.setGravity(Gravity.TOP | Gravity.START);
        } else {
            input2.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setView(v)
                .setPositiveButton(R.string.importa_azione, (d, w) -> cb.take(
                        input.getText() == null ? "" : input.getText().toString().trim(),
                        input2.getText() == null ? "" : input2.getText().toString()))
                .setNegativeButton(R.string.annulla, null)
                .show();
    }
}
