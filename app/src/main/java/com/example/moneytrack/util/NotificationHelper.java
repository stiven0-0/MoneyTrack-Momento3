package com.example.moneytrack.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.moneytrack.MainActivity;
import com.example.moneytrack.R;

public final class NotificationHelper {

    public static final String CHANNEL_ID = "moneytrack_transacciones";

    private NotificationHelper() {
    }

    /** Registra el canal de notificaciones (obligatorio desde Android 8.0 / API 26). */
    public static void crearCanal(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel canal = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.canal_nombre),
                NotificationManager.IMPORTANCE_DEFAULT);
        canal.setDescription(context.getString(R.string.canal_descripcion));

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(canal);
        }
    }

    public static boolean tienePermiso(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Muestra una notificación local que, al tocarla, reabre la aplicación. */
    @SuppressLint("MissingPermission") // se verifica con tienePermiso()
    public static void mostrar(Context context, String titulo, String texto) {
        if (!tienePermiso(context)) {
            return;
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_money)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context)
                .notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), builder.build());
    }
}
