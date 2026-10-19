package com.halo.decomp;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {28, 35})
public class TouchDistributionHostTest {
    @Test public void mobilePresetsHaveSeparatedVisibleTargetsWithPhoneInsets() {
        for (String text : new String[] {TouchPresets.twoThumbs(), TouchPresets.threeFingers()}) {
            TouchLayout layout = TouchLayout.importConfiguration(text).layout;
            layout.bounds(1209, 540);
            layout.setMinimumRadius(34);
            layout.safeInsets(39, 137, 96.75f, 42);
            for (int i = 0; i < layout.size(); i++) {
                if (!layout.shown(i)) continue;
                for (int j = i + 1; j < layout.size(); j++) {
                    if (!layout.shown(j)) continue;
                    double distance = Math.hypot(layout.x(i) - layout.x(j), layout.y(i) - layout.y(j));
                    assertTrue("Targets overlap: " + i + "/" + j,
                        distance >= layout.radius(i) + layout.radius(j) + 2);
                }
            }
        }
    }

    @Test public void draggingUsesDisplayPositionWithoutAJumpAndKeepsAuthoredCoordinates() {
        TouchLayout layout = TouchLayout.importConfiguration(TouchPresets.twoThumbs()).layout;
        float saved = layout.savedX(3);
        layout.bounds(1209, 540);
        layout.safeInsets(39, 137, 96.75f, 42);
        float x = layout.x(3), y = layout.y(3);
        layout.move(3, x, y);
        assertEquals(x, layout.x(3), .001f);
        assertEquals(saved, layout.savedX(3), .001f);
        layout.move(3, x - 20, y + 15);
        assertEquals(x - 20, layout.x(3), .001f);
        assertEquals(y + 15, layout.y(3), .001f);
    }

    @Test public void unchangedOldPresetMigratesButCustomPositionsAndSensitivitySurvive() {
        TouchLayout layout = TouchLayout.importConfiguration(TouchPresets.twoThumbs()).layout;
        float[][] old = ReflectionHelpers.getStaticField(TouchPresets.class, "LEGACY_PLACES");
        for (int i = 0; i < old.length; i++) layout.move(i, old[i][0], old[i][1]);
        layout.setOpacity(4, .4f);
        String text = layout.exportConfiguration(1.75f);
        TouchLayout.Configuration upgraded = TouchLayout.importConfiguration(TouchPresets.upgradeMobileLayout(text));
        assertEquals(1.75f, upgraded.sensitivity, 0);
        assertEquals(.4f, upgraded.layout.opacity(4), 0);
        assertTrue(upgraded.layout.savedX(5) < 400);
        layout.move(2, layout.x(2) - 5, layout.y(2));
        String custom = layout.exportConfiguration(1.75f);
        assertEquals(custom, TouchPresets.upgradeMobileLayout(custom));
    }
}
