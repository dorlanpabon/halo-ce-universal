package com.halo.decomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {28, 35}, qualifiers = "w800dp-h360dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class DataPackagesHostTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    public static class TestActivity extends Activity {
        File root;
        @Override public File getExternalFilesDir(String type) { return root; }
    }
    private TestActivity activity;

    @Before public void prepare() throws Exception {
        activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        activity.root = temporary.newFolder();
    }

    @After public void close() {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        if (dialog != null)
            dialog.dismiss();
        activity.finish();
    }

    @Test public void onlyAcceptsHttpsSourcesWithoutCredentialsOrFragments() {
        assertTrue(DataPackages.validAddress("https://example.com/data.zip?token=abc%2Fdef"));
        for (String address : new String[] {"", "http://example.com/a.iso", "file:///a.zip",
                "https://user:pass@example.com/a.zip", "https://example.com/a.zip#part",
                "https://example.com:70000/a", "https://bad host/a", "https://example.com/\na"})
            assertFalse(address, DataPackages.validAddress(address));
    }

    @Test public void invalidSourceStaysInFormWithoutStartingAnyDownload() {
        DataPackages.show(activity, () -> fail("must not launch game"), dialog -> dialog.show());
        AlertDialog form = ShadowAlertDialog.getLatestAlertDialog();
        EditText field = find(form.getWindow().getDecorView(), EditText.class, null);
        assertNotNull(field);
        assertEquals("", field.getText().toString());
        field.setText("http://example.com/data.zip");
        form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(form.isShowing());
        assertNotNull(field.getError());
        assertFalse(DataPackages.isBusy());
        assertFalse(new File(activity.getCacheDir(), "game-data.partial").exists());
    }

    @Test public void installedMapsAreNotReplacedAndExistingFilesStayUntouched() throws Exception {
        File maps = new File(activity.root, "maps");
        assertTrue(maps.mkdir());
        File ui = new File(maps, "ui.map");
        Files.write(ui.toPath(), "existing map".getBytes(StandardCharsets.US_ASCII));
        DataPackages.show(activity, () -> fail("must not restart game"), dialog -> dialog.show());
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNull(find(dialog.getWindow().getDecorView(), EditText.class, null));
        assertEquals("existing map", new String(Files.readAllBytes(ui.toPath()), StandardCharsets.US_ASCII));
        assertFalse(DataPackages.isBusy());
    }

    @Test public void launcherOffersBothDownloadActionsInScrollableContent() throws Exception {
        LauncherActivity launcher = Robolectric.buildActivity(LauncherActivity.class).get();
        ReflectionHelpers.setField(launcher, "dataRoot", activity.root);
        ReflectionHelpers.callInstanceMethod(launcher, "buildInterface");
        View view = launcher.getWindow().getDecorView();
        assertNotNull(find(view, ScrollView.class, null));
        Button data = find(view, Button.class, "Descargar paquete de datos");
        assertNotNull(data);
        assertNotNull(find(view, Button.class, "Buscar actualización de la APK"));
        data.performClick();
        assertNotNull(find(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(), EditText.class, null));
        launcher.finish();
    }

    private static <T extends View> T find(View view, Class<T> type, String text) {
        if (type.isInstance(view) && (text == null || view instanceof Button
                && text.contentEquals(((Button) view).getText())))
            return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = find(group.getChildAt(i), type, text);
                if (found != null)
                    return found;
            }
        }
        return null;
    }
}
