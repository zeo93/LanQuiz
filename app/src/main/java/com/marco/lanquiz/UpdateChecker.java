package com.marco.lanquiz;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Aggiornamenti dell'app: la versione pubblicata è l'ultima Release del
 * repository GitHub, e l'APK è il suo allegato .apk. Nessun server da tenere
 * acceso: basta pubblicare una release con il tag della nuova versione.
 */
public class UpdateChecker {

    public static final String REPO = "zeo93/LanQuiz";
    private static final String API_LATEST =
            "https://api.github.com/repos/" + REPO + "/releases/latest";

    public static class UpdateInfo {
        public String version;
        public String changelog;
        public String apkUrl;
    }

    public interface Callback {
        void onResult(UpdateInfo update, String error);
    }

    public static String currentVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** Controllo silenzioso all'avvio: se c'è una versione nuova la propone. */
    public static void checkOnStartup(Activity activity) {
        if (!Store.updateCheck(activity)) {
            return;
        }
        checkAsync(activity, (update, error) -> {
            if (update != null && !activity.isFinishing()) {
                NotificationHelper.ensureChannels(activity);
                NotificationHelper.notifyUpdate(activity, update.version);
                showUpdateDialog(activity, update);
            }
        });
    }

    public static void checkAsync(Context context, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                UpdateInfo info = fetchLatest(context);
                boolean newer = isNewer(info.version, currentVersion(context)) && info.apkUrl != null;
                main.post(() -> callback.onResult(newer ? info : null, null));
            } catch (Exception e) {
                main.post(() -> callback.onResult(null, e.getMessage()));
            }
        }).start();
    }

    private static UpdateInfo fetchLatest(Context context) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_LATEST).openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new Exception(code == 404
                    ? context.getString(R.string.nessuna_release) : "HTTP " + code);
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
        }
        conn.disconnect();
        JSONObject o = new JSONObject(sb.toString());
        UpdateInfo info = new UpdateInfo();
        info.version = o.optString("tag_name", "").replaceFirst("^v", "");
        info.changelog = o.optString("body", "");
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name", "").endsWith(".apk")) {
                    info.apkUrl = a.optString("browser_download_url");
                    break;
                }
            }
        }
        return info;
    }

    /** Confronto fra versioni tipo 1.2.3: numero per numero, da sinistra. */
    static boolean isNewer(String remote, String local) {
        try {
            String[] r = remote.split("\\.");
            String[] l = local.split("\\.");
            int n = Math.max(r.length, l.length);
            for (int i = 0; i < n; i++) {
                int ri = i < r.length ? Integer.parseInt(r[i].replaceAll("[^0-9]", "")) : 0;
                int li = i < l.length ? Integer.parseInt(l[i].replaceAll("[^0-9]", "")) : 0;
                if (ri != li) {
                    return ri > li;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static void showUpdateDialog(Activity activity, UpdateInfo update) {
        String msg = activity.getString(R.string.aggiornamento_messaggio,
                update.version, currentVersion(activity));
        if (update.changelog != null && !update.changelog.trim().isEmpty()) {
            msg += "\n\n" + update.changelog.trim();
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.nuova_versione)
                .setMessage(msg)
                .setPositiveButton(R.string.scarica_installa, (d, w) -> downloadAndInstall(activity, update))
                .setNegativeButton(R.string.piu_tardi, null)
                .show();
    }

    private static void downloadAndInstall(Activity activity, UpdateInfo update) {
        View v = LayoutInflater.from(activity).inflate(R.layout.dialog_progress, null);
        AlertDialog progress = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.download_in_corso)
                .setView(v)
                .setCancelable(false)
                .create();
        progress.show();

        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                File dir = new File(activity.getCacheDir(), "updates");
                dir.mkdirs();
                File apk = new File(dir, "update.apk");
                HttpURLConnection conn = (HttpURLConnection) new URL(update.apkUrl).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
                conn.disconnect();

                Uri uri = FileProvider.getUriForFile(activity,
                        activity.getPackageName() + ".fileprovider", apk);
                Intent install = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                main.post(() -> {
                    progress.dismiss();
                    activity.startActivity(install);
                });
            } catch (Exception e) {
                main.post(() -> {
                    progress.dismiss();
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.errore)
                            .setMessage(activity.getString(R.string.errore_download, e.getMessage()))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }
        }).start();
    }
}
