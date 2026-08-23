package com.marco.lanquiz;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.util.Locale;

/** Pezzi di interfaccia costruiti a runtime, condivisi fra le activity. */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Card aggiunta in fondo a parent: restituisce la colonna interna da riempire. */
    public static LinearLayout card(Context c, LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 6), 0, dp(c, 6));
        card.setLayoutParams(lp);
        card.setRadius(dp(c, 20));
        card.setCardElevation(0);
        card.setStrokeColor(c.getColor(R.color.line));
        card.setStrokeWidth(dp(c, 1));
        card.setContentPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        parent.addView(card);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        card.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    public static TextView text(Context c, LinearLayout parent, CharSequence s,
                                float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sizeSp);
        if (color != 0) {
            tv.setTextColor(color);
        }
        if (bold) {
            tv.setTypeface(null, Typeface.BOLD);
        }
        tv.setPadding(0, dp(c, 2), 0, dp(c, 2));
        parent.addView(tv);
        return tv;
    }

    public static TextView sectionTitle(Context c, LinearLayout parent, String title) {
        TextView tv = text(c, parent, title, 17, c.getColor(R.color.accent), true);
        ((LinearLayout.LayoutParams) tv.getLayoutParams()).topMargin = dp(c, 14);
        return tv;
    }

    /** Riga "etichetta … valore" usata nelle statistiche. */
    public static void row(Context c, LinearLayout parent, String label, String value, int color) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(c, 4), 0, dp(c, 4));
        TextView l = new TextView(c);
        l.setText(label);
        l.setTextSize(15);
        l.setTextColor(c.getColor(R.color.muted));
        l.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(15);
        v.setGravity(Gravity.END);
        v.setTypeface(null, Typeface.BOLD);
        v.setTextColor(color != 0 ? color : c.getColor(R.color.on_surface));
        row.addView(l);
        row.addView(v);
        parent.addView(row);
    }

    /**
     * Su tablet il contenuto non deve stirarsi da bordo a bordo: lo si tiene
     * entro content_max_width e centrato.
     */
    public static void limitWidth(View content) {
        int max = content.getResources().getDimensionPixelSize(R.dimen.content_max_width);
        if (max <= 0) {
            return;
        }
        content.post(() -> {
            View parent = (View) content.getParent();
            if (parent == null || parent.getWidth() <= max) {
                return;
            }
            ViewGroup.LayoutParams lp = content.getLayoutParams();
            if (lp.width == max) {
                return; // gia sistemato: non rifarlo a ogni layout
            }
            lp.width = max;
            if (lp instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) lp).gravity = Gravity.CENTER_HORIZONTAL;
            } else if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lp).gravity = Gravity.CENTER_HORIZONTAL;
            }
            content.setLayoutParams(lp);
        });
    }

    public static String duration(int seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.ITALY, "%d h %02d min", h, m);
        }
        if (m > 0) {
            return String.format(Locale.ITALY, "%d min %02d s", m, s);
        }
        return String.format(Locale.ITALY, "%d s", s);
    }

    public static String clock(int seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        int h = seconds / 3600;
        if (h > 0) {
            return String.format(Locale.ITALY, "%d:%02d:%02d", h, (seconds % 3600) / 60, seconds % 60);
        }
        return String.format(Locale.ITALY, "%02d:%02d", seconds / 60, seconds % 60);
    }

    /** "domani", "fra 5 giorni", oppure la data se è lontana. */
    public static String quando(Context c, long time) {
        long giorni = Math.max(0, (time - System.currentTimeMillis() + 60000) / (24L * 3600 * 1000));
        if (giorni <= 0) {
            return c.getString(R.string.oggi);
        }
        if (giorni == 1) {
            return c.getString(R.string.domani);
        }
        if (giorni <= 14) {
            return c.getString(R.string.fra_giorni, (int) giorni);
        }
        return new java.text.SimpleDateFormat("d MMM", Locale.ITALY)
                .format(new java.util.Date(time));
    }

    /** Verde se supera la soglia, arancione se ci va vicino, rosso altrimenti. */
    public static int scoreColor(Context c, int percent, int passPct) {
        if (percent >= passPct) {
            return c.getColor(R.color.ok);
        }
        if (percent >= passPct - 15) {
            return c.getColor(R.color.warn);
        }
        return c.getColor(R.color.ko);
    }

    public static void buzz(Context c, boolean right) {
        if (!Store.haptics(c)) {
            return;
        }
        Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(right
                    ? VibrationEffect.EFFECT_CLICK : VibrationEffect.EFFECT_DOUBLE_CLICK));
        } else if (right) {
            v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 30, 70, 30}, -1));
        }
    }
}
