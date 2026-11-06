package com.example.moneytrack;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.moneytrack.data.UsuarioRepository;
import com.example.moneytrack.util.NotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Servicio de Firebase Cloud Messaging. Recibe los mensajes push enviados desde
 * la consola de Firebase (Messaging > Nueva campaña) y los muestra al usuario.
 */
public class MoneyTrackMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MoneyTrackFCM";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Nuevo token FCM: " + token);

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario != null) {
            new UsuarioRepository().guardarTokenFcm(usuario.getUid(), token);
        }
    }

    /**
     * Se ejecuta con la app en primer plano (con la app en segundo plano, el sistema
     * muestra por sí mismo los mensajes de tipo "notification").
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage mensaje) {
        super.onMessageReceived(mensaje);

        String titulo = getString(R.string.notif_titulo_push_default);
        String cuerpo = "";

        RemoteMessage.Notification notificacion = mensaje.getNotification();
        if (notificacion != null) {
            if (notificacion.getTitle() != null) {
                titulo = notificacion.getTitle();
            }
            if (notificacion.getBody() != null) {
                cuerpo = notificacion.getBody();
            }
        } else {
            // Mensajes solo de datos: {"title": "...", "body": "..."}
            Map<String, String> datos = mensaje.getData();
            if (datos.containsKey("title")) {
                titulo = datos.get("title");
            }
            if (datos.containsKey("body")) {
                cuerpo = datos.get("body");
            }
        }

        Log.d(TAG, "Mensaje push recibido: " + titulo + " - " + cuerpo);
        NotificationHelper.mostrar(getApplicationContext(), titulo, cuerpo);
    }
}
