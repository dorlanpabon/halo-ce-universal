package com.halo.decomp;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Real save/restore UI and lifecycle, with the native engine boundary replaced. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {28, 35}, qualifiers = "w960dp-h540dp-land-mdpi",
    instrumentedPackages = {"com.halo.decomp", "org.libsdl.app"},
    shadows = {PauseMenuHostTest.EngineBoundary.class, TouchControlsMultitouchHostTest.NativeSink.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class CheckpointUiHostTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private HaloActivity activity;
    private TouchControls controls;

    @Before public void prepare() throws Exception {
        PauseMenuHostTest.EngineBoundary.pauses = PauseMenuHostTest.EngineBoundary.resumes = 0;
        activity = Robolectric.buildActivity(HaloActivity.class).get();
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        controls = new TouchControls(activity);
        root.addView(controls);
        controls.layout(0, 0, 960, 540);
        ReflectionHelpers.setField(controls, "showing", true);
        ReflectionHelpers.setField(activity, "touchControls", controls);
        ReflectionHelpers.setField(activity, "activityResumed", true);
        ReflectionHelpers.setField(activity, "checkpointStore", new CheckpointStore(temporary.newFolder(),
            name -> new ByteArrayInputStream(name.getBytes(StandardCharsets.US_ASCII))));
    }

    @After public void stopWork() throws Exception {
        ReflectionHelpers.setField(activity, "activityResumed", false);
        ReflectionHelpers.setField(activity, "checkpointExiting", true);
        ReflectionHelpers.setField(activity, "checkpointRequestId", null);
        Thread worker = ReflectionHelpers.getField(activity, "checkpointWorker");
        ReflectionHelpers.callInstanceMethod(activity, "stopCheckpointWatch");
        if (worker != null)
            worker.join(2000);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        if (dialog != null && dialog.isShowing())
            dialog.dismiss();
        controls.stopDeviceInput();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private Button button(View view, String text) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(text))
            return (Button) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Button result = button(((ViewGroup) view).getChildAt(i), text);
                if (result != null)
                    return result;
            }
        return null;
    }

    private String save() {
        activity.showTouchPauseMenu();
        AlertDialog pause = ShadowAlertDialog.getLatestAlertDialog();
        Button save = button(pause.getWindow().getDecorView(), "Guardar y salir");
        assertNotNull(save);
        save.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse("No dialog may suspend SDL during the save", pause.isShowing());
        assertNotNull(ReflectionHelpers.getField(activity, "checkpointProgress"));
        return ReflectionHelpers.getField(activity, "checkpointRequestId");
    }

    private String restore() {
        String id = CheckpointStore.newId();
        ReflectionHelpers.callInstanceMethod(activity, "beginCheckpointOperation",
            ReflectionHelpers.ClassParameter.from(String.class, id),
            ReflectionHelpers.ClassParameter.from(boolean.class, true));
        return id;
    }

    @Test public void saveKeepsPauseButtonsAndExplainsCheckpointSemantics() {
        activity.showTouchPauseMenu();
        AlertDialog pause = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(shadowOf(pause).getMessage().toString().contains("no la posición exacta"));
        assertNotNull(button(pause.getWindow().getDecorView(), "Guardar y salir"));
        assertEquals("Reanudar", pause.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        assertEquals("Ajustes de controles", pause.getButton(AlertDialog.BUTTON_NEUTRAL).getText().toString());
        assertEquals("Atrás", pause.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
    }

    @Test public void busySaveBlocksInputAndCannotStartTwice() {
        String id = save();
        activity.startCheckpointSave();
        activity.onResume();
        activity.onWindowFocusChanged(true);
        assertEquals(id, ReflectionHelpers.getField(activity, "checkpointRequestId"));
        assertFalse((boolean) ReflectionHelpers.getField(controls, "deviceInputActive"));
        assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)));
        assertNull(ReflectionHelpers.getField(activity, "touchPauseDialog"));
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test public void onlyMatchingSavedAcknowledgementExitsWithoutRestart() {
        String id = save();
        activity.completeCheckpoint(CheckpointStore.newId(), "saved");
        assertNull(shadowOf(activity).getNextStartedActivity());
        activity.completeCheckpoint(id, "saved");
        Intent exit = shadowOf(activity).getNextStartedActivity();
        assertNotNull(exit);
        assertEquals(ProfileRestartActivity.class.getName(), exit.getComponent().getClassName());
        assertTrue(exit.getBooleanExtra("exit-only", false));
        assertFalse((boolean) ReflectionHelpers.getField(controls, "deviceInputActive"));
    }

    @Test public void missingCheckpointKeepsGamePausedAndOffersReturn() {
        String id = save();
        activity.completeCheckpoint(id, "no_checkpoint");
        AlertDialog error = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("No se pudo guardar", shadowOf(error).getTitle());
        assertTrue(shadowOf(error).getMessage().toString().contains("Todavía no hay un punto de control"));
        assertNull(shadowOf(activity).getNextStartedActivity());
        error.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Pausa", shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle());
    }

    @Test public void failedSaveNeverReportsSuccessOrExits() {
        String id = save();
        activity.completeCheckpoint(id, "io_error");
        assertEquals("No se pudo guardar", shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle());
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertNull(ReflectionHelpers.getField(activity, "checkpointRequestId"));
        assertFalse((boolean) ReflectionHelpers.getField(controls, "deviceInputActive"));
    }

    @Test public void timeoutRejectsLateSaveAcknowledgement() {
        String id = save();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15));
        AlertDialog error = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(shadowOf(error).getMessage().toString().contains("No se cerró la partida"));
        activity.completeCheckpoint(id, "saved");
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertSame(error, ShadowAlertDialog.getLatestAlertDialog());
    }

    @Test public void restoreAcknowledgementClearsProgressWithoutExiting() {
        String id = restore();
        activity.completeCheckpoint(id, "resumed");
        assertNull(ReflectionHelpers.getField(activity, "checkpointProgress"));
        assertNull(ReflectionHelpers.getField(activity, "checkpointRequestId"));
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test public void backgroundRestoreDoesNotActivateInput() {
        String id = restore();
        activity.onPause();
        activity.completeCheckpoint(id, "resumed");
        assertFalse((boolean) ReflectionHelpers.getField(controls, "deviceInputActive"));
        assertEquals(0, PauseMenuHostTest.EngineBoundary.resumes);
    }

    @Test public void incompatibleRestoreExplainsErrorAndCannotSilentlyResumeNewGame() {
        String id = restore();
        activity.completeCheckpoint(id, "incompatible");
        AlertDialog error = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("No se pudo continuar", shadowOf(error).getTitle());
        assertTrue(shadowOf(error).getMessage().toString().contains("El archivo se conserva"));
        activity.onWindowFocusChanged(true);
        assertFalse((boolean) ReflectionHelpers.getField(controls, "deviceInputActive"));
        error.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        Intent exit = shadowOf(activity).getNextStartedActivity();
        assertNotNull(exit);
        assertTrue(exit.getBooleanExtra("exit-only", false));
    }

    @Test public void restoreTimeoutPreservesSlotAndRejectsLateAcknowledgement() {
        String id = restore();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(120));
        AlertDialog error = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("No se pudo continuar", shadowOf(error).getTitle());
        activity.completeCheckpoint(id, "resumed");
        assertSame(error, ShadowAlertDialog.getLatestAlertDialog());
        assertNotNull(ReflectionHelpers.getField(activity, "checkpointErrorDialog"));
        assertNull(shadowOf(activity).getNextStartedActivity());
    }
}
