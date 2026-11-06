package com.example.moneytrack;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.moneytrack.adapter.TransaccionAdapter;
import com.example.moneytrack.data.FirestoreHelper;
import com.example.moneytrack.data.SessionManager;
import com.example.moneytrack.data.UsuarioRepository;
import com.example.moneytrack.modelo.Transaccion;
import com.example.moneytrack.util.NotificationHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.messaging.FirebaseMessaging;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TransaccionAdapter.OnTransaccionClickListener {

    public static final String EXTRA_TRANSACCION_ID = "transaccion_id";
    private static final String TAG = "MainActivity";

    private FirestoreHelper firestoreHelper;
    private ListenerRegistration listenerRegistration;
    private TransaccionAdapter adapter;
    private TextView tvTotalIngresos;
    private TextView tvTotalGastos;
    private TextView tvBalance;
    private TextView tvListaVacia;
    private RecyclerView recyclerTransacciones;

    private final NumberFormat formatoMoneda = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    // Permiso POST_NOTIFICATIONS (Android 13+) con la API moderna de resultados
    private final ActivityResultLauncher<String> permisoNotificacionesLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedido -> {
                // Si lo deniega la app sigue funcionando; simplemente no se muestran notificaciones.
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Instala la pantalla de portada primero
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Sin sesión no hay nada que mostrar: volvemos al login.
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            irALogin();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        firestoreHelper = new FirestoreHelper();

        tvTotalIngresos = findViewById(R.id.tvTotalIngresos);
        tvTotalGastos = findViewById(R.id.tvTotalGastos);
        tvBalance = findViewById(R.id.tvBalance);
        tvListaVacia = findViewById(R.id.tvListaVacia);
        recyclerTransacciones = findViewById(R.id.recyclerTransacciones);
        FloatingActionButton fabAgregar = findViewById(R.id.fabAgregar);

        configurarToolbar(usuario);

        // RBAC: el adapter oculta editar/eliminar si el rol no es admin
        adapter = new TransaccionAdapter(this, SessionManager.isAdmin(this));
        recyclerTransacciones.setLayoutManager(new LinearLayoutManager(this));
        recyclerTransacciones.setAdapter(adapter);

        fabAgregar.setOnClickListener(v -> abrirFormulario(null));

        solicitarPermisoNotificaciones();
        registrarTokenFcm(usuario);

        // Nos suscribimos a los cambios de la colección en tiempo real.
        // Cada vez que un documento se crea, edita o elimina —desde este
        // dispositivo o desde cualquier otro— este callback se dispara solo.
        listenerRegistration = firestoreHelper.escucharTransacciones(new FirestoreHelper.TransaccionesCallback() {
            @Override
            public void onCambio(@NonNull List<Transaccion> transacciones) {
                actualizarLista(transacciones);
            }

            @Override
            public void onError(@NonNull Exception e) {
                Toast.makeText(MainActivity.this,
                        getString(R.string.error_cargar_transacciones, e.getMessage()),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void configurarToolbar(FirebaseUser usuario) {
        MaterialToolbar toolbar = findViewById(R.id.toolbarMain);

        String email = usuario.getEmail() != null ? usuario.getEmail() : "";
        String rolTexto = getString(SessionManager.isAdmin(this)
                ? R.string.rol_admin_label : R.string.rol_estandar_label);
        toolbar.setSubtitle(getString(R.string.subtitulo_sesion, email, rolTexto));

        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_mapa) {
                startActivity(new Intent(this, MapaActivity.class));
                return true;
            }
            if (id == R.id.action_logout) {
                cerrarSesion();
                return true;
            }
            return false;
        });
    }

    private void solicitarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !NotificationHelper.tienePermiso(this)) {
            permisoNotificacionesLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Obtiene el token FCM del dispositivo, lo imprime en Logcat y lo guarda en usuarios/{uid}. */
    private void registrarTokenFcm(FirebaseUser usuario) {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    Log.d(TAG, "Token FCM: " + token);
                    new UsuarioRepository().guardarTokenFcm(usuario.getUid(), token);
                })
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo obtener el token FCM", e));
    }

    /** Cierra sesión, limpia SharedPreferences y reinicia el stack en LoginActivity. */
    private void cerrarSesion() {
        FirebaseAuth.getInstance().signOut();
        SessionManager.limpiar(this);
        irALogin();
    }

    private void irALogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Liberación de memoria: removemos el listener de Firestore para
        // evitar fugas de memoria y llamadas innecesarias al cerrar la app.
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }

    private void actualizarLista(List<Transaccion> transacciones) {
        adapter.setTransacciones(transacciones);
        actualizarResumen(transacciones);

        boolean hayTransacciones = !transacciones.isEmpty();
        tvListaVacia.setVisibility(hayTransacciones ? View.GONE : View.VISIBLE);
        recyclerTransacciones.setVisibility(hayTransacciones ? View.VISIBLE : View.GONE);
    }

    private void actualizarResumen(List<Transaccion> transacciones) {
        double totalIngresos = 0;
        double totalGastos = 0;

        for (Transaccion transaccion : transacciones) {
            if (transaccion.esIngreso()) {
                totalIngresos += transaccion.getMonto();
            } else {
                totalGastos += transaccion.getMonto();
            }
        }

        tvTotalIngresos.setText(formatoMoneda.format(totalIngresos));
        tvTotalGastos.setText(formatoMoneda.format(totalGastos));
        tvBalance.setText(formatoMoneda.format(totalIngresos - totalGastos));
    }

    private void abrirFormulario(String documentId) {
        Intent intent = new Intent(this, TransactionFormActivity.class);
        if (documentId != null) {
            intent.putExtra(EXTRA_TRANSACCION_ID, documentId);
        }
        startActivity(intent);
    }

    @Override
    public void onEditarClick(Transaccion transaccion) {
        if (!SessionManager.isAdmin(this)) {
            Toast.makeText(this, R.string.sin_permiso_editar, Toast.LENGTH_SHORT).show();
            return;
        }
        abrirFormulario(transaccion.getDocumentId());
    }

    @Override
    public void onEliminarClick(Transaccion transaccion) {
        if (!SessionManager.isAdmin(this)) {
            Toast.makeText(this, R.string.sin_permiso_editar, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.eliminar_titulo)
                .setMessage(getString(R.string.eliminar_mensaje, transaccion.getConcepto()))
                .setPositiveButton(R.string.eliminar, (dialog, which) ->
                        firestoreHelper.eliminar(transaccion.getDocumentId(), new FirestoreHelper.OperacionCallback() {
                            @Override
                            public void onExito() {
                                // No hace falta refrescar manualmente: el
                                // SnapshotListener recibe el cambio y
                                // actualiza la lista automáticamente.
                            }

                            @Override
                            public void onError(@NonNull Exception e) {
                                Toast.makeText(MainActivity.this,
                                        getString(R.string.error_eliminar, e.getMessage()),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton(R.string.cancelar, null)
                .show();



    }
}
