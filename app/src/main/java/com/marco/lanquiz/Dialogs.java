package com.marco.lanquiz;

import android.app.Activity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/** Le finestrelle per scrivere una nota o assegnare gli argomenti. */
public final class Dialogs {

    public interface OnText {
        void take(String value);
    }

    private Dialogs() {
    }

    public static void text(Activity a, int titleRes, int hintRes, String current,
                            boolean multiline, OnText cb) {
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_input, null);
        TextInputLayout wrap = v.findViewById(R.id.wrap);
        TextInputEditText input = v.findViewById(R.id.input);
        wrap.setHint(a.getString(hintRes));
        input.setText(current);
        if (multiline) {
            input.setSingleLine(false);
            input.setMinLines(3);
            input.setMaxLines(8);
            input.setGravity(Gravity.TOP | Gravity.START);
        }
        input.setSelection(input.getText() == null ? 0 : input.getText().length());
        new MaterialAlertDialogBuilder(a)
                .setTitle(titleRes)
                .setView(v)
                .setPositiveButton(R.string.salva, (d, w) -> cb.take(
                        input.getText() == null ? "" : input.getText().toString()))
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    /** Nota personale sulla domanda: vuota la cancella. */
    public static void editNote(Activity a, String bankId, String questionId, Runnable onDone) {
        text(a, R.string.modifica_nota, R.string.nota_hint,
                Store.note(a, bankId, questionId), true, value -> {
                    Store.setNote(a, bankId, questionId, value);
                    if (onDone != null) {
                        onDone.run();
                    }
                });
    }

    /**
     * Argomenti della domanda. Quelli scritti nel file non si toccano: qui si
     * modificano solo i tuoi, e la casella parte da quelli.
     */
    public static void editTags(Activity a, String bankId, Question q, Runnable onDone) {
        List<String> mine = Store.userTags(a, bankId).get(q.id());
        String current = mine == null ? "" : TextUtils.join(" ", mine);
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_input, null);
        TextInputLayout wrap = v.findViewById(R.id.wrap);
        TextInputEditText input = v.findViewById(R.id.input);
        wrap.setHint(a.getString(R.string.argomenti_hint));
        if (!q.tags.isEmpty()) {
            wrap.setHelperText(a.getString(R.string.argomenti_del_file,
                    TextUtils.join(", ", q.tags)));
        }
        input.setText(current);
        new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.modifica_argomenti)
                .setView(v)
                .setPositiveButton(R.string.salva, (d, w) -> {
                    Store.setUserTags(a, bankId, q.id(), parseTags(
                            input.getText() == null ? "" : input.getText().toString()));
                    if (onDone != null) {
                        onDone.run();
                    }
                })
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    static List<String> parseTags(String raw) {
        List<String> out = new ArrayList<>();
        for (String piece : raw.split("[,;@]+")) {
            String tag = Parser.normalizeTag(piece);
            if (!tag.isEmpty() && !out.contains(tag)) {
                out.add(tag);
            }
        }
        return out;
    }
}
