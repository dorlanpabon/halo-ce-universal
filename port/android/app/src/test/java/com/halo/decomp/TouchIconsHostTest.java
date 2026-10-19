package com.halo.decomp;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowCanvas;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {28, 35})
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class TouchIconsHostTest {
    @Test public void silhouettesDoNotAddLargeOrRepeatedActionLabels() {
        Canvas canvas = canvas();
        TouchIcons icons = icons();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int action = 0; action < 12; action++) {
            icons.draw(canvas, paint, 50, 50, 30, action);
            icons.drawLabel(canvas, paint, 50, 50, 30, action);
        }
        assertEquals("Actions use silhouettes without redundant text", 0,
            shadowOf(canvas).getTextHistoryCount());
        assertEquals("Drawing restores the canvas for the next control", 1, canvas.getSaveCount());
    }

    @Test public void contextPromptsKeepXboxLettersAndDistinctActions() {
        Canvas canvas = canvas();
        TouchIcons icons = icons();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        icons.drawCaption(canvas, paint, 40, 40, 24, "X", "USAR");
        icons.drawCaption(canvas, paint, 100, 40, 24, "Y", "ARMA");
        ShadowCanvas drawn = shadowOf(canvas);
        assertEquals(4, drawn.getTextHistoryCount());
        assertEquals("X", drawn.getDrawnTextEvent(0).text);
        assertEquals("USAR", drawn.getDrawnTextEvent(1).text);
        assertEquals("Y", drawn.getDrawnTextEvent(2).text);
        assertEquals("ARMA", drawn.getDrawnTextEvent(3).text);
        assertTrue(drawn.getDrawnTextEvent(0).x < drawn.getDrawnTextEvent(1).x);
        assertTrue(drawn.getDrawnTextEvent(2).x < drawn.getDrawnTextEvent(3).x);
    }

    @Test public void contextCaptionsStaySmallAndInsideTheirButton() {
        TouchIcons icons = icons();
        for (float radius : new float[]{18, 30, 46}) {
            Canvas canvas = canvas();
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            icons.drawCaption(canvas, paint, 80, 80, radius, "X", "USAR");
            icons.drawCaption(canvas, paint, 80, 80, radius, "White", "ARMA");
            ShadowCanvas drawn = shadowOf(canvas);
            for (int i = 0; i < drawn.getTextHistoryCount(); i++) {
                ShadowCanvas.TextHistoryEvent text = drawn.getDrawnTextEvent(i);
                float width = text.paint.measureText(text.text);
                assertTrue("Caption remains small", text.paint.getTextSize() <= radius * 0.271f);
                assertTrue("Caption starts within button", text.x >= 80 - radius);
                assertTrue("Caption ends within button", text.x + width <= 80 + radius);
                assertTrue("Caption sits below icon", text.y > 80 && text.y < 80 + radius);
            }
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void eachSilhouetteIsVisibleTransparentAndRespectsOpacity() {
        TouchIcons icons = icons();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setAlpha(127);
        for (int action = 0; action < 12; action++) {
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            icons.draw(canvas, paint, 50, 50, 30, action);
            int visible = 0;
            for (int y = 0; y < 100; y++) {
                for (int x = 0; x < 100; x++) {
                    int pixel = bitmap.getPixel(x, y);
                    if (Color.alpha(pixel) == 0)
                        continue;
                    visible++;
                    assertTrue("Silhouette remains inside button", Math.abs(x - 50) < 24
                        && Math.abs(y - 50) < 24);
                    assertTrue("HUD opacity applies to vector", Color.alpha(pixel) <= 127);
                    assertTrue("No black SVG background is painted", Color.red(pixel) >= 250
                        && Color.green(pixel) >= 250 && Color.blue(pixel) >= 250);
                }
            }
            assertTrue("Every game action has a visible pictogram: " + action, visible > 50);
            assertTrue("Corners remain transparent", Color.alpha(bitmap.getPixel(28, 28)) == 0);
            assertEquals(1, canvas.getSaveCount());
        }
    }

    @Test public void invalidActionsDoNotAlterCanvasState() {
        Canvas canvas = canvas();
        TouchIcons icons = icons();
        Paint paint = new Paint();
        for (int action : new int[]{-1, 12, Integer.MAX_VALUE})
            icons.draw(canvas, paint, 50, 50, 30, action);
        assertEquals(0, shadowOf(canvas).getTextHistoryCount());
        assertEquals(1, canvas.getSaveCount());
    }

    private static TouchIcons icons() {
        return new TouchIcons(RuntimeEnvironment.getApplication().getResources());
    }

    private static Canvas canvas() {
        return new Canvas(Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888));
    }
}
