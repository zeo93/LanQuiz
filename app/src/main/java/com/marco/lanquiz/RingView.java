package com.marco.lanquiz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

/**
 * Anello di avanzamento con la percentuale al centro: quanto materiale di un
 * banco è ormai assodato. Disegnato a mano perché deve restare leggibile a
 * 48dp sulle card e a 148dp nella scheda in cima, con lo stesso disegno.
 */
public class RingView extends View {

    private final Paint traccia = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arco = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numero = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint didascalia = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private int percento;
    private String sotto;

    public RingView(Context c) {
        this(c, null);
    }

    public RingView(Context c, @Nullable AttributeSet attrs) {
        super(c, attrs);
        traccia.setStyle(Paint.Style.STROKE);
        traccia.setColor(c.getColor(R.color.surface2));
        arco.setStyle(Paint.Style.STROKE);
        arco.setStrokeCap(Paint.Cap.ROUND);
        arco.setColor(c.getColor(R.color.accent));

        Typeface display = ResourcesCompat.getFont(c, R.font.bricolage_extrabold);
        numero.setColor(c.getColor(R.color.on_surface));
        numero.setTextAlign(Paint.Align.CENTER);
        numero.setTypeface(display != null ? display : Typeface.DEFAULT_BOLD);
        didascalia.setColor(c.getColor(R.color.muted));
        didascalia.setTextAlign(Paint.Align.CENTER);
    }

    /** @param percento 0–100  @param colore tinta dell'arco  @param sotto etichetta, può essere null */
    public void set(int percento, int colore, @Nullable String sotto) {
        this.percento = Math.max(0, Math.min(100, percento));
        this.sotto = sotto;
        arco.setColor(colore);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float lato = Math.min(getWidth(), getHeight());
        if (lato <= 0) {
            return;
        }
        // spessore e testo in proporzione al lato: un solo disegno per tutte le misure
        float spessore = lato * (lato < 80 ? 0.115f : 0.09f);
        traccia.setStrokeWidth(spessore);
        arco.setStrokeWidth(spessore);

        float m = spessore / 2f + lato * 0.01f;
        box.set(m, m, lato - m, lato - m);
        float cx = lato / 2f;

        canvas.drawArc(box, 0, 360, false, traccia);
        if (percento > 0) {
            canvas.drawArc(box, -90, 360f * percento / 100f, false, arco);
        }

        numero.setTextSize(lato * (sotto != null ? 0.21f : 0.26f));
        float baseline = sotto != null ? cx + lato * 0.03f : cx - (numero.ascent() + numero.descent()) / 2f;
        canvas.drawText(percento + "%", cx, baseline, numero);

        if (sotto != null) {
            didascalia.setTextSize(lato * 0.088f);
            canvas.drawText(sotto, cx, baseline + lato * 0.145f, didascalia);
        }
    }
}
