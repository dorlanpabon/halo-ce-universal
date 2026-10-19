package com.halo.decomp;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowCanvas;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {28, 35})
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class TouchIconsHostTest {
    @Test public void ambiguousActionsHaveDistinctVisibleNames() {
        Canvas canvas = canvas();
        TouchIcons icons = new TouchIcons();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int action = 0; action < 12; action++) {
            icons.draw(canvas, paint, 50, 50, 30, action);
            icons.drawLabel(canvas, paint, 50, 50, 30, action);
        }
        ShadowCanvas drawn = shadowOf(canvas);
        assertEquals("Every action has a visible name", 12, drawn.getTextHistoryCount());
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < drawn.getTextHistoryCount(); i++)
            labels.add(drawn.getDrawnTextEvent(i).text);
        assertEquals("Buttons remain identifiable without decoding their pictogram", 12, labels.size());
        for (String needed : new String[]{"PAUSA", "ATRÁS", "DISPARAR", "ZOOM", "GRANADA", "TIPO G."})
            assertTrue("Visible name: " + needed, labels.contains(needed));
    }

    @Test public void contextPromptsKeepXboxLettersAndDistinctActions() {
        Canvas canvas = canvas();
        TouchIcons icons = new TouchIcons();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        icons.drawCaption(canvas, paint, 40, 40, 24, "X", "USAR");
        icons.drawCaption(canvas, paint, 100, 40, 24, "Y", "ARMA");
        ShadowCanvas drawn = shadowOf(canvas);
        assertEquals(4, drawn.getTextHistoryCount());
        assertEquals("X", drawn.getDrawnTextEvent(0).text);
        assertEquals("USAR", drawn.getDrawnTextEvent(1).text);
        assertEquals("Y", drawn.getDrawnTextEvent(2).text);
        assertEquals("ARMA", drawn.getDrawnTextEvent(3).text);
        assertEquals(40f, drawn.getDrawnTextEvent(0).x, 0f);
        assertEquals(100f, drawn.getDrawnTextEvent(2).x, 0f);
    }

    @Test public void captionsFitTheirButtonAtSmallAndLargeHudSizes() {
        TouchIcons icons = new TouchIcons();
        for (float radius : new float[]{18, 30, 46}) {
            Canvas canvas = canvas();
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            for (int action = 0; action < 12; action++) {
                icons.draw(canvas, paint, 50, 50, radius, action);
                icons.drawLabel(canvas, paint, 50, 50, radius, action);
            }
            ShadowCanvas drawn = shadowOf(canvas);
            for (int i = 0; i < drawn.getTextHistoryCount(); i++) {
                ShadowCanvas.TextHistoryEvent text = drawn.getDrawnTextEvent(i);
                float width = text.paint.measureText(text.text);
                assertTrue("Readable finite size", Float.isFinite(text.paint.getTextSize())
                    && text.paint.getTextSize() > 0);
                assertTrue("Caption stays inside button width", Float.isFinite(width) && width <= radius * 1.401f);
                assertEquals(Paint.Align.CENTER, text.paint.getTextAlign());
                assertTrue("Caption stays below the icon and inside button height",
                    text.y > 50 && text.y < 50 + radius);
            }
            assertEquals("Drawing restores the canvas for the next control", 1, canvas.getSaveCount());
        }
    }

    private static Canvas canvas() {
        return new Canvas(Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888));
    }
}
