package com.marco.lanquiz;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

/** Il quiz vero e proprio: una domanda alla volta, con mappa e timer. */
public class QuizActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION = "session";

    private Session session;
    private MaterialToolbar toolbar;
    private TextView counter;
    private TextView timerView;
    private TextView questionView;
    private TextView multiHint;
    private LinearLayout answersBox;
    private MaterialCardView feedback;
    private TextView feedbackTitle;
    private TextView feedbackText;
    private LinearProgressIndicator progress;
    private MaterialButton btnPrev;
    private MaterialButton btnNext;
    private MaterialButton btnConfirm;
    private ScrollView scroll;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private boolean running;
    private boolean finished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        session = Session.pick(getIntent().getStringExtra(EXTRA_SESSION));
        if (session == null && savedInstanceState != null) {
            session = Session.fromJson(savedInstanceState.getString("state"));
        }
        if (session == null || session.items.isEmpty()) {
            finish();
            return;
        }

        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(session.bankTitle);
        toolbar.setSubtitle(getString(session.exam()
                ? R.string.modalita_esame : R.string.modalita_studio));
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> askExit());

        counter = findViewById(R.id.counter);
        timerView = findViewById(R.id.timer);
        questionView = findViewById(R.id.question);
        multiHint = findViewById(R.id.multi_hint);
        answersBox = findViewById(R.id.answers);
        feedback = findViewById(R.id.feedback);
        feedbackTitle = findViewById(R.id.feedback_title);
        feedbackText = findViewById(R.id.feedback_text);
        progress = findViewById(R.id.progress);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnConfirm = findViewById(R.id.btn_confirm);
        scroll = findViewById(R.id.scroll);
        Ui.limitWidth(findViewById(R.id.page));

        progress.setMax(session.items.size());
        btnPrev.setOnClickListener(v -> go(session.index - 1));
        btnNext.setOnClickListener(v -> {
            if (session.index >= session.items.size() - 1) {
                askFinish();
            } else {
                go(session.index + 1);
            }
        });
        btnConfirm.setOnClickListener(v -> reveal(session.current()));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                askExit();
            }
        });

        show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (session != null) {
            out.putString("state", session.toJson());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        ticker.postDelayed(tick, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        ticker.removeCallbacks(tick);
        if (!finished && session != null && !isFinishing()) {
            Store.saveResume(this, session.toJson()); // riprendibile dalla home
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running || finished) {
                return;
            }
            session.elapsedSeconds++;
            if (session.timerSeconds > 0) {
                session.secondsLeft--;
                paintTimer();
                if (session.secondsLeft <= 0) {
                    session.timedOut = true;
                    Toast.makeText(QuizActivity.this, R.string.tempo_scaduto,
                            Toast.LENGTH_LONG).show();
                    finishQuiz();
                    return;
                }
            }
            ticker.postDelayed(this, 1000);
        }
    };

    private void paintTimer() {
        if (session.timerSeconds <= 0) {
            timerView.setVisibility(View.GONE);
            return;
        }
        timerView.setVisibility(View.VISIBLE);
        timerView.setText(Ui.clock(session.secondsLeft));
        timerView.setTextColor(session.secondsLeft <= 60 ? getColor(R.color.ko)
                : session.secondsLeft <= 300 ? getColor(R.color.warn)
                : getColor(R.color.muted));
    }

    // ------------------------------------------------------------ menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_quiz, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem flag = menu.findItem(R.id.menu_flag);
        Session.Item item = session == null ? null : session.current();
        if (flag != null && item != null) {
            flag.getIcon().setTint(item.flagged
                    ? getColor(R.color.flag) : getColor(R.color.muted));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_flag) {
            Session.Item cur = session.current();
            cur.flagged = Store.toggleFlag(this, session.bankId, cur.question.id());
            invalidateOptionsMenu();
            return true;
        }
        if (item.getItemId() == R.id.menu_map) {
            showMap();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------- disegno domanda

    private void go(int index) {
        session.index = Math.max(0, Math.min(index, session.items.size() - 1));
        show();
    }

    private void show() {
        Session.Item item = session.current();
        counter.setText(getString(R.string.domanda_di, session.index + 1, session.items.size()));
        progress.setProgressCompat(session.answeredCount(), true);
        paintTimer();
        invalidateOptionsMenu();

        questionView.setText(item.question.text);
        multiHint.setVisibility(item.question.multi() ? View.VISIBLE : View.GONE);

        answersBox.removeAllViews();
        boolean revealed = item.revealed && !session.exam();
        int columns = shortAnswers(item) ? getResources().getInteger(R.integer.answer_columns) : 1;
        LinearLayout line = null;
        for (int pos = 0; pos < item.order.size(); pos++) {
            int original = item.order.get(pos);
            if (columns == 1 || pos % columns == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                answersBox.addView(line, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            line.addView(answerButton(item, original, revealed, columns));
        }

        paintFeedback(item, revealed);
        paintButtons(item, revealed);
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private boolean shortAnswers(Session.Item item) {
        for (String a : item.question.answers) {
            if (a.length() > 36) {
                return false;
            }
        }
        return true;
    }

    private MaterialButton answerButton(Session.Item item, int original,
                                        boolean revealed, int columns) {
        MaterialButton b = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                columns == 1 ? ViewGroup.LayoutParams.MATCH_PARENT : 0,
                ViewGroup.LayoutParams.WRAP_CONTENT, columns == 1 ? 0f : 1f);
        int m = Ui.dp(this, 4);
        lp.setMargins(0, m, columns == 1 ? 0 : m, m);
        b.setLayoutParams(lp);
        b.setText(item.question.answers.get(original));
        b.setAllCaps(false);
        b.setMaxLines(20);
        b.setSingleLine(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        b.setLetterSpacing(0f);
        b.setTextSize(15);
        b.setCornerRadius(Ui.dp(this, 12));
        b.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        b.setMinHeight(Ui.dp(this, 56));
        b.setInsetTop(0);
        b.setInsetBottom(0);

        boolean selected = item.selected.contains(original);
        boolean correct = item.isCorrect(original);
        int neutral = getColor(R.color.on_surface);
        int muted = getColor(R.color.muted);

        if (revealed) {
            if (correct) {
                b.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.ok_bg)));
                b.setStrokeColor(ColorStateList.valueOf(getColor(R.color.ok)));
                b.setTextColor(getColor(R.color.ok));
                b.setTypeface(null, Typeface.BOLD);
            } else if (selected) {
                b.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.ko_bg)));
                b.setStrokeColor(ColorStateList.valueOf(getColor(R.color.ko)));
                b.setTextColor(getColor(R.color.ko));
            } else {
                b.setStrokeColor(ColorStateList.valueOf(getColor(R.color.surface2)));
                b.setTextColor(muted);
            }
            b.setEnabled(false);
            // un bottone disabilitato sbiadisce: qui il colore deve restare leggibile
            b.setAlpha(1f);
        } else if (selected) {
            b.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.indigo_soft)));
            b.setStrokeColor(ColorStateList.valueOf(getColor(R.color.indigo)));
            b.setTextColor(getColor(R.color.indigo));
            b.setTypeface(null, Typeface.BOLD);
        } else {
            b.setStrokeColor(ColorStateList.valueOf(getColor(R.color.surface2)));
            b.setTextColor(neutral);
        }

        if (!revealed) {
            b.setOnClickListener(v -> onAnswerTapped(item, original));
        }
        return b;
    }

    private void onAnswerTapped(Session.Item item, int original) {
        if (item.question.multi()) {
            if (!item.selected.remove(original)) {
                item.selected.add(original);
            }
        } else {
            item.selected.clear();
            item.selected.add(original);
        }

        if (session.exam() || item.question.multi()) {
            show(); // in esame e nelle multiple si conferma a parte
            return;
        }
        reveal(item);
    }

    /** In modalità studio svela subito l'esito della domanda corrente. */
    private void reveal(Session.Item item) {
        if (!item.answered() || session.exam()) {
            return;
        }
        item.revealed = true;
        Ui.buzz(this, item.right());
        show();

        boolean canAutoAdvance = Store.autoNext(this) && item.right()
                && item.question.explanation.isEmpty()
                && session.index < session.items.size() - 1;
        if (canAutoAdvance) {
            ticker.postDelayed(() -> {
                if (!finished && session.index < session.items.size() - 1) {
                    go(session.index + 1);
                }
            }, 700);
        } else if (allAnswered()) {
            ticker.postDelayed(this::finishQuiz, 900);
        }
    }

    private boolean allAnswered() {
        for (Session.Item it : session.items) {
            if (!it.answered()) {
                return false;
            }
        }
        return true;
    }

    private void paintFeedback(Session.Item item, boolean revealed) {
        if (!revealed) {
            feedback.setVisibility(View.GONE);
            return;
        }
        boolean right = item.right();
        feedback.setVisibility(View.VISIBLE);
        feedback.setCardBackgroundColor(getColor(right ? R.color.ok_bg : R.color.ko_bg));
        feedbackTitle.setText(right ? R.string.risposta_esatta : R.string.risposta_sbagliata);
        feedbackTitle.setTextColor(getColor(right ? R.color.ok : R.color.ko));
        if (item.question.explanation.isEmpty()) {
            feedbackText.setVisibility(View.GONE);
        } else {
            feedbackText.setVisibility(View.VISIBLE);
            feedbackText.setText(item.question.explanation);
            feedbackText.setTextColor(getColor(right ? R.color.ok : R.color.ko));
        }
    }

    private void paintButtons(Session.Item item, boolean revealed) {
        btnPrev.setVisibility(session.index == 0 ? View.INVISIBLE : View.VISIBLE);
        boolean last = session.index == session.items.size() - 1;
        btnNext.setText(last ? (session.exam() ? R.string.consegna : R.string.termina)
                : R.string.successiva);
        boolean needsConfirm = !session.exam() && !revealed
                && item.question.multi() && item.answered();
        btnConfirm.setVisibility(needsConfirm ? View.VISIBLE : View.GONE);
    }

    // -------------------------------------------------------------- mappa

    private void showMap() {
        View sheetView = LayoutInflater.from(this).inflate(R.layout.sheet_map, null);
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(sheetView);

        ((TextView) sheetView.findViewById(R.id.legend)).setText(
                getString(R.string.domanda_di, session.answeredCount(), session.items.size()));

        RecyclerView grid = sheetView.findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, 6));
        grid.getLayoutParams().height = Math.min(Ui.dp(this, 420),
                Ui.dp(this, 56) * (int) Math.ceil(session.items.size() / 6.0));
        grid.setAdapter(new RecyclerView.Adapter<MapHolder>() {
            @NonNull
            @Override
            public MapHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextView tv = new TextView(QuizActivity.this);
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(14);
                tv.setTypeface(null, Typeface.BOLD);
                int size = Ui.dp(QuizActivity.this, 44);
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(size, size);
                int m = Ui.dp(QuizActivity.this, 3);
                lp.setMargins(m, m, m, m);
                tv.setLayoutParams(lp);
                return new MapHolder(tv);
            }

            @Override
            public void onBindViewHolder(@NonNull MapHolder holder, int position) {
                Session.Item it = session.items.get(position);
                TextView tv = (TextView) holder.itemView;
                tv.setText(String.valueOf(position + 1));

                int bg;
                int fg = getColor(R.color.on_surface);
                if (it.revealed && !session.exam()) {
                    bg = getColor(it.right() ? R.color.ok_bg : R.color.ko_bg);
                    fg = getColor(it.right() ? R.color.ok : R.color.ko);
                } else if (it.answered()) {
                    bg = getColor(R.color.indigo_soft);
                    fg = getColor(R.color.indigo);
                } else {
                    bg = getColor(R.color.surface2);
                }
                android.graphics.drawable.GradientDrawable shape =
                        new android.graphics.drawable.GradientDrawable();
                shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                shape.setCornerRadius(Ui.dp(QuizActivity.this, 10));
                shape.setColor(bg);
                if (position == session.index) {
                    shape.setStroke(Ui.dp(QuizActivity.this, 2), getColor(R.color.indigo));
                } else if (it.flagged) {
                    shape.setStroke(Ui.dp(QuizActivity.this, 2), getColor(R.color.flag));
                }
                tv.setBackground(shape);
                tv.setTextColor(fg);
                tv.setOnClickListener(v -> {
                    sheet.dismiss();
                    go(position);
                });
            }

            @Override
            public int getItemCount() {
                return session.items.size();
            }
        });
        sheet.show();
    }

    private static class MapHolder extends RecyclerView.ViewHolder {
        MapHolder(View v) {
            super(v);
        }
    }

    // ------------------------------------------------------------ chiusura

    private void askFinish() {
        int answered = session.answeredCount();
        if (answered == session.items.size()) {
            finishQuiz();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.consegna_conferma, answered, session.items.size()))
                .setPositiveButton(session.exam() ? R.string.consegna : R.string.termina,
                        (d, w) -> finishQuiz())
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    private void askExit() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.esci_conferma)
                .setPositiveButton(R.string.esci_e_salva, (d, w) -> {
                    Store.saveResume(this, session.toJson());
                    finished = true;
                    finish();
                })
                .setNeutralButton(R.string.esci_e_scarta, (d, w) -> {
                    Store.clearResume(this);
                    finished = true;
                    finish();
                })
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    private void finishQuiz() {
        if (finished) {
            return;
        }
        finished = true;
        running = false;
        ticker.removeCallbacksAndMessages(null);
        Store.clearResume(this);

        List<String> right = session.rightIds();
        List<String> wrong = session.wrongIds();
        Store.recordResult(this, session.bankId, session.bankTitle, session.mode,
                session.correctCount(), session.items.size(), session.elapsedSeconds,
                right, wrong);

        startActivity(new Intent(this, ResultActivity.class)
                .putExtra(ResultActivity.EXTRA_SESSION, Session.park(session)));
        finish();
    }
}
