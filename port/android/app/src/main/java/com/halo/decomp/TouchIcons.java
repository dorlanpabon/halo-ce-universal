package com.halo.decomp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Original vector symbols, drawn without textures or per-frame allocations. */
final class TouchIcons {
    private static final String[] LABELS = {
        "SALTAR", "TIPO G.", "USAR", "ARMA", "GOLPE", "LUZ",
        "GRANADA", "DISPARAR", "PAUSA", "ATRÁS", "AGACHAR", "ZOOM"
    };
    private final Path path = new Path();

    /** Context prompts name Xbox buttons, so keep their letter visible on touch. */
    void drawCaption(Canvas canvas, Paint paint, float x, float y, float radius,
            String button, String action) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(radius * 0.52f);
        canvas.drawText(button, x, y - radius * 0.04f, paint);
        paint.setTextSize(radius * 0.32f);
        canvas.drawText(action, x, y + radius * 0.5f, paint);
    }

    void drawLabel(Canvas canvas, Paint paint, float x, float y, float radius, int action) {
        if (action < 0 || action >= LABELS.length)
            return;
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(radius * 0.3f);
        float width = paint.measureText(LABELS[action]);
        if (width > radius * 1.4f)
            paint.setTextSize(paint.getTextSize() * radius * 1.4f / width);
        canvas.drawText(LABELS[action], x, y + radius * 0.64f, paint);
    }

    void draw(Canvas canvas, Paint paint, float x, float y, float radius, int action) {
        if (action < 0 || action > 11)
            return;
        canvas.save();
        canvas.translate(x, y - radius * 0.17f);
        float scale = radius * 0.48f;
        canvas.scale(scale, scale);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.11f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        path.rewind();
        switch (action) {
            case 0: // jump
                canvas.drawCircle(0, -0.65f, 0.2f, paint);
                line(0, -0.4f, 0, 0.15f);
                line(-0.65f, -0.65f, 0, -0.2f);
                line(0, -0.2f, 0.65f, -0.65f);
                line(0, 0.15f, -0.55f, 0.6f);
                line(0, 0.15f, 0.55f, 0.6f);
                line(-0.65f, 0.95f, 0.65f, 0.95f);
                break;
            case 1: // grenade type
                canvas.drawOval(-0.85f, -0.05f, -0.15f, 0.8f, paint);
                canvas.drawOval(0.15f, -0.05f, 0.85f, 0.8f, paint);
                canvas.drawRect(-0.65f, -0.3f, -0.35f, -0.05f, paint);
                canvas.drawRect(0.35f, -0.3f, 0.65f, -0.05f, paint);
                line(-0.65f, -0.65f, 0.65f, -0.65f);
                line(0.65f, -0.65f, 0.35f, -0.95f);
                line(0.65f, -0.65f, 0.35f, -0.35f);
                break;
            case 2: // reload / hold to interact
                canvas.drawArc(-0.7f, -0.7f, 0.7f, 0.7f, 20, 290, false, paint);
                line(0.45f, -0.55f, 0.75f, -0.55f);
                line(0.75f, -0.55f, 0.75f, -0.9f);
                break;
            case 3: // switch weapon
                line(-0.8f, -0.35f, 0.7f, -0.35f);
                line(0.7f, -0.35f, 0.3f, -0.75f);
                line(0.8f, 0.35f, -0.7f, 0.35f);
                line(-0.7f, 0.35f, -0.3f, 0.75f);
                break;
            case 4: // melee
                path.moveTo(-0.65f, 0.7f);
                path.lineTo(-0.75f, -0.15f);
                path.lineTo(-0.4f, -0.55f);
                path.lineTo(0.45f, -0.55f);
                path.lineTo(0.75f, -0.15f);
                path.lineTo(0.4f, 0.7f);
                path.close();
                line(-0.25f, -0.55f, -0.25f, 0);
                line(0.15f, -0.55f, 0.15f, 0);
                break;
            case 5: // flashlight
                canvas.drawRect(-0.7f, -0.25f, 0.15f, 0.25f, paint);
                line(0.15f, -0.25f, 0.45f, -0.5f);
                line(0.45f, -0.5f, 0.45f, 0.5f);
                line(0.45f, 0.5f, 0.15f, 0.25f);
                line(0.7f, -0.6f, 1, -0.8f);
                line(0.7f, 0, 1, 0);
                line(0.7f, 0.6f, 1, 0.8f);
                break;
            case 6: // grenade
                canvas.drawOval(-0.5f, -0.4f, 0.5f, 0.75f, paint);
                canvas.drawRect(-0.2f, -0.7f, 0.2f, -0.4f, paint);
                line(0.2f, -0.7f, 0.55f, -0.4f);
                break;
            case 7: // fire
                path.moveTo(0, -0.85f);
                path.lineTo(-0.28f, -0.35f);
                path.lineTo(-0.28f, 0.8f);
                path.lineTo(0.28f, 0.8f);
                path.lineTo(0.28f, -0.35f);
                path.close();
                line(-0.28f, 0.45f, 0.28f, 0.45f);
                line(-0.8f, -0.65f, -0.55f, -0.4f);
                line(0.8f, -0.65f, 0.55f, -0.4f);
                line(-0.9f, 0, -0.55f, 0);
                line(0.9f, 0, 0.55f, 0);
                break;
            case 8: // pause
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(-0.6f, -0.8f, -0.2f, 0.8f, 0.08f, 0.08f, paint);
                canvas.drawRoundRect(0.2f, -0.8f, 0.6f, 0.8f, 0.08f, 0.08f, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case 9: // back
                line(-0.6f, 0, 0.7f, 0);
                line(-0.6f, 0, 0, -0.6f);
                line(-0.6f, 0, 0, 0.6f);
                break;
            case 10: // crouch
                canvas.drawCircle(0.1f, -0.6f, 0.2f, paint);
                line(0.1f, -0.35f, -0.3f, 0.15f);
                line(-0.3f, 0.15f, 0.4f, 0.15f);
                line(0.4f, 0.15f, 0.15f, 0.75f);
                line(-0.3f, 0.15f, -0.65f, 0.7f);
                line(0.1f, -0.2f, 0.65f, -0.1f);
                break;
            case 11: // zoom
                canvas.drawCircle(-0.2f, -0.2f, 0.55f, paint);
                line(0.2f, 0.2f, 0.85f, 0.85f);
                line(-0.5f, -0.2f, 0.1f, -0.2f);
                line(-0.2f, -0.5f, -0.2f, 0.1f);
                break;
        }
        canvas.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private void line(float x1, float y1, float x2, float y2) {
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
    }
}
