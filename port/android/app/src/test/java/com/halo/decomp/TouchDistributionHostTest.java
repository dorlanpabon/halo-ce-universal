package com.halo.decomp;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {28, 35})
public class TouchDistributionHostTest {
    private static final float[][] LEGACY = {
        {900, 430}, {900, 330}, {812, 146}, {900, 146}, {800, 350}, {700, 450},
        {800, 480}, {900, 236}, {526, 480}, {612, 480}, {576, 68}, {390, 68},
        {100, 237}, {100, 335}, {51, 286}, {149, 286}, {130, 420}, {145, 110}
    };
    private static final float[][] REDISTRIBUTED = {
        {900, 445}, {900, 350}, {790, 180}, {900, 180}, {785, 350}, {260, 410},
        {790, 470}, {900, 265}, {105, 205}, {220, 205}, {576, 68}, {390, 68},
        {100, 237}, {100, 335}, {51, 286}, {149, 286}, {130, 420}, {310, 300}
    };

    @Test public void mobilePresetsRestorePositionsAndSeparateFireFromPunchWithPhoneInsets() {
        for (String text : new String[] {TouchPresets.twoThumbs(), TouchPresets.threeFingers()}) {
            TouchLayout layout = TouchLayout.importConfiguration(text).layout;
            assertRestoredPositions(layout);
            // 1612 x 720 display, using TouchControls' 4/3 pixel-to-logical scale.
            layout.bounds(1209, 540);
            layout.setMinimumRadius(34);
            layout.safeInsets(39, 137, 96.75f, 42);
            double distance = Math.hypot(layout.x(4) - layout.x(1), layout.y(4) - layout.y(1));
            assertTrue("Fire and punch targets need a clear gap",
                distance >= layout.radius(4) + layout.radius(1) + 8);
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

    @Test public void legacyAndRedistributedPresetsRestoreWithoutChangingSettingsAndOnlyOnce() {
        for (float[][] positions : new float[][][] {LEGACY, REDISTRIBUTED}) {
            TouchLayout layout = layoutAt(positions);
            String migrated = TouchPresets.upgradeMobileLayout(layout.exportConfiguration(1.75f));
            TouchLayout.Configuration upgraded = TouchLayout.importConfiguration(migrated);
            assertRestoredPositions(upgraded.layout);
            assertEquals(1.75f, upgraded.sensitivity, 0);
            assertEquals(layout.rumbleEnabled, upgraded.layout.rumbleEnabled);
            assertEquals(layout.gyroscopeEnabled, upgraded.layout.gyroscopeEnabled);
            assertEquals(layout.floatingStick, upgraded.layout.floatingStick);
            assertEquals(layout.opacity, upgraded.layout.opacity, 0);
            for (int i = 0; i < layout.size(); i++) {
                assertEquals(layout.sizeScale(i), upgraded.layout.sizeScale(i), 0);
                assertEquals(layout.opacity(i), upgraded.layout.opacity(i), 0);
                assertEquals(layout.shown(i), upgraded.layout.shown(i));
            }
            assertEquals(migrated, TouchPresets.upgradeMobileLayout(migrated));
        }
    }

    @Test public void handPositionedLayoutsRemainExactlyAsAuthored() {
        for (float[][] positions : new float[][][] {LEGACY, REDISTRIBUTED}) {
            TouchLayout layout = layoutAt(positions);
            layout.move(2, layout.x(2) - 5, layout.y(2));
            String custom = layout.exportConfiguration(1.75f);
            assertEquals(custom, TouchPresets.upgradeMobileLayout(custom));
        }
    }

    private static TouchLayout layoutAt(float[][] positions) {
        TouchLayout layout = TouchLayout.importConfiguration(TouchPresets.threeFingers()).layout;
        for (int i = 0; i < positions.length; i++) layout.move(i, positions[i][0], positions[i][1]);
        layout.rumbleEnabled = true;
        layout.gyroscopeEnabled = true;
        layout.floatingStick = false;
        layout.setOpacity(.7f);
        layout.setOpacity(4, .4f);
        layout.setSize(4, 1.4f);
        layout.setShown(6, false);
        return layout;
    }

    private static void assertRestoredPositions(TouchLayout layout) {
        assertEquals(LEGACY.length, layout.size());
        for (int i = 0; i < LEGACY.length; i++) {
            assertEquals("X of control " + i, i == 4 ? 780 : LEGACY[i][0], layout.savedX(i), .001f);
            assertEquals("Y of control " + i, LEGACY[i][1], layout.savedY(i), .001f);
        }
    }
}
