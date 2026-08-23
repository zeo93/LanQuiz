package com.marco.lanquiz;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Impostazioni dell'app: aspetto, valutazione, comportamento, aggiornamenti. */
public class SettingsActivity extends AppCompatActivity {

    private LinearLayout box;
    private ActivityResultLauncher<String[]> pickBackup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_page);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.impostazioni);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        pickBackup = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        askHowToImport(uri);
                    }
                });

        box = findViewById(R.id.container);
        Ui.limitWidth(box);
        render();
    }

    private void render() {
        box.removeAllViews();

        // --- aspetto --------------------------------------------------------
        LinearLayout look = Ui.card(this, box);
        Ui.text(this, look, getString(R.string.aspetto), 17, getColor(R.color.accent), true);
        Ui.text(this, look, getString(R.string.tema), 14, getColor(R.color.muted), false);

        ChipGroup themes = new ChipGroup(this);
        themes.setSingleSelection(true);
        themes.setSelectionRequired(true);
        String[] values = {"sistema", "chiaro", "scuro"};
        int[] labels = {R.string.tema_sistema, R.string.tema_chiaro, R.string.tema_scuro};
        String current = Store.theme(this);
        for (int i = 0; i < values.length; i++) {
            Chip chip = new Chip(this);
            chip.setText(labels[i]);
            chip.setCheckable(true);
            chip.setId(1000 + i);
            chip.setChecked(values[i].equals(current));
            themes.addView(chip);
        }
        themes.setOnCheckedStateChangeListener((g, ids) -> {
            int id = g.getCheckedChipId();
            if (id >= 1000 && id < 1000 + values.length) {
                Store.set(this, Store.THEME, values[id - 1000]);
                App.applyTheme(this);
                recreate();
            }
        });
        look.addView(themes);

        // --- valutazione ----------------------------------------------------
        LinearLayout grading = Ui.card(this, box);
        Ui.text(this, grading, getString(R.string.valutazione), 17,
                getColor(R.color.accent), true);
        TextView pass = Ui.text(this, grading, getString(R.string.soglia_superamento)
                + ": " + Store.passPct(this) + "%", 14, getColor(R.color.muted), false);
        Slider slider = new Slider(this);
        slider.setValueFrom(30f);
        slider.setValueTo(100f);
        slider.setStepSize(5f);
        slider.setValue(Math.max(30, Math.min(100, Store.passPct(this))));
        slider.addOnChangeListener((s, value, fromUser) -> {
            int v = Math.round(value);
            Store.set(this, Store.PASS_PCT, v);
            pass.setText(getString(R.string.soglia_superamento) + ": " + v + "%");
        });
        grading.addView(slider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- comportamento --------------------------------------------------
        LinearLayout behaviour = Ui.card(this, box);
        Ui.text(this, behaviour, getString(R.string.comportamento), 17,
                getColor(R.color.accent), true);
        addSwitch(behaviour, R.string.vibrazione, Store.haptics(this),
                v -> Store.set(this, Store.HAPTICS, v));
        addSwitch(behaviour, R.string.avanzamento_automatico, Store.autoNext(this),
                v -> Store.set(this, Store.AUTO_NEXT, v));

        // --- quiz preinstallati nascosti -------------------------------------
        LinearLayout bundled = Ui.card(this, box);
        Ui.text(this, bundled, getString(R.string.quiz_preinstallati), 17,
                getColor(R.color.accent), true);
        int hidden = Store.hiddenBanks(this).size();
        if (hidden == 0) {
            Ui.text(this, bundled, getString(R.string.nessun_nascosto), 14,
                    getColor(R.color.muted), false);
        } else {
            MaterialButton restore = outlined(getString(R.string.ripristina_nascosti, hidden));
            restore.setOnClickListener(v -> {
                Banks.restoreBundled(this);
                render();
            });
            bundled.addView(restore);
        }

        // --- backup e trasferimento ------------------------------------------
        LinearLayout backup = Ui.card(this, box);
        Ui.text(this, backup, getString(R.string.backup), 17, getColor(R.color.accent), true);
        Ui.text(this, backup, getString(R.string.backup_desc), 14,
                getColor(R.color.muted), false);
        MaterialButton esporta = outlined(getString(R.string.esporta_backup));
        esporta.setOnClickListener(v -> exportBackup());
        backup.addView(esporta);
        MaterialButton importa = outlined(getString(R.string.importa_backup));
        importa.setOnClickListener(v -> pickBackup.launch(
                new String[]{"application/json", "text/*", "*/*"}));
        backup.addView(importa);

        // --- aggiornamenti ---------------------------------------------------
        LinearLayout updates = Ui.card(this, box);
        Ui.text(this, updates, getString(R.string.aggiornamenti), 17,
                getColor(R.color.accent), true);
        Ui.text(this, updates, getString(R.string.versione_app,
                UpdateChecker.currentVersion(this)), 14, getColor(R.color.muted), false);
        addSwitch(updates, R.string.controlla_avvio, Store.updateCheck(this),
                v -> Store.set(this, Store.UPDATE_CHECK, v));
        MaterialButton check = outlined(getString(R.string.controlla_aggiornamenti));
        check.setOnClickListener(v -> {
            check.setEnabled(false);
            UpdateChecker.checkAsync(this, (update, error) -> {
                check.setEnabled(true);
                if (update != null) {
                    UpdateChecker.showUpdateDialog(this, update);
                } else {
                    Toast.makeText(this, error != null
                                    ? getString(R.string.errore_download, error)
                                    : getString(R.string.nessun_aggiornamento),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
        updates.addView(check);
    }

    // ------------------------------------------------------------- backup

    private void exportBackup() {
        try {
            File dir = new File(getCacheDir(), "condivisi");
            dir.mkdirs();
            String giorno = new SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
                    .format(new Date());
            File out = new File(dir, "lanquiz-backup-" + giorno + ".json");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(Store.exportAll(this).toString(2)
                        .getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", out);
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    getString(R.string.esporta_backup)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.backup_errore, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void askHowToImport(Uri uri) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_come)
                .setMessage(R.string.backup_come_desc)
                .setPositiveButton(R.string.backup_unisci, (d, w) -> doImport(uri, false))
                .setNeutralButton(R.string.backup_sostituisci, (d, w) -> doImport(uri, true))
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    private void doImport(Uri uri, boolean sostituisci) {
        new Thread(() -> {
            String message;
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) {
                    throw new IOException("file non leggibile");
                }
                int aggiunti = Store.importAll(this,
                        new JSONObject(Banks.readAll(in)), sostituisci);
                message = getString(R.string.backup_fatto, aggiunti);
            } catch (Exception e) {
                message = getString(R.string.backup_errore,
                        e.getMessage() == null ? "?" : e.getMessage());
            }
            String finalMessage = message;
            runOnUiThread(() -> {
                Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show();
                App.applyTheme(this);
                render();
            });
        }).start();
    }

    private interface OnToggle {
        void set(boolean value);
    }

    private void addSwitch(LinearLayout parent, int labelRes, boolean value, OnToggle cb) {
        MaterialSwitch s = new MaterialSwitch(this);
        s.setText(labelRes);
        s.setChecked(value);
        s.setOnCheckedChangeListener((b, v) -> cb.set(v));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 4);
        parent.addView(s, lp);
    }

    private MaterialButton outlined(String label) {
        MaterialButton b = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        b.setLayoutParams(lp);
        return b;
    }
}
