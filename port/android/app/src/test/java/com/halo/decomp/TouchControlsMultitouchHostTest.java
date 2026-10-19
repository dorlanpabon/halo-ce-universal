package com.halo.decomp;

import android.app.Application;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowCanvas;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Exercises the production MotionEvent router; only its JNI boundary is replaced. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {28, 35},
    qualifiers = "w960dp-h540dp-land-mdpi", instrumentedPackages = "com.halo.decomp",
    shadows = TouchControlsMultitouchHostTest.NativeSink.class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class TouchControlsMultitouchHostTest {
    private TouchControls controls;
    private TouchLayout layout;
    private long downTime;

    @Implements(value = TouchControls.class)
    public static class NativeSink extends org.robolectric.shadows.ShadowView {
        static final int[] state = new int[7];
        static float lookX, lookY;
        static int lookCalls;
        static int bindingSerial;
        static int[] suppliedBindings;

        @Implementation protected static void nativeState(int lx, int ly, int rx, int ry,
                int lt, int rt, int buttons) {
            int[] values = {lx, ly, rx, ry, lt, rt, buttons};
            System.arraycopy(values, 0, state, 0, values.length);
        }
        @Implementation protected static void nativeLook(float x, float y) {
            lookX += x;
            lookY += y;
            lookCalls++;
        }
        @Implementation protected static void nativeLookReset() {
            lookX = lookY = 0;
            lookCalls = 0;
        }
        @Implementation protected static int nativeScene() { return 5; }
        @Implementation protected static int nativeGyroMode() { return 0; }
        @Implementation protected static int nativeRumble() { return 0; }
        @Implementation protected static int nativeBindings(int[] bindings) {
            if (suppliedBindings != null)
                System.arraycopy(suppliedBindings, 0, bindings, 0, bindings.length);
            return bindingSerial;
        }
    }

    @Before public void createControls() {
        NativeSink.bindingSerial = 0;
        NativeSink.suppliedBindings = null;
        HaloActivity activity = Robolectric.buildActivity(HaloActivity.class).get();
        activity.getSharedPreferences("touch-layout-v1", 0).edit().clear().commit();
        controls = new TouchControls(activity);
        ReflectionHelpers.setField(controls, "horizontalSensitivity", 1f);
        controls.layout(0, 0, 960, 540);
        ReflectionHelpers.setField(controls, "showing", true);
        layout = ReflectionHelpers.getField(controls, "layout");
        controls.reset();
        downTime = SystemClock.uptimeMillis();
    }

    @Test public void serialChurnWithUnchangedBindingsNeverRedrawsHud() {
        int[] bindings = ReflectionHelpers.getField(controls, "bindings");
        NativeSink.suppliedBindings = bindings.clone();
        for (int frame = 1; frame <= 120; frame++) {
            NativeSink.bindingSerial = frame;
            shadowOf(controls).clearWasInvalidated();
            ReflectionHelpers.callInstanceMethod(controls, "readBindings");
            assertFalse("Serial changes alone must not redraw", shadowOf(controls).wasInvalidated());
        }
        assertArrayEquals(NativeSink.suppliedBindings, bindings);
    }

    @Test public void changedBindingRedrawsOnceThenStableMappingDoesNotRedraw() {
        int[] bindings = ReflectionHelpers.getField(controls, "bindings");
        NativeSink.suppliedBindings = bindings.clone();
        NativeSink.suppliedBindings[0] = 4;
        NativeSink.bindingSerial = 1;
        shadowOf(controls).clearWasInvalidated();
        ReflectionHelpers.callInstanceMethod(controls, "readBindings");
        assertTrue("A real profile mapping change redraws", shadowOf(controls).wasInvalidated());
        assertEquals(4, bindings[0]);
        for (int frame = 2; frame <= 120; frame++) {
            NativeSink.bindingSerial = frame;
            shadowOf(controls).clearWasInvalidated();
            ReflectionHelpers.callInstanceMethod(controls, "readBindings");
            assertFalse(shadowOf(controls).wasInvalidated());
        }
    }

    @Test public void idlePollingWithSerialChurnDoesNotRedrawHud() {
        ShadowSystemClock.advanceBy(Duration.ofMillis(1000));
        ReflectionHelpers.setField(controls, "touchscreen", true);
        ReflectionHelpers.setField(controls, "deviceInputActive", true);
        ReflectionHelpers.setField(controls, "shownSince", SystemClock.uptimeMillis() - 300);
        NativeSink.suppliedBindings = ((int[]) ReflectionHelpers.getField(controls, "bindings")).clone();
        Runnable poll = ReflectionHelpers.getField(controls, "poll");
        for (int frame = 1; frame <= 120; frame++) {
            NativeSink.bindingSerial = frame;
            shadowOf(controls).clearWasInvalidated();
            poll.run();
            assertFalse("Stable scene, gyro and bindings do not invalidate", shadowOf(controls).wasInvalidated());
        }
        controls.stopDeviceInput();
    }

    @Test public void twoThumbsMoveAndDragFireSimultaneously() {
        float sx = layout.x(TouchLayout.LEFT), sy = layout.y(TouchLayout.LEFT);
        float fx = layout.x(4), fy = layout.y(4);
        event(MotionEvent.ACTION_DOWN, point(11, sx, sy));
        event(pointerDown(1), point(11, sx, sy), point(23, fx, fy));
        event(MotionEvent.ACTION_MOVE, point(11, sx + 35, sy - 18), point(23, fx + 25, fy - 20));
        assertTrue("Joystick remains held", NativeSink.state[0] > 0 && NativeSink.state[1] < 0);
        assertEquals("Fire remains held while dragging", 32767, NativeSink.state[5]);
        assertEquals(25, NativeSink.lookX, .001f);
        assertEquals(-20, NativeSink.lookY, .001f);
        event(pointerUp(1), point(11, sx + 35, sy - 18), point(23, fx + 25, fy - 20));
        assertEquals(0, NativeSink.state[5]);
        assertTrue("Lifting fire does not release movement", NativeSink.state[0] > 0);
        event(MotionEvent.ACTION_UP, point(11, sx + 35, sy - 18));
        assertReleased();
    }

    @Test public void contextualActionShowsXboxXAndUsarWhileInventorySwitchShowsYAndArma() {
        Bitmap bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        controls.draw(canvas);
        ShadowCanvas drawn = shadowOf(canvas);
        List<String> captions = new ArrayList<>();
        for (int i = 0; i < drawn.getTextHistoryCount(); i++)
            captions.add(drawn.getDrawnTextEvent(i).text);
        assertTrue("The prompt's physical X is identifiable", captions.contains("X"));
        assertTrue("The contextual action is explicit", captions.contains("USAR"));
        assertTrue("Inventory weapon switching is distinct", captions.contains("Y") && captions.contains("ARMA"));
    }

    @Test public void contextualXStaysHeldForPickupAndReleasesOnlyWhenFingerLifts() {
        float x = layout.x(2), y = layout.y(2);
        event(MotionEvent.ACTION_DOWN, point(6, x, y));
        assertEquals("Xbox X is SDL button bit 2", 1 << 2, NativeSink.state[6]);
        for (int frame = 0; frame < 45; frame++) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(34));
            event(MotionEvent.ACTION_MOVE, point(6, x + 20, y + 3));
            assertEquals("Hold remains X throughout the simulated pickup press", 1 << 2, NativeSink.state[6]);
        }
        event(MotionEvent.ACTION_UP, point(6, x + 20, y + 3));
        assertReleased();
    }

    @Test public void contextualXAndInventoryYAreSeparateAndDoNotSendFire() {
        event(MotionEvent.ACTION_DOWN, point(6, layout.x(2), layout.y(2)));
        assertEquals(1 << 2, NativeSink.state[6]);
        assertEquals(0, NativeSink.state[5]);
        event(MotionEvent.ACTION_UP, point(6, layout.x(2), layout.y(2)));
        event(MotionEvent.ACTION_DOWN, point(7, layout.x(3), layout.y(3)));
        assertEquals("Y changes the equipped inventory weapon", 1 << 3, NativeSink.state[6]);
        assertEquals(0, NativeSink.state[5]);
        event(MotionEvent.ACTION_UP, point(7, layout.x(3), layout.y(3)));
        assertReleased();
    }

    @Test public void contextualHoldCoexistsWithMovementAndFireAndIndependentRelease() {
        float sx = layout.x(TouchLayout.LEFT), sy = layout.y(TouchLayout.LEFT);
        float ux = layout.x(2), uy = layout.y(2), fx = layout.x(4), fy = layout.y(4);
        event(MotionEvent.ACTION_DOWN, point(10, sx, sy));
        event(pointerDown(1), point(10, sx, sy), point(20, ux, uy));
        event(pointerDown(2), point(10, sx, sy), point(20, ux, uy), point(30, fx, fy));
        event(MotionEvent.ACTION_MOVE, point(30, fx + 20, fy), point(20, ux, uy), point(10, sx + 35, sy));
        assertTrue(NativeSink.state[0] > 0);
        assertEquals(32767, NativeSink.state[5]);
        assertEquals(1 << 2, NativeSink.state[6]);
        event(pointerUp(1), point(30, fx + 20, fy), point(20, ux, uy), point(10, sx + 35, sy));
        assertEquals(0, NativeSink.state[6]);
        assertEquals(32767, NativeSink.state[5]);
        assertTrue(NativeSink.state[0] > 0);
        event(MotionEvent.ACTION_CANCEL, point(30, fx + 20, fy), point(10, sx + 35, sy));
        assertReleased();
    }

    @Test public void threeFingersMoveAimAndFireWithReorderedPointerIndices() {
        float sx = layout.x(TouchLayout.LEFT), sy = layout.y(TouchLayout.LEFT);
        float fx = layout.x(4), fy = layout.y(4);
        event(MotionEvent.ACTION_DOWN, point(9, sx, sy));
        event(pointerDown(1), point(9, sx, sy), point(27, 570, 240));
        event(pointerDown(2), point(9, sx, sy), point(27, 570, 240), point(4, fx, fy));
        event(MotionEvent.ACTION_MOVE, point(4, fx + 20, fy + 2), point(9, sx - 30, sy), point(27, 590, 225));
        assertTrue(NativeSink.state[0] < 0);
        assertEquals(32767, NativeSink.state[5]);
        assertEquals("Independent aiming wins over fire dragging", 20, NativeSink.lookX, .001f);
        assertEquals(-15, NativeSink.lookY, .001f);
        event(pointerUp(0), point(4, fx + 20, fy + 2), point(9, sx - 30, sy), point(27, 590, 225));
        assertEquals(0, NativeSink.state[5]);
        assertTrue(NativeSink.state[0] < 0);
        event(MotionEvent.ACTION_MOVE, point(27, 605, 230), point(9, sx - 40, sy));
        assertEquals(35, NativeSink.lookX, .001f);
        event(MotionEvent.ACTION_CANCEL, point(27, 605, 230), point(9, sx - 40, sy));
        assertReleased();
    }

    @Test public void threeFingersMoveAimAndUseTheRealLeftFireIndependently() {
        layout.setShown(TouchLayout.FIRE_LEFT, true);
        assertEquals(17, layout.type(TouchLayout.FIRE_LEFT));
        float sx = layout.x(TouchLayout.LEFT), sy = layout.y(TouchLayout.LEFT);
        float fx = layout.x(TouchLayout.FIRE_LEFT), fy = layout.y(TouchLayout.FIRE_LEFT);
        event(MotionEvent.ACTION_DOWN, point(9, sx, sy));
        event(pointerDown(1), point(9, sx, sy), point(27, 570, 240));
        event(pointerDown(2), point(9, sx, sy), point(27, 570, 240), point(4, fx, fy));
        event(MotionEvent.ACTION_MOVE, point(4, fx, fy), point(9, sx + 30, sy), point(27, 590, 225));
        assertTrue("Movement continues while the left fire is held", NativeSink.state[0] > 0);
        assertEquals(32767, NativeSink.state[5]);
        assertEquals("The separate right finger controls horizontal aim", 20, NativeSink.lookX, .001f);
        assertEquals(-15, NativeSink.lookY, .001f);
        event(pointerUp(0), point(4, fx, fy), point(9, sx + 30, sy), point(27, 590, 225));
        assertEquals(0, NativeSink.state[5]);
        assertTrue(NativeSink.state[0] > 0);
        event(MotionEvent.ACTION_MOVE, point(27, 605, 230), point(9, sx + 40, sy));
        assertEquals(35, NativeSink.lookX, .001f);
        event(MotionEvent.ACTION_CANCEL, point(27, 605, 230), point(9, sx + 40, sy));
        assertReleased();
    }

    @Test public void rightAndLeftFireKeepTriggerHeldUntilBothFingersLiftInEitherOrder() {
        layout.setShown(TouchLayout.FIRE_LEFT, true);
        float rx = layout.x(4), ry = layout.y(4);
        float lx = layout.x(TouchLayout.FIRE_LEFT), ly = layout.y(TouchLayout.FIRE_LEFT);
        for (boolean rightFirst : new boolean[] {true, false}) {
            controls.reset();
            event(MotionEvent.ACTION_DOWN, point(3, rx, ry));
            event(pointerDown(1), point(3, rx, ry), point(17, lx, ly));
            assertEquals(32767, NativeSink.state[5]);
            event(pointerUp(rightFirst ? 0 : 1), point(3, rx, ry), point(17, lx, ly));
            assertEquals("Either fire button alone keeps RT held", 32767, NativeSink.state[5]);
            event(MotionEvent.ACTION_UP, rightFirst ? point(17, lx, ly) : point(3, rx, ry));
            assertReleased();
        }
    }

    @Test public void generalLeftFireSwitchPersistsOnlyItsOwnVisibilityChoice() {
        boolean[] shown = new boolean[layout.size()];
        for (int i = 0; i < shown.length; i++) shown[i] = layout.shown(i);
        ReflectionHelpers.callInstanceMethod(controls, "showGeneral");
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        try {
            Switch leftFire = findLeftFire(dialog.getWindow().getDecorView());
            assertNotNull("General exposes the left fire switch", leftFire);
            assertFalse(leftFire.isChecked());
            for (boolean enabled : new boolean[] {true, false}) {
                leftFire.performClick();
                assertEquals(enabled, layout.shown(TouchLayout.FIRE_LEFT));
                String saved = controls.getContext().getSharedPreferences("touch-layout-v1", 0)
                    .getString("configuration", null);
                assertNotNull(saved);
                TouchLayout restored = TouchLayout.importConfiguration(saved).layout;
                for (int i = 0; i < shown.length; i++)
                    assertEquals("Visibility of control " + i,
                        i == TouchLayout.FIRE_LEFT ? enabled : shown[i], restored.shown(i));
            }
        } finally {
            dialog.dismiss();
        }
    }

    @Test public void aimFingerTakesOverFromDraggedFireAndReturnsWithoutJump() {
        float fx = layout.x(4), fy = layout.y(4);
        event(MotionEvent.ACTION_DOWN, point(8, fx, fy));
        event(MotionEvent.ACTION_MOVE, point(8, fx + 20, fy));
        event(pointerDown(1), point(8, fx + 20, fy), point(30, 570, 240));
        event(MotionEvent.ACTION_MOVE, point(8, fx + 50, fy), point(30, 585, 235));
        assertEquals("A separate aiming finger can take over", 35, NativeSink.lookX, .001f);
        assertEquals(32767, NativeSink.state[5]);
        event(pointerUp(1), point(8, fx + 50, fy), point(30, 585, 235));
        event(MotionEvent.ACTION_MOVE, point(8, fx + 65, fy));
        assertEquals("Return to fire begins at its latest position", 50, NativeSink.lookX, .001f);
        assertEquals(32767, NativeSink.state[5]);
    }

    @Test public void twoAimingFingersTransferOnLiftWithoutCameraJump() {
        event(MotionEvent.ACTION_DOWN, point(5, 570, 240));
        event(pointerDown(1), point(5, 570, 240), point(29, 650, 230));
        event(MotionEvent.ACTION_MOVE, point(29, 660, 230), point(5, 590, 240));
        assertEquals(10, NativeSink.lookX, .001f);
        event(pointerUp(0), point(29, 660, 230), point(5, 590, 240));
        event(MotionEvent.ACTION_MOVE, point(5, 600, 240));
        assertEquals(20, NativeSink.lookX, .001f);
    }

    @Test public void immediateAimLiftDoesNotRepeatEarlierFireDrag() {
        float fx = layout.x(4), fy = layout.y(4);
        event(MotionEvent.ACTION_DOWN, point(8, fx, fy));
        event(MotionEvent.ACTION_MOVE, point(8, fx + 20, fy));
        event(pointerDown(1), point(8, fx + 20, fy), point(30, 570, 240));
        event(pointerUp(1), point(8, fx + 20, fy), point(30, 570, 240));
        event(MotionEvent.ACTION_MOVE, point(8, fx + 35, fy));
        assertEquals(35, NativeSink.lookX, .001f);
        assertEquals(32767, NativeSink.state[5]);
    }

    @Test public void aimOnlyMovementPublishesEveryDeltaWithoutRedrawingUnchangedHud() {
        event(MotionEvent.ACTION_DOWN, point(5, 570, 240));
        shadowOf(controls).clearWasInvalidated();
        event(MotionEvent.ACTION_MOVE, point(5, 580, 245));
        event(MotionEvent.ACTION_MOVE, point(5, 590, 250));
        assertEquals(20, NativeSink.lookX, .001f);
        assertEquals(10, NativeSink.lookY, .001f);
        assertEquals(2, NativeSink.lookCalls);
        assertFalse("Only the game camera changes", shadowOf(controls).wasInvalidated());
    }

    @Test public void fractionalAimStepsPreserveTheSameTotalAsOneMove() {
        event(MotionEvent.ACTION_DOWN, point(5, 570, 240));
        event(MotionEvent.ACTION_MOVE, point(5, 573.75f, 236.25f));
        float singleX = NativeSink.lookX, singleY = NativeSink.lookY;
        assertEquals(3.75f, singleX, .0001f);
        assertEquals(-3.75f, singleY, .0001f);
        assertEquals(1, NativeSink.lookCalls);
        event(MotionEvent.ACTION_UP, point(5, 573.75f, 236.25f));

        controls.reset();
        event(MotionEvent.ACTION_DOWN, point(5, 570, 240));
        for (int step = 1; step <= 15; step++)
            event(MotionEvent.ACTION_MOVE, point(5, 570 + step * .25f, 240 - step * .25f));
        assertEquals("Quarter-pixel horizontal motion is never rounded away", singleX, NativeSink.lookX, .0001f);
        assertEquals("Quarter-pixel vertical motion is never rounded away", singleY, NativeSink.lookY, .0001f);
        assertEquals(15, NativeSink.lookCalls);
        event(MotionEvent.ACTION_UP, point(5, 573.75f, 236.25f));
        assertReleased();
    }

    @Test public void joystickChangeStillRedrawsAndLiftingItKeepsFireHeld() {
        float sx = layout.x(TouchLayout.LEFT), sy = layout.y(TouchLayout.LEFT);
        float fx = layout.x(4), fy = layout.y(4);
        event(MotionEvent.ACTION_DOWN, point(11, sx, sy));
        event(pointerDown(1), point(11, sx, sy), point(23, fx, fy));
        shadowOf(controls).clearWasInvalidated();
        event(MotionEvent.ACTION_MOVE, point(11, sx + 35, sy), point(23, fx, fy));
        assertTrue(shadowOf(controls).wasInvalidated());
        event(pointerUp(0), point(11, sx + 35, sy), point(23, fx, fy));
        assertEquals(0, NativeSink.state[0]);
        assertEquals(0, NativeSink.state[1]);
        assertEquals(32767, NativeSink.state[5]);
    }

    @Test public void stoppingInputReleasesEveryHeldAction() {
        float sx = layout.x(TouchLayout.LEFT), sy = layout.y(TouchLayout.LEFT);
        float fx = layout.x(4), fy = layout.y(4);
        event(MotionEvent.ACTION_DOWN, point(11, sx, sy));
        event(pointerDown(1), point(11, sx, sy), point(23, fx, fy));
        event(MotionEvent.ACTION_MOVE, point(11, sx + 35, sy), point(23, fx + 20, fy));
        controls.stopDeviceInput();
        assertReleased();
        assertEquals(-1, (int) ReflectionHelpers.getField(controls, "lookPointer"));
    }

    @Test public void duplicateFireKeepsTriggerUntilLastFingerLifts() {
        int duplicate = layout.duplicate(4);
        layout.move(duplicate, 620, 110);
        event(MotionEvent.ACTION_DOWN, point(3, layout.x(4), layout.y(4)));
        event(pointerDown(1), point(3, layout.x(4), layout.y(4)), point(17, 620, 110));
        event(pointerUp(0), point(3, layout.x(4), layout.y(4)), point(17, 620, 110));
        assertEquals(32767, NativeSink.state[5]);
        event(MotionEvent.ACTION_UP, point(17, 620, 110));
        assertReleased();
    }

    @Test public void cancellationClearsMovementFireAndAiming() {
        float sx = layout.x(TouchLayout.LEFT), sy = layout.y(TouchLayout.LEFT);
        float fx = layout.x(4), fy = layout.y(4);
        event(MotionEvent.ACTION_DOWN, point(10, sx, sy));
        event(pointerDown(1), point(10, sx, sy), point(20, fx, fy));
        event(MotionEvent.ACTION_MOVE, point(10, sx + 30, sy), point(20, fx + 20, fy));
        event(MotionEvent.ACTION_CANCEL, point(10, sx + 30, sy), point(20, fx + 20, fy));
        assertReleased();
        assertEquals(-1, (int) ReflectionHelpers.getField(controls, "lookPointer"));
    }

    private void assertReleased() {
        assertArrayEquals(new int[7], NativeSink.state);
    }

    private static Switch findLeftFire(View view) {
        if (view instanceof Switch && "Disparo izquierdo".contentEquals(((Switch) view).getText()))
            return (Switch) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Switch found = findLeftFire(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int pointerDown(int index) {
        return MotionEvent.ACTION_POINTER_DOWN | index << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    }

    private static int pointerUp(int index) {
        return MotionEvent.ACTION_POINTER_UP | index << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    }

    private static float[] point(int id, float x, float y) { return new float[] {id, x, y}; }

    private void event(int action, float[]... points) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[points.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[points.length];
        for (int i = 0; i < points.length; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = (int) points[i][0];
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = points[i][1];
            coords[i].y = points[i][2];
            coords[i].pressure = coords[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
            points.length, properties, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { assertTrue(controls.onTouchEvent(event)); }
        finally { event.recycle(); }
    }
}
