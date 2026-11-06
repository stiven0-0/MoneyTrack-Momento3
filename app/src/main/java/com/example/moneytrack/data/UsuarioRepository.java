package com.example.moneytrack.data;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Acceso a la colección "usuarios". El documentId es el uid de Firebase Auth
 * y el campo "rol" vale "admin" o "estandar".
 */
public class UsuarioRepository {

    private static final String COLECCION_USUARIOS = "usuarios";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface RolCallback {
        void onRol(@NonNull String rol);

        void onError(@NonNull Exception e);
    }

    /**
     * Consulta el rol del usuario. Si todavía no existe su documento (por ejemplo,
     * una cuenta creada desde la consola de Firebase) se crea con rol "estandar".
     */
    public void obtenerOCrearRol(@NonNull FirebaseUser usuario, @NonNull RolCallback callback) {
        DocumentReference ref = db.collection(COLECCION_USUARIOS).document(usuario.getUid());

        ref.get()
                .addOnSuccessListener(snapshot -> {
                    String rol = snapshot.exists() ? snapshot.getString("rol") : null;
                    if (rol != null) {
                        callback.onRol(rol);
                        return;
                    }
                    crearUsuario(usuario.getUid(), usuario.getEmail(), new FirestoreHelper.OperacionCallback() {
                        @Override
                        public void onExito() {
                            callback.onRol(SessionManager.ROL_ESTANDAR);
                        }

                        @Override
                        public void onError(@NonNull Exception e) {
                            callback.onError(e);
                        }
                    });
                })
                .addOnFailureListener(callback::onError);
    }

    /** Crea el perfil de un usuario nuevo siempre con rol "estandar". */
    public void crearUsuario(String uid, String email, FirestoreHelper.OperacionCallback callback) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("email", email);
        datos.put("rol", SessionManager.ROL_ESTANDAR);
        datos.put("creadoEn", FieldValue.serverTimestamp());

        db.collection(COLECCION_USUARIOS)
                .document(uid)
                .set(datos)
                .addOnSuccessListener(unused -> callback.onExito())
                .addOnFailureListener(callback::onError);
    }

    /** Guarda el token FCM del dispositivo para poder enviar pushes de prueba desde la consola. */
    public void guardarTokenFcm(String uid, String token) {
        db.collection(COLECCION_USUARIOS)
                .document(uid)
                .update("fcmToken", token);
    }
}
