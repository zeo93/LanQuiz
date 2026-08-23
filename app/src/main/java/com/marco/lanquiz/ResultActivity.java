package com.marco.lanquiz;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/** Esito del quiz e ripasso domanda per domanda. */
public class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION = "session";

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_REVIEW = 1;

    private Session session;
    private int wrongCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        session = Session.pick(getIntent().getStringExtra(EXTRA_SESSION));
        if (session == null) {
            finish();
            return;
        }
        wrongCount = session.items.size() - session.correctCount();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle(session.bankTitle);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> goHome());

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new Adapter());
        Ui.limitWidth(list);
    }

    private void goHome() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private void restart(Session fresh) {
        if (fresh.items.isEmpty()) {
            goHome();
            return;
        }
        startActivity(new Intent(this, QuizActivity.class)
                .putExtra(QuizActivity.EXTRA_SESSION, Session.park(fresh)));
        finish();
    }

    private void share() {
        String text = getString(R.string.riepilogo_condivisione, session.bankTitle,
                session.percent(), session.correctCount(), session.items.size());
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text), getString(R.string.condividi_risultato)));
    }

    // ------------------------------------------------------------- adapter

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_HEADER : TYPE_REVIEW;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext()).inflate(type == TYPE_HEADER
                    ? R.layout.item_result_header : R.layout.item_review, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                bindHeader(holder.itemView);
            } else {
                bindReview(holder.itemView, position - 1);
            }
        }

        @Override
        public int getItemCount() {
            return session.items.size() + 1;
        }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        Holder(View v) {
            super(v);
        }
    }

    private void bindHeader(View v) {
        int pct = session.percent();
        int pass = Store.passPct(this);
        boolean ok = pct >= pass;

        TextView percent = v.findViewById(R.id.percent);
        percent.setText(pct + "%");
        percent.setTextColor(Ui.scoreColor(this, pct, pass));

        TextView verdict = v.findViewById(R.id.verdict);
        verdict.setText(ok ? R.string.promosso : R.string.bocciato);
        verdict.setTextColor(Ui.scoreColor(this, pct, pass));

        ((TextView) v.findViewById(R.id.detail)).setText(
                getString(R.string.risposte_corrette, session.correctCount(), session.items.size())
                        + "\n" + getString(R.string.soglia_pct, pass));
        ((TextView) v.findViewById(R.id.timing)).setText(
                getString(R.string.tempo_impiegato, Ui.duration(session.elapsedSeconds)));
        v.findViewById(R.id.timeout).setVisibility(session.timedOut ? View.VISIBLE : View.GONE);

        MaterialButton retryWrong = v.findViewById(R.id.btn_retry_wrong);
        retryWrong.setText(getString(R.string.riprova_sbagliate, wrongCount));
        retryWrong.setVisibility(wrongCount > 0 ? View.VISIBLE : View.GONE);
        retryWrong.setOnClickListener(x -> restart(Session.retryWrong(session)));

        v.findViewById(R.id.btn_retry_all).setOnClickListener(x -> {
            Bank bank = Banks.find(this, session.bankId);
            if (bank == null) {
                goHome();
                return;
            }
            restart(Session.build(this, bank, Session.Config.fromPrefs(this)));
        });
        v.findViewById(R.id.btn_home).setOnClickListener(x -> goHome());
        v.findViewById(R.id.btn_share).setOnClickListener(x -> share());
    }

    private void bindReview(View v, int index) {
        Session.Item item = session.items.get(index);
        boolean right = item.right();

        TextView badge = v.findViewById(R.id.badge);
        badge.setText(String.valueOf(index + 1));
        badge.setTextColor(getColor(right ? R.color.ok : R.color.ko));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(Ui.dp(this, 8));
        shape.setColor(getColor(right ? R.color.ok_bg : R.color.ko_bg));
        badge.setBackground(shape);

        ((TextView) v.findViewById(R.id.question)).setText(item.question.text);

        LinearLayout answers = v.findViewById(R.id.answers);
        answers.removeAllViews();
        for (int pos = 0; pos < item.order.size(); pos++) {
            int original = item.order.get(pos);
            boolean isCorrect = item.isCorrect(original);
            boolean chosen = item.selected.contains(original);
            if (!isCorrect && !chosen) {
                continue; // nel ripasso bastano l'esatta e quella che hai scelto
            }
            TextView tv = new TextView(this);
            tv.setText((isCorrect ? "✓ " : "✗ ") + item.question.answers.get(original));
            tv.setTextSize(14);
            tv.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
            tv.setTextColor(getColor(isCorrect ? R.color.ok : R.color.ko));
            if (chosen) {
                tv.setTypeface(null, Typeface.BOLD);
            }
            answers.addView(tv);
        }
        if (!item.answered()) {
            TextView tv = new TextView(this);
            tv.setText(R.string.senza_risposta);
            tv.setTextSize(13);
            tv.setTextColor(getColor(R.color.muted));
            answers.addView(tv);
        }

        TextView explanation = v.findViewById(R.id.explanation);
        if (item.question.explanation.isEmpty()) {
            explanation.setVisibility(View.GONE);
        } else {
            explanation.setVisibility(View.VISIBLE);
            explanation.setText(getString(R.string.spiegazione) + ": " + item.question.explanation);
        }

        TextView note = v.findViewById(R.id.note);
        String testo = Store.note(this, session.bankId, item.question.id());
        note.setVisibility(testo.isEmpty() ? View.GONE : View.VISIBLE);
        note.setText(testo);
        note.setTextColor(getColor(R.color.on_surface));

        TextView tagsView = v.findViewById(R.id.tags);
        List<String> tags = Store.tagsOf(item.question,
                Store.userTags(this, session.bankId).get(item.question.id()));
        tagsView.setVisibility(tags.isEmpty() ? View.GONE : View.VISIBLE);
        tagsView.setText(TextUtils.join("  ", prefixed(tags)));

        // Quando tornerà questa domanda: è il riscontro della ripetizione spaziata.
        TextView box = v.findViewById(R.id.box);
        Store.Card card = Store.cards(this, session.bankId).get(item.question.id());
        box.setText(card == null ? getString(R.string.mai_affrontata)
                : getString(R.string.scatola_n, card.box, Store.SCATOLA_MAX,
                Ui.quando(this, card.due)));

        v.findViewById(R.id.btn_note).setOnClickListener(x -> Dialogs.editNote(this,
                session.bankId, item.question.id(), () -> bindReview(v, index)));
        v.findViewById(R.id.btn_tags).setOnClickListener(x -> Dialogs.editTags(this,
                session.bankId, item.question, () -> bindReview(v, index)));
    }

    private static List<String> prefixed(List<String> tags) {
        List<String> out = new ArrayList<>();
        for (String t : tags) {
            out.add("#" + t);
        }
        return out;
    }
}
