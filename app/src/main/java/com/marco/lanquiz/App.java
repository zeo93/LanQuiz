package com.marco.lanquiz;

import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        applyTheme(this);
    }

    /** Applica il tema scelto in Impostazioni (chiaro, scuro o come il sistema). */
    public static void applyTheme(Context c) {
        String theme = Store.theme(c);
        int mode;
        if ("chiaro".equals(theme)) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("scuro".equals(theme)) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
