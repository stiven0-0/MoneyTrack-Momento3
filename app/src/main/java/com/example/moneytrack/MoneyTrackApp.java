package com.example.moneytrack;

import android.app.Application;

import com.example.moneytrack.util.NotificationHelper;

public class MoneyTrackApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // El canal debe existir antes de que llegue cualquier notificación (local o push).
        NotificationHelper.crearCanal(this);
    }
}
