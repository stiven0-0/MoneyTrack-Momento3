package com.example.moneytrack.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneytrack.modelo.Transaccion;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreHelper {

    private static final String COLECCION_TRANSACCIONES = "transacciones";
    private static final String CAMPO_FECHA_CREACION = "creadoEn";

    private final FirebaseFirestore db;

    public FirestoreHelper() {
        db = FirebaseFirestore.getInstance();
    }

    public interface OperacionCallback {
        void onExito();

        void onError(@NonNull Exception e);
    }

    public interface TransaccionesCallback {
        void onCambio(@NonNull List<Transaccion> transacciones);

        void onError(@NonNull Exception e);
    }

    /**
     * Genera un ID de documento sin escribir nada todavía. Se necesita antes de
     * guardar para poder nombrar la imagen en Storage: imagenes_transacciones/{id}.jpg
     */
    public String generarId() {
        return db.collection(COLECCION_TRANSACCIONES).document().getId();
    }

    /** Crea (o reemplaza) el documento con el ID indicado. */
    public void crear(String documentId, Transaccion transaccion, OperacionCallback callback) {
        Map<String, Object> datos = construirMapa(transaccion);
        datos.put(CAMPO_FECHA_CREACION, FieldValue.serverTimestamp());

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario != null && usuario.getEmail() != null) {
            datos.put("creadoPor", usuario.getEmail());
        }

        db.collection(COLECCION_TRANSACCIONES)
                .document(documentId)
                .set(datos)
                .addOnSuccessListener(unused -> callback.onExito())
                .addOnFailureListener(callback::onError);
    }


    public void actualizar(String documentId, Transaccion transaccion, OperacionCallback callback) {
        Map<String, Object> datos = construirMapa(transaccion);

        db.collection(COLECCION_TRANSACCIONES)
                .document(documentId)
                .update(datos)
                .addOnSuccessListener(unused -> callback.onExito())
                .addOnFailureListener(callback::onError);
    }

    public void eliminar(String documentId, OperacionCallback callback) {
        db.collection(COLECCION_TRANSACCIONES)
                .document(documentId)
                .delete()
                .addOnSuccessListener(unused -> callback.onExito())
                .addOnFailureListener(callback::onError);
    }

    public void obtenerPorId(String documentId,
                              OnSuccessListener<Transaccion> onSuccess,
                              OnFailureListener onFailure) {
        db.collection(COLECCION_TRANSACCIONES)
                .document(documentId)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.onSuccess(mapearSnapshot(snapshot)))
                .addOnFailureListener(onFailure);
    }


    public ListenerRegistration escucharTransacciones(TransaccionesCallback callback) {
        Query query = db.collection(COLECCION_TRANSACCIONES)
                .orderBy(CAMPO_FECHA_CREACION, Query.Direction.DESCENDING);

        return query.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snapshots == null) {
                return;
            }

            List<Transaccion> lista = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshots) {
                Transaccion transaccion = doc.toObject(Transaccion.class);
                transaccion.setDocumentId(doc.getId());
                lista.add(transaccion);
            }
            callback.onCambio(lista);
        });
    }

    /**
     * Los campos opcionales (imagen y ubicación) solo se incluyen si tienen valor,
     * así una edición nunca borra por accidente la foto o la ubicación existentes.
     */
    private Map<String, Object> construirMapa(Transaccion transaccion) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("concepto", transaccion.getConcepto());
        datos.put("monto", transaccion.getMonto());
        datos.put("tipo", transaccion.getTipo());

        if (transaccion.getImagenUrl() != null) {
            datos.put("imagenUrl", transaccion.getImagenUrl());
        }
        if (transaccion.getLatitud() != null && transaccion.getLongitud() != null) {
            datos.put("latitud", transaccion.getLatitud());
            datos.put("longitud", transaccion.getLongitud());
        }
        if (transaccion.getDireccion() != null) {
            datos.put("direccion", transaccion.getDireccion());
        }
        return datos;
    }

    @Nullable
    private Transaccion mapearSnapshot(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) {
            return null;
        }
        Transaccion transaccion = snapshot.toObject(Transaccion.class);
        if (transaccion != null) {
            transaccion.setDocumentId(snapshot.getId());
        }
        return transaccion;
    }
}
