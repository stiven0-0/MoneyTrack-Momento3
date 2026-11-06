package com.example.moneytrack;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.moneytrack.data.FirestoreHelper;
import com.example.moneytrack.data.SessionManager;
import com.example.moneytrack.modelo.Transaccion;
import com.example.moneytrack.util.NotificationHelper;
import com.example.moneytrack.util.SimpleTextWatcher;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransactionFormActivity extends AppCompatActivity {

    private static final String TAG = "TransactionForm";
    private static final int LONGITUD_MINIMA_CONCEPTO = 3;
    private static final int LADO_MAXIMO_IMAGEN_PX = 1280;
    private static final int CALIDAD_JPEG = 85;

    // Claves para conservar el estado al rotar la pantalla
    private static final String STATE_IMAGEN_URI = "state_imagen_uri";
    private static final String STATE_CAMARA_URI = "state_camara_uri";
    private static final String STATE_IMAGEN_URL = "state_imagen_url";
    private static final String STATE_TIENE_UBICACION = "state_tiene_ubicacion";
    private static final String STATE_LATITUD = "state_latitud";
    private static final String STATE_LONGITUD = "state_longitud";
    private static final String STATE_DIRECCION = "state_direccion";
    private static final String STATE_ID_NUEVO = "state_id_nuevo";

    private FirestoreHelper firestoreHelper;
    private FusedLocationProviderClient fusedClient;
    private CancellationTokenSource cancelacionUbicacion;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextInputLayout tilConcepto;
    private TextInputLayout tilMonto;
    private TextInputEditText etConcepto;
    private TextInputEditText etMonto;
    private RadioButton rbGasto;
    private RadioButton rbIngreso;
    private Button btnGuardar;
    private Button btnEliminar;
    private ImageView ivPreview;
    private LinearProgressIndicator progressSubida;
    private TextView tvProgresoSubida;
    private TextView tvUbicacion;

    private String documentId = null;   // != null => modo edición
    private String idNuevo = null;      // ID reservado para una transacción nueva
    private boolean guardando = false;

    // Imagen
    private Uri imagenSeleccionada = null;  // imagen nueva elegida por el usuario (galería o cámara)
    private Uri camaraUri = null;           // archivo temporal donde la cámara escribe la foto
    private String imagenUrlExistente = null;

    // Ubicación
    private Double latitud = null;
    private Double longitud = null;
    private String direccion = null;

    // ---------- ActivityResultLauncher (API moderna) ----------

    private final ActivityResultLauncher<String> galeriaLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    imagenSeleccionada = uri;
                    mostrarImagen();
                }
            });

    private final ActivityResultLauncher<Uri> camaraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), exito -> {
                if (Boolean.TRUE.equals(exito) && camaraUri != null) {
                    imagenSeleccionada = camaraUri;
                    mostrarImagen();
                }
            });

    private final ActivityResultLauncher<String> permisoCamaraLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedido -> {
                if (Boolean.TRUE.equals(concedido)) {
                    lanzarCamara();
                } else {
                    Toast.makeText(this, R.string.permiso_camara_denegado, Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String[]> permisosUbicacionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), resultado -> {
                boolean fine = Boolean.TRUE.equals(resultado.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarse = Boolean.TRUE.equals(resultado.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (fine || coarse) {
                    obtenerUbicacion();
                } else {
                    tvUbicacion.setText(R.string.ubicacion_no_disponible);
                    Toast.makeText(this, R.string.permiso_ubicacion_denegado, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transaction_form);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.formRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        documentId = getIntent().getStringExtra(MainActivity.EXTRA_TRANSACCION_ID);

        // RBAC: editar es exclusivo del rol admin
        if (documentId != null && !SessionManager.isAdmin(this)) {
            Toast.makeText(this, R.string.sin_permiso_editar, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        firestoreHelper = new FirestoreHelper();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        tilConcepto = findViewById(R.id.tilConcepto);
        tilMonto = findViewById(R.id.tilMonto);
        etConcepto = findViewById(R.id.etConcepto);
        etMonto = findViewById(R.id.etMonto);
        RadioGroup rgTipo = findViewById(R.id.rgTipo);
        rbGasto = findViewById(R.id.rbGasto);
        rbIngreso = findViewById(R.id.rbIngreso);
        btnGuardar = findViewById(R.id.btnGuardar);
        btnEliminar = findViewById(R.id.btnEliminar);
        ivPreview = findViewById(R.id.ivPreview);
        progressSubida = findViewById(R.id.progressSubida);
        tvProgresoSubida = findViewById(R.id.tvProgresoSubida);
        tvUbicacion = findViewById(R.id.tvUbicacion);
        Button btnGaleria = findViewById(R.id.btnGaleria);
        Button btnCamara = findViewById(R.id.btnCamara);
        Button btnUbicacion = findViewById(R.id.btnUbicacion);

        toolbar.setNavigationOnClickListener(v -> finish());

        configurarValidacionEnTiempoReal();

        btnGaleria.setOnClickListener(v -> galeriaLauncher.launch("image/*"));
        btnCamara.setOnClickListener(v -> tomarFoto());
        btnUbicacion.setOnClickListener(v -> iniciarCapturaUbicacion());
        btnGuardar.setOnClickListener(v -> guardarTransaccion());
        btnEliminar.setOnClickListener(v -> eliminarTransaccion());

        if (documentId != null) {
            toolbar.setTitle(R.string.titulo_editar);
            btnEliminar.setVisibility(View.VISIBLE);
        } else {
            toolbar.setTitle(R.string.titulo_nueva);
        }

        if (savedInstanceState != null) {
            restaurarEstado(savedInstanceState);
            mostrarImagen();
            actualizarTextoUbicacion();
        } else if (documentId != null) {
            mostrarImagen();
            actualizarTextoUbicacion();
            cargarTransaccion();
        } else {
            rbGasto.setChecked(true);
            mostrarImagen();
            actualizarTextoUbicacion();
            // Al registrar una transacción nueva capturamos el GPS automáticamente
            iniciarCapturaUbicacion();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (imagenSeleccionada != null) {
            outState.putString(STATE_IMAGEN_URI, imagenSeleccionada.toString());
        }
        if (camaraUri != null) {
            outState.putString(STATE_CAMARA_URI, camaraUri.toString());
        }
        outState.putString(STATE_IMAGEN_URL, imagenUrlExistente);
        outState.putBoolean(STATE_TIENE_UBICACION, latitud != null && longitud != null);
        if (latitud != null && longitud != null) {
            outState.putDouble(STATE_LATITUD, latitud);
            outState.putDouble(STATE_LONGITUD, longitud);
        }
        outState.putString(STATE_DIRECCION, direccion);
        outState.putString(STATE_ID_NUEVO, idNuevo);
    }

    private void restaurarEstado(Bundle estado) {
        String uri = estado.getString(STATE_IMAGEN_URI);
        imagenSeleccionada = uri != null ? Uri.parse(uri) : null;
        String uriCamara = estado.getString(STATE_CAMARA_URI);
        camaraUri = uriCamara != null ? Uri.parse(uriCamara) : null;
        imagenUrlExistente = estado.getString(STATE_IMAGEN_URL);
        if (estado.getBoolean(STATE_TIENE_UBICACION)) {
            latitud = estado.getDouble(STATE_LATITUD);
            longitud = estado.getDouble(STATE_LONGITUD);
        }
        direccion = estado.getString(STATE_DIRECCION);
        idNuevo = estado.getString(STATE_ID_NUEVO);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cancelacionUbicacion != null) {
            cancelacionUbicacion.cancel();
        }
        executor.shutdown();
    }

    // ---------- Validaciones ----------

    private void configurarValidacionEnTiempoReal() {
        etConcepto.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String texto = s.toString().trim();
                if (texto.isEmpty()) {
                    tilConcepto.setError(null);
                } else if (texto.length() < LONGITUD_MINIMA_CONCEPTO) {
                    tilConcepto.setError(getString(R.string.error_concepto_corto));
                } else {
                    tilConcepto.setError(null);
                }
            }
        });

        etMonto.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String texto = s.toString().trim();
                if (texto.isEmpty()) {
                    tilMonto.setError(null);
                    return;
                }
                try {
                    double valor = Double.parseDouble(texto);
                    tilMonto.setError(valor <= 0 ? getString(R.string.error_monto_invalido) : null);
                } catch (NumberFormatException e) {
                    tilMonto.setError(getString(R.string.error_monto_invalido));
                }
            }
        });
    }

    private void cargarTransaccion() {
        firestoreHelper.obtenerPorId(documentId,
                transaccion -> {
                    if (transaccion == null) {
                        Toast.makeText(this, R.string.error_transaccion_no_encontrada, Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    etConcepto.setText(transaccion.getConcepto());
                    etMonto.setText(String.valueOf(transaccion.getMonto()));

                    if (transaccion.esIngreso()) {
                        rbIngreso.setChecked(true);
                    } else {
                        rbGasto.setChecked(true);
                    }

                    imagenUrlExistente = transaccion.getImagenUrl();
                    latitud = transaccion.getLatitud();
                    longitud = transaccion.getLongitud();
                    direccion = transaccion.getDireccion();
                    mostrarImagen();
                    actualizarTextoUbicacion();
                },
                e -> {
                    Toast.makeText(this,
                            getString(R.string.error_cargar_transacciones, e.getMessage()),
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    // ---------- Imagen: cámara / galería ----------

    private void tomarFoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            lanzarCamara();
        } else {
            permisoCamaraLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void lanzarCamara() {
        try {
            File carpeta = new File(getCacheDir(), "fotos");
            if (!carpeta.exists() && !carpeta.mkdirs()) {
                throw new IOException("No se pudo crear la carpeta de fotos");
            }
            File archivo = File.createTempFile("foto_", ".jpg", carpeta);
            camaraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", archivo);
            camaraLauncher.launch(camaraUri);
        } catch (IOException | IllegalArgumentException e) {
            Log.e(TAG, "No se pudo preparar la cámara", e);
            Toast.makeText(this, R.string.error_crear_archivo, Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarImagen() {
        if (imagenSeleccionada != null) {
            Glide.with(this)
                    .load(imagenSeleccionada)
                    .placeholder(R.drawable.ic_placeholder_image)
                    .error(R.drawable.ic_image_error)
                    .centerCrop()
                    .into(ivPreview);
        } else {
            Glide.with(this)
                    .load(imagenUrlExistente)
                    .placeholder(R.drawable.ic_placeholder_image)
                    .error(R.drawable.ic_image_error)
                    .fallback(R.drawable.ic_placeholder_image)
                    .centerCrop()
                    .into(ivPreview);
        }
    }

    // ---------- Ubicación: FusedLocation + Geocoder ----------

    private boolean tienePermisoUbicacion() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void iniciarCapturaUbicacion() {
        if (tienePermisoUbicacion()) {
            obtenerUbicacion();
        } else {
            permisosUbicacionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    @SuppressLint("MissingPermission") // el permiso se verifica en iniciarCapturaUbicacion()
    private void obtenerUbicacion() {
        tvUbicacion.setText(R.string.ubicacion_obteniendo);

        if (cancelacionUbicacion != null) {
            cancelacionUbicacion.cancel();
        }
        cancelacionUbicacion = new CancellationTokenSource();

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelacionUbicacion.getToken())
                .addOnSuccessListener(ubicacion -> {
                    if (ubicacion != null) {
                        procesarUbicacion(ubicacion);
                    } else {
                        usarUltimaUbicacion();
                    }
                })
                .addOnFailureListener(e -> usarUltimaUbicacion());
    }

    @SuppressLint("MissingPermission")
    private void usarUltimaUbicacion() {
        fusedClient.getLastLocation()
                .addOnSuccessListener(ubicacion -> {
                    if (ubicacion != null) {
                        procesarUbicacion(ubicacion);
                    } else if (activityViva()) {
                        tvUbicacion.setText(R.string.ubicacion_no_disponible);
                    }
                })
                .addOnFailureListener(e -> {
                    if (activityViva()) {
                        tvUbicacion.setText(R.string.ubicacion_no_disponible);
                    }
                });
    }

    private void procesarUbicacion(Location ubicacion) {
        latitud = ubicacion.getLatitude();
        longitud = ubicacion.getLongitude();
        direccion = null;
        if (activityViva()) {
            actualizarTextoUbicacion();
        }
        geocodificar(latitud, longitud);
    }

    /** Geocodificación inversa: convierte latitud/longitud en una dirección legible. */
    @SuppressWarnings("deprecation")
    private void geocodificar(double lat, double lng) {
        final android.content.Context appContext = getApplicationContext();
        executor.execute(() -> {
            String resultado = null;
            try {
                if (Geocoder.isPresent()) {
                    Geocoder geocoder = new Geocoder(appContext, Locale.getDefault());
                    List<Address> direcciones = geocoder.getFromLocation(lat, lng, 1);
                    if (direcciones != null && !direcciones.isEmpty()) {
                        resultado = direcciones.get(0).getAddressLine(0);
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Geocoder sin respuesta", e);
            }
            final String direccionFinal = resultado;
            runOnUiThread(() -> {
                direccion = direccionFinal;
                if (activityViva()) {
                    actualizarTextoUbicacion();
                }
            });
        });
    }

    private void actualizarTextoUbicacion() {
        if (latitud == null || longitud == null) {
            tvUbicacion.setText(R.string.ubicacion_no_disponible);
            return;
        }
        String coordenadas = String.format(Locale.US, "%.5f, %.5f", latitud, longitud);
        if (direccion != null && !direccion.isEmpty()) {
            tvUbicacion.setText(direccion + "\n" + coordenadas);
        } else {
            tvUbicacion.setText(coordenadas);
        }
    }

    // ---------- Guardado ----------

    private void guardarTransaccion() {
        if (guardando) {
            return;
        }

        String concepto = etConcepto.getText() != null ? etConcepto.getText().toString().trim() : "";
        String montoTexto = etMonto.getText() != null ? etMonto.getText().toString().trim() : "";

        if (TextUtils.isEmpty(concepto)) {
            tilConcepto.setError(getString(R.string.error_concepto_vacio));
            etConcepto.requestFocus();
            return;
        }

        if (concepto.length() < LONGITUD_MINIMA_CONCEPTO) {
            tilConcepto.setError(getString(R.string.error_concepto_corto));
            etConcepto.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(montoTexto)) {
            tilMonto.setError(getString(R.string.error_monto_vacio));
            etMonto.requestFocus();
            return;
        }

        double monto;
        try {
            monto = Double.parseDouble(montoTexto);
        } catch (NumberFormatException e) {
            tilMonto.setError(getString(R.string.error_monto_invalido));
            etMonto.requestFocus();
            return;
        }

        if (monto <= 0) {
            tilMonto.setError(getString(R.string.error_monto_invalido));
            etMonto.requestFocus();
            return;
        }

        int tipo = rbIngreso.isChecked() ? Transaccion.TIPO_INGRESO : Transaccion.TIPO_GASTO;
        Transaccion transaccion = new Transaccion(concepto, monto, tipo);
        transaccion.setImagenUrl(imagenUrlExistente);
        transaccion.setLatitud(latitud);
        transaccion.setLongitud(longitud);
        transaccion.setDireccion(direccion);

        // El ID se conoce antes de subir la imagen: imagenes_transacciones/{id}.jpg
        String id = documentId;
        if (id == null) {
            if (idNuevo == null) {
                idNuevo = firestoreHelper.generarId();
            }
            id = idNuevo;
        }

        establecerGuardando(true);

        if (imagenSeleccionada != null) {
            prepararYSubirImagen(id, transaccion);
        } else {
            guardarEnFirestore(id, transaccion);
        }
    }

    /** Reduce y comprime la imagen en segundo plano para no subir fotos de varios MB. */
    private void prepararYSubirImagen(String id, Transaccion transaccion) {
        final Uri origen = imagenSeleccionada;
        final android.content.Context appContext = getApplicationContext();

        mostrarProgreso(0);

        executor.execute(() -> {
            byte[] bytes = null;
            try {
                // Glide decodifica, respeta la rotación EXIF y limita el tamaño.
                Bitmap bitmap = Glide.with(appContext)
                        .asBitmap()
                        .load(origen)
                        .override(LADO_MAXIMO_IMAGEN_PX, LADO_MAXIMO_IMAGEN_PX)
                        .fitCenter()
                        .submit()
                        .get();
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida);
                bytes = salida.toByteArray();
            } catch (Exception e) {
                Log.e(TAG, "No se pudo procesar la imagen", e);
            }

            final byte[] datos = bytes;
            runOnUiThread(() -> {
                if (datos == null) {
                    if (activityViva()) {
                        ocultarProgreso();
                        establecerGuardando(false);
                        Toast.makeText(this, R.string.error_procesar_imagen, Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                subirImagen(id, transaccion, datos);
            });
        });
    }

    /** Sube a Firebase Storage, muestra el progreso y obtiene la URL de descarga. */
    private void subirImagen(String id, Transaccion transaccion, byte[] datos) {
        StorageReference referencia = FirebaseStorage.getInstance()
                .getReference()
                .child("imagenes_transacciones/" + id + ".jpg");

        StorageMetadata metadatos = new StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build();

        referencia.putBytes(datos, metadatos)
                .addOnProgressListener(snapshot -> {
                    long total = snapshot.getTotalByteCount();
                    if (total > 0 && activityViva()) {
                        mostrarProgreso((int) (100 * snapshot.getBytesTransferred() / total));
                    }
                })
                .addOnSuccessListener(snapshot -> referencia.getDownloadUrl()
                        .addOnSuccessListener(url -> {
                            transaccion.setImagenUrl(url.toString());
                            guardarEnFirestore(id, transaccion);
                        })
                        .addOnFailureListener(e -> errorSubida(e)))
                .addOnFailureListener(this::errorSubida);
    }

    private void errorSubida(Exception e) {
        if (!activityViva()) {
            return;
        }
        ocultarProgreso();
        establecerGuardando(false);
        Toast.makeText(this, getString(R.string.error_subir_imagen, e.getMessage()), Toast.LENGTH_LONG).show();
    }

    private void guardarEnFirestore(String id, Transaccion transaccion) {
        final boolean esEdicion = documentId != null;
        final android.content.Context appContext = getApplicationContext();

        FirestoreHelper.OperacionCallback callback = new FirestoreHelper.OperacionCallback() {
            @Override
            public void onExito() {
                // Notificación local: se emite aunque la pantalla ya no esté visible.
                String monto = NumberFormat.getCurrencyInstance(new Locale("es", "MX"))
                        .format(transaccion.getMonto());
                NotificationHelper.mostrar(appContext,
                        appContext.getString(esEdicion
                                ? R.string.notif_titulo_actualizada : R.string.notif_titulo_guardada),
                        transaccion.getConcepto() + " · " + monto);

                if (!activityViva()) {
                    return;
                }
                ocultarProgreso();
                establecerGuardando(false);
                Toast.makeText(TransactionFormActivity.this,
                        esEdicion ? R.string.mensaje_actualizado : R.string.mensaje_guardado,
                        Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(@NonNull Exception e) {
                if (!activityViva()) {
                    return;
                }
                ocultarProgreso();
                establecerGuardando(false);
                Toast.makeText(TransactionFormActivity.this,
                        getString(R.string.error_guardar, e.getMessage()),
                        Toast.LENGTH_SHORT).show();
            }
        };

        if (esEdicion) {
            firestoreHelper.actualizar(id, transaccion, callback);
        } else {
            firestoreHelper.crear(id, transaccion, callback);
        }
    }

    private void eliminarTransaccion() {
        if (guardando || documentId == null) {
            return;
        }
        establecerGuardando(true);

        firestoreHelper.eliminar(documentId, new FirestoreHelper.OperacionCallback() {
            @Override
            public void onExito() {
                Toast.makeText(TransactionFormActivity.this, R.string.mensaje_eliminado, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(@NonNull Exception e) {
                establecerGuardando(false);
                Toast.makeText(TransactionFormActivity.this,
                        getString(R.string.error_eliminar, e.getMessage()),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------- Utilidades de UI ----------

    private void mostrarProgreso(int porcentaje) {
        progressSubida.setVisibility(View.VISIBLE);
        tvProgresoSubida.setVisibility(View.VISIBLE);
        progressSubida.setProgressCompat(porcentaje, true);
        tvProgresoSubida.setText(getString(R.string.subiendo_imagen, porcentaje));
    }

    private void ocultarProgreso() {
        progressSubida.setVisibility(View.GONE);
        tvProgresoSubida.setVisibility(View.GONE);
    }

    private void establecerGuardando(boolean guardandoAhora) {
        guardando = guardandoAhora;
        btnGuardar.setEnabled(!guardandoAhora);
        btnEliminar.setEnabled(!guardandoAhora);
    }

    private boolean activityViva() {
        return !isFinishing() && !isDestroyed();
    }
}
