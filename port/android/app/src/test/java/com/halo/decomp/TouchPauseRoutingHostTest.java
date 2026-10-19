package com.halo.decomp;

import android.app.Application;
import android.os.SystemClock;
import android.view.MotionEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Pause navigation must work even when a demo has no game pause-menu tags. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {28, 35},
    qualifiers = "w960dp-h540dp-land-mdpi", instrumentedPackages = "com.halo.decomp",
    shadows = TouchControlsMultitouchHostTest.NativeSink.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class TouchPauseRoutingHostTest {
    public static class RecordingActivity extends HaloActivity {
        int opened;
        @Override void showTouchPauseMenu() { opened++; }
    }
    private RecordingActivity activity;
    private TouchControls controls;
    private TouchLayout layout;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(RecordingActivity.class).get();
        activity.getSharedPreferences("touch-layout-v1", 0).edit().clear().commit();
        controls = new TouchControls(activity);
        controls.layout(0, 0, 960, 540);
        ReflectionHelpers.setField(controls, "showing", true);
        layout = ReflectionHelpers.getField(controls, "layout");
        controls.reset();
    }

    @Test public void pauseAndBackOpenNavigationOnReleaseWithoutXboxButtonBits() {
        for (int button : new int[] {10, 11}) {
            send(MotionEvent.ACTION_DOWN, layout.x(button), layout.y(button));
            assertEquals(0, TouchControlsMultitouchHostTest.NativeSink.state[6]);
            int before = activity.opened;
            send(MotionEvent.ACTION_UP, layout.x(button), layout.y(button));
            assertEquals(before + 1, activity.opened);
            assertEquals(0, TouchControlsMultitouchHostTest.NativeSink.state[6]);
        }
    }

    @Test public void cancelledPausePressDoesNotOpenNavigation() {
        send(MotionEvent.ACTION_DOWN, layout.x(10), layout.y(10));
        send(MotionEvent.ACTION_CANCEL, layout.x(10), layout.y(10));
        assertEquals(0, activity.opened);
    }

    @Test public void SwipeBeginningOnPauseDoesNotOpenNavigation() {
        float x = layout.x(10), y = layout.y(10);
        send(MotionEvent.ACTION_DOWN, x, y);
        send(MotionEvent.ACTION_MOVE, x + 60, y + 60);
        send(MotionEvent.ACTION_UP, x + 60, y + 60);
        assertEquals(0, activity.opened);
    }

    private void send(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        controls.onTouchEvent(event);
        event.recycle();
    }
}
