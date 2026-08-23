package com.marco.lanquiz;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

/** Impostazioni dell'app: aspetto, valutazione, comportamento, aggiornamenti. */
public class SettingsActivity extends AppCompatActivity {

    private LinearLayout box;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_page);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.impostazioni);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        box = findViewById(R.id.container);
        Ui.limitWidth(box);
        render();
    }

    private void render() {
        box.removeAllViews();

        // --- aspetto --------------------------------------------------------
        LinearLayout look = Ui.card(this, box);
        Ui.text(this, look, getString(R.string.aspetto), 17, getColor(R.color.indigo), true);
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
                getColor(R.color.indigo), true);
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
                getColor(R.color.indigo), true);
        addSwitch(behaviour, R.string.vibrazione, Store.haptics(this),
                v -> Store.set(this, Store.HAPTICS, v));
        addSwitch(behaviour, R.string.avanzamento_automatico, Store.autoNext(this),
                v -> Store.set(this, Store.AUTO_NEXT, v));

        // --- quiz preinstallati nascosti -------------------------------------
        LinearLayout bundled = Ui.card(this, box);
        Ui.text(this, bundled, getString(R.string.quiz_preinstallati), 17,
                getColor(R.color.indigo), true);
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

        // --- aggiornamenti ---------------------------------------------------
        LinearLayout updates = Ui.card(this, box);
        Ui.text(this, updates, getString(R.string.aggiornamenti), 17,
                getColor(R.color.indigo), true);
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
