package com.halo.decomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.SystemClock;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Imports a player's remotely hosted game data without bundling or replacing commercial maps. */
final class DataPackages {
    private static final AtomicBoolean busy = new AtomicBoolean();
    private static final long MAX_DOWNLOAD = 8L * 1024 * 1024 * 1024;

    private DataPackages() {}

    static boolean isBusy() { return busy.get(); }

    static void show(Activity activity, Runnable ready, Consumer<AlertDialog> open) {
        File root = activity.getExternalFilesDir(null);
        if (root == null || new File(root, "maps/ui.map").isFile() || busy.get()) {
            message(activity, open, root == null ? "El almacenamiento no está disponible."
                : busy.get() ? "Ya hay una descarga de datos en curso."
                : "Los datos ya están instalados. Tus mapas y partidas se conservan.");
            return;
        }
        LinearLayout content = column(activity);
        TextView help = new TextView(activity);
        help.setText("Pega una URL HTTPS de tus datos de Halo Xbox: ZIP con una carpeta maps, "
            + "o imagen ISO/XISO. La APK no incluye los archivos comerciales del juego. "
            + "La descarga se instala directamente en este teléfono.");
        content.addView(help);
        EditText address = new EditText(activity);
        address.setHint("https://…/halo-datos.zip");
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        content.addView(address);
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("Descargar paquete de datos").setView(content)
            .setNegativeButton("Cancelar", null).setPositiveButton("Descargar", null).create();
        open.accept(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String url = address.getText().toString().trim();
            if (!validAddress(url)) {
                address.setError("Escribe una URL HTTPS válida, sin usuario ni contraseña.");
                return;
            }
            if (busy.compareAndSet(false, true)) {
                download(activity, root, url, ready, open);
                dialog.dismiss();
            }
        });
    }

    static boolean validAddress(String text) {
        try {
            URI uri = new URI(text);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && !uri.getHost().isEmpty() && uri.getRawUserInfo() == null && uri.getFragment() == null
                && uri.getPort() <= 65535;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static void download(Activity activity, File root, String url, Runnable ready,
            Consumer<AlertDialog> open) {
        AtomicBoolean canceled = new AtomicBoolean();
        LinearLayout content = column(activity);
        TextView status = new TextView(activity);
        status.setText("Conectando… Mantén la aplicación abierta.");
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setIndeterminate(true);
        content.addView(status);
        content.addView(bar);
        AlertDialog progress = new AlertDialog.Builder(activity)
            .setTitle("Descargando datos de Halo").setView(content)
            .setNegativeButton("Cancelar", (d, w) -> canceled.set(true)).create();
        progress.setCanceledOnTouchOutside(false);
        progress.setOnCancelListener(d -> canceled.set(true));
        open.accept(progress);
        new Thread(() -> {
            File archive = new File(activity.getCacheDir(), "game-data.partial");
            long[] lastReport = {0};
            String failure = null;
            boolean installed = false;
            try {
                ControlsDownload.transfer(url, archive, MAX_DOWNLOAD, canceled, (done, total) ->
                    report(activity, progress, canceled, status, bar, "Descargando", done, total, lastReport));
                String map = GameDataPackage.install(archive, root, canceled, (file, done, total) ->
                    report(activity, progress, canceled, status, bar, "Instalando " + file, done, total, lastReport));
                installed = true;
                // The demo's UI cannot select its campaign. An existing player's startup script is never replaced.
                if ("demob30".equals(map)) {
                    File init = new File(root, "init.txt");
                    if (init.createNewFile()) {
                        try (FileOutputStream out = new FileOutputStream(init)) {
                            out.write("map_name levels\\demob30\\demob30\n".getBytes(StandardCharsets.US_ASCII));
                        }
                    }
                }
            } catch (Exception e) {
                failure = e instanceof IOException ? e.getMessage() : "No se pudo instalar el paquete.";
            } finally {
                archive.delete();
                busy.set(false);
            }
            String error = failure;
            boolean success = installed;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed())
                    return;
                if (success) {
                    AlertDialog complete = new AlertDialog.Builder(activity)
                        .setTitle("Datos instalados").setMessage(error == null ? "El paquete está listo para jugar."
                            : "Los mapas están instalados, pero no se pudo preparar el inicio de la demo.\n" + error)
                        .setPositiveButton("Jugar", (d, w) -> ready.run()).create();
                    open.accept(complete);
                } else if (!canceled.get()) {
                    message(activity, open, "No se pudo instalar el paquete.\n" + error
                        + "\nPuedes volver a intentarlo.");
                }
                progress.dismiss();
            });
        }, "halo game data").start();
    }

    private static void report(Activity activity, AlertDialog progress, AtomicBoolean canceled,
            TextView status, ProgressBar bar, String text, long done, long total, long[] lastReport) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            canceled.set(true);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastReport[0] < 100 && done != total)
            return;
        lastReport[0] = now;
        activity.runOnUiThread(() -> {
            if (!progress.isShowing() || canceled.get())
                return;
            status.setText(text + " · " + (done >> 20) + (total > 0 ? " / " + (total >> 20) : "") + " MB");
            bar.setIndeterminate(total <= 0);
            if (total > 0)
                bar.setProgress((int) (done * 1000 / total));
        });
    }

    private static LinearLayout column(Activity activity) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding / 2, padding, 0);
        return content;
    }

    private static void message(Activity activity, Consumer<AlertDialog> open, String text) {
        open.accept(new AlertDialog.Builder(activity).setTitle("Paquete de datos")
            .setMessage(text).setPositiveButton("Aceptar", null).create());
    }
}
