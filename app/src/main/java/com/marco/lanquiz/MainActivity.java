package com.marco.lanquiz;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Home: impostazioni della sessione e catalogo dei quiz. */
public class MainActivity extends AppCompatActivity {

    /** Un banco con i numeri che servono a mostrarlo in lista. */
    private static class Row {
        Bank bank;
        int best = -1;
        int due;        // domande in scadenza oggi
        int unseen;     // domande mai affrontate
        int assodate;   // dalla scatola 3 in su: non tornano prima di una settimana
        int flags;
        long nextDue;   // quando torna la prossima, se oggi non c'è nulla
        final List<String> tags = new ArrayList<>();

        int pctAssodate() {
            return bank.count > 0 ? Math.round(assodate * 100f / bank.count) : 0;
        }
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
        MaterialButtonToggleGroup modeGroup = findViewById(R.id.group_mode);
        TextView modeHint = findViewById(R.id.mode_hint);

        boolean exam = Store.MODE_EXAM.equals(Store.mode(this));
        modeGroup.check(exam ? R.id.chip_esame : R.id.chip_studio);
        modeHint.setText(exam ? R.string.modalita_esame_desc : R.string.modalita_studio_desc);
        modeGroup.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            boolean isExam = checkedId == R.id.chip_esame;
            Store.set(this, Store.MODE, isExam ? Store.MODE_EXAM : Store.MODE_STUDY);
            modeHint.setText(isExam ? R.string.modalita_esame_desc : R.string.modalita_studio_desc);
        });

        MaterialButtonToggleGroup countGroup = findViewById(R.id.group_count);
        MaterialButton countCustom = findViewById(R.id.chip_count_custom);
        MaterialButtonToggleGroup timerGroup = findViewById(R.id.group_timer);
        MaterialButton timerCustom = findViewById(R.id.chip_timer_custom);

        applyCountSelection(countGroup, countCustom, Store.count(this));
        countGroup.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.chip_count_custom) {
                askNumber(R.string.quante_domande, Math.max(1, Store.count(this)), value -> {
                    Store.set(this, Store.COUNT, value);
                    applyCountSelection(countGroup, countCustom, value);
                });
            } else if (checkedId == R.id.chip_all) {
                Store.set(this, Store.COUNT, 0);
                countCustom.setText(R.string.personalizza);
            } else if (checkedId == R.id.chip_10) {
                Store.set(this, Store.COUNT, 10);
                countCustom.setText(R.string.personalizza);
            } else if (checkedId == R.id.chip_25) {
                Store.set(this, Store.COUNT, 25);
                countCustom.setText(R.string.personalizza);
            } else if (checkedId == R.id.chip_50) {
                Store.set(this, Store.COUNT, 50);
                countCustom.setText(R.string.personalizza);
            }
        });

        applyTimerSelection(timerGroup, timerCustom, Store.timer(this));
        timerGroup.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.chip_timer_custom) {
                askNumber(R.string.timer_personalizzato, Math.max(1, Store.timer(this)), value -> {
                    Store.set(this, Store.TIMER, value);
                    applyTimerSelection(timerGroup, timerCustom, value);
                });
            } else {
                int minutes = checkedId == R.id.chip_t15 ? 15
                        : checkedId == R.id.chip_t30 ? 30
                        : checkedId == R.id.chip_t90 ? 90 : 0;
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

    private void applyCountSelection(MaterialButtonToggleGroup group, MaterialButton custom,
                                     int value) {
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

    private void applyTimerSelection(MaterialButtonToggleGroup group, MaterialButton custom,
                                     int minutes) {
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
                r.flags = Store.flagIds(this, b.id).size();
                r.due = Session.poolSize(this, b, Session.Filter.DA_RIPASSARE);
                r.unseen = Session.poolSize(this, b, Session.Filter.MAI_VISTE);
                for (Store.Result res : Store.historyFor(this, b.id)) {
                    r.best = Math.max(r.best, res.percent());
                }
                List<String> ids = new ArrayList<>();
                Map<String, List<String>> mine = Store.userTags(this, b.id);
                Map<String, Store.Card> cards = Store.cards(this, b.id);
                for (Question q : Banks.load(this, b)) {
                    ids.add(q.id());
                    Store.Card card = cards.get(q.id());
                    if (card != null && card.box >= 3) {
                        r.assodate++;
                    }
                    for (String tag : Store.tagsOf(q, mine.get(q.id()))) {
                        if (!r.tags.contains(tag)) {
                            r.tags.add(tag);
                        }
                    }
                }
                Collections.sort(r.tags);
                if (r.due == 0) {
                    r.nextDue = Store.nextDue(this, b.id, ids);
                }
                fresh.add(r);
            }
            runOnUiThread(() -> {
                rows.clear();
                rows.addAll(fresh);
                bindOggi();
                render();
            });
        }).start();
    }

    /** La scheda in cima: quante domande scadono oggi e quanto è assodato in tutto. */
    private void bindOggi() {
        MaterialCardView card = findViewById(R.id.oggi_card);
        if (card == null) {
            return;
        }
        int dovute = 0;
        int domande = 0;
        int assodate = 0;
        int affrontate = 0;
        int banchiDovuti = 0;
        long prossimo = 0;
        for (Row r : rows) {
            dovute += r.due;
            domande += r.bank.count;
            assodate += r.assodate;
            affrontate += r.bank.count - r.unseen;
            if (r.due > 0) {
                banchiDovuti++;
            }
            if (r.nextDue > 0 && (prossimo == 0 || r.nextDue < prossimo)) {
                prossimo = r.nextDue;
            }
        }
        if (domande == 0) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);

        TextView tag = findViewById(R.id.oggi_tag);
        TextView titolo = findViewById(R.id.oggi_titolo);
        TextView sotto = findViewById(R.id.oggi_sotto);
        MaterialButton vai = findViewById(R.id.oggi_vai);
        MaterialButton esame = findViewById(R.id.oggi_esame);
        RingView ring = findViewById(R.id.oggi_ring);

        // tre stati: non hai ancora cominciato, hai roba in scadenza, sei in pari
        int tinta;
        if (affrontate == 0) {
            tag.setText(R.string.da_cominciare);
            tinta = getColor(R.color.muted);
            tag.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.surface2)));
            titolo.setText(getString(R.string.domande_pronte, domande));
            sotto.setText(R.string.oggi_prima_volta);
        } else if (dovute > 0) {
            tag.setText(R.string.in_scadenza_oggi_tag);
            tinta = getColor(R.color.warn);
            tag.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.warn_bg)));
            titolo.setText(dovute == 1
                    ? getString(R.string.una_domanda_n, dovute)
                    : getString(R.string.n_domande, dovute));
            sotto.setText(getString(R.string.oggi_su_banchi, banchiDovuti,
                    getString(banchiDovuti == 1 ? R.string.un_banco : R.string.piu_banchi)));
        } else {
            tag.setText(R.string.ripasso_in_pari_tag);
            tinta = getColor(R.color.ok);
            tag.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.ok_bg)));
            titolo.setText(R.string.oggi_niente);
            sotto.setText(prossimo > 0
                    ? getString(R.string.oggi_prossimo, Ui.quando(this, prossimo))
                    : getString(R.string.oggi_tutto_assodato));
        }
        tag.setTextColor(tinta);

        ring.set(domande > 0 ? Math.round(assodate * 100f / domande) : 0,
                getColor(R.color.accent), getString(R.string.assodato));

        vai.setVisibility(dovute > 0 ? View.VISIBLE : View.GONE);
        vai.setOnClickListener(v -> avviaRipassoGenerale());
        esame.setOnClickListener(v -> {
            Store.set(this, Store.COUNT, 50);
            Store.set(this, Store.TIMER, 90);
            Store.set(this, Store.MODE, Store.MODE_EXAM);
            bindSettings();
            Toast.makeText(this, R.string.prova_esame_impostata, Toast.LENGTH_LONG).show();
        });
    }

    /** Ripassa il banco che ha più domande in scadenza: un tocco, si parte. */
    private void avviaRipassoGenerale() {
        Row scelto = null;
        for (Row r : rows) {
            if (r.due > 0 && (scelto == null || r.due > scelto.due)) {
                scelto = r;
            }
        }
        if (scelto != null) {
            start(scelto.bank, Session.Filter.DA_RIPASSARE, null);
        }
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
        card.setRadius(Ui.dp(this, 20));
        card.setCardElevation(0);
        card.setStrokeColor(getColor(R.color.line));
        card.setStrokeWidth(Ui.dp(this, 1));
        card.setContentPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> onBankClicked(row));
        card.setOnLongClickListener(v -> {
            showBankMenu(row.bank);
            return true;
        });

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(inner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Stato del ripasso: prima come colore e come anello, poi come parola.
        String stato;
        int colore;
        int sfondoPastiglia;
        if (row.due > 0) {
            stato = getString(R.string.stato_oggi, row.due);
            colore = getColor(R.color.warn);
            sfondoPastiglia = getColor(R.color.warn_bg);
        } else if (row.unseen == row.bank.count) {
            stato = getString(R.string.stato_mai_iniziato);
            colore = getColor(R.color.muted);
            sfondoPastiglia = getColor(R.color.surface2);
        } else if (row.nextDue > 0) {
            stato = Ui.quando(this, row.nextDue);
            colore = getColor(R.color.accent);
            sfondoPastiglia = getColor(R.color.accent_soft);
        } else {
            stato = getString(R.string.stato_in_pari);
            colore = getColor(R.color.accent);
            sfondoPastiglia = getColor(R.color.accent_soft);
        }

        RingView ring = new RingView(this);
        ring.set(row.pctAssodate(), colore, null);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                Ui.dp(this, 46), Ui.dp(this, 46));
        rlp.setMarginEnd(Ui.dp(this, 13));
        inner.addView(ring, rlp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        inner.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(row.bank.title);
        title.setTextAppearance(R.style.Text_Titolo);
        col.addView(title);

        List<String> bits = new ArrayList<>();
        bits.add(row.bank.count == 1 ? getString(R.string.una_domanda)
                : getString(R.string.n_domande, row.bank.count));
        if (row.best >= 0) {
            bits.add(getString(R.string.record_pct, row.best));
        }
        if (row.flags > 0) {
            bits.add(getString(R.string.contrassegnate_n, row.flags));
        }

        LinearLayout riga = new LinearLayout(this);
        riga.setOrientation(LinearLayout.HORIZONTAL);
        riga.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(this, 3);
        col.addView(riga, rowLp);

        TextView sub = new TextView(this);
        sub.setText(TextUtils.join(" · ", bits));
        sub.setTextSize(13);
        sub.setTextColor(getColor(R.color.muted));
        riga.addView(sub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView pastiglia = new TextView(this);
        pastiglia.setText(stato);
        pastiglia.setTextSize(12);
        pastiglia.setTypeface(null, android.graphics.Typeface.BOLD);
        pastiglia.setTextColor(colore);
        pastiglia.setBackgroundResource(R.drawable.bg_pill);
        pastiglia.setBackgroundTintList(ColorStateList.valueOf(sfondoPastiglia));
        pastiglia.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.setMarginStart(Ui.dp(this, 8));
        riga.addView(pastiglia, plp);

        // Le stesse voci del tocco prolungato, ma raggiungibili anche a vista.
        ImageButton more = new ImageButton(this);
        more.setImageResource(R.drawable.ic_more);
        more.setBackground(null);
        more.setContentDescription(getString(R.string.opzioni_quiz));
        more.setOnClickListener(v -> showBankMenu(row.bank));
        inner.addView(more, new LinearLayout.LayoutParams(
                Ui.dp(this, 32), Ui.dp(this, 32)));

        line.addView(card);
    }

    // -------------------------------------------------------- avvio del quiz

    private void onBankClicked(Row row) {
        if (row.bank.count == 0) {
            Toast.makeText(this, R.string.banco_vuoto, Toast.LENGTH_SHORT).show();
            return;
        }
        if (row.due == 0 && row.flags == 0 && row.tags.isEmpty()
                && (row.unseen == 0 || row.unseen == row.bank.count)) {
            start(row.bank, Session.Filter.TUTTE, null);
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Session.Filter> filters = new ArrayList<>();
        labels.add(getString(R.string.tutte_le_domande, row.bank.count));
        filters.add(Session.Filter.TUTTE);
        if (row.due > 0) {
            labels.add(getString(R.string.solo_da_ripassare, row.due));
            filters.add(Session.Filter.DA_RIPASSARE);
        }
        if (row.unseen > 0 && row.unseen < row.bank.count) {
            labels.add(getString(R.string.solo_mai_viste, row.unseen));
            filters.add(Session.Filter.MAI_VISTE);
        }
        if (row.flags > 0) {
            labels.add(getString(R.string.solo_contrassegnate, row.flags));
            filters.add(Session.Filter.CONTRASSEGNATE);
        }
        if (!row.tags.isEmpty()) {
            labels.add(getString(R.string.solo_argomento));
            filters.add(Session.Filter.ARGOMENTO);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(row.bank.title)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (filters.get(which) == Session.Filter.ARGOMENTO) {
                        chooseTag(row);
                    } else {
                        start(row.bank, filters.get(which), null);
                    }
                })
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    private void chooseTag(Row row) {
        String[] tags = row.tags.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.scegli_argomento)
                .setItems(tags, (d, which) ->
                        start(row.bank, Session.Filter.ARGOMENTO, tags[which]))
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    private void start(Bank bank, Session.Filter filter, String tag) {
        Session.Config cfg = Session.Config.fromPrefs(this);
        cfg.filter = filter;
        cfg.tag = tag;
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
