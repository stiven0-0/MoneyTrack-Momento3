package com.example.moneytrack;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.moneytrack.data.FirestoreHelper;
import com.example.moneytrack.modelo.Transaccion;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Mapa interactivo con un marcador por cada transacción que tenga ubicación.
 * Los datos llegan en tiempo real desde Firestore.
 */
public class MapaActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mapa;
    private boolean mapaCargado = false;
    private boolean camaraAjustada = false;

    private FirestoreHelper firestoreHelper;
    private ListenerRegistration listenerRegistration;
    private List<Transaccion> ultimasTransacciones;

    private final NumberFormat formatoMoneda = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_mapa);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mapaRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        firestoreHelper = new FirestoreHelper();

        MaterialToolbar toolbar = findViewById(R.id.toolbarMapa);
        toolbar.setNavigationOnClickListener(v -> finish());

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (fragment instanceof SupportMapFragment) {
            ((SupportMapFragment) fragment).getMapAsync(this);
        }

        // Alternar capas: Normal <-> Satélite
        MaterialButtonToggleGroup toggleCapas = findViewById(R.id.toggleCapas);
        toggleCapas.check(R.id.btnMapaNormal);
        toggleCapas.addOnButtonCheckedListener((grupo, botonId, marcado) -> {
            if (!marcado || mapa == null) {
                return;
            }
            mapa.setMapType(botonId == R.id.btnMapaSatelite
                    ? GoogleMap.MAP_TYPE_SATELLITE
                    : GoogleMap.MAP_TYPE_NORMAL);
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mapa = googleMap;
        mapa.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // Controles de zoom (+ / -) y brújula
        mapa.getUiSettings().setZoomControlsEnabled(true);
        mapa.getUiSettings().setZoomGesturesEnabled(true);
        mapa.getUiSettings().setCompassEnabled(true);

        // InfoWindow personalizada: concepto, monto y dirección
        mapa.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(@NonNull Marker marker) {
                return null; // se usa el marco por defecto y solo se personaliza el contenido
            }

            @Override
            public View getInfoContents(@NonNull Marker marker) {
                Object tag = marker.getTag();
                if (!(tag instanceof Transaccion)) {
                    return null;
                }
                Transaccion t = (Transaccion) tag;

                View vista = LayoutInflater.from(MapaActivity.this)
                        .inflate(R.layout.info_window_transaccion, null);
                TextView tvConcepto = vista.findViewById(R.id.tvInfoConcepto);
                TextView tvMonto = vista.findViewById(R.id.tvInfoMonto);
                TextView tvDireccion = vista.findViewById(R.id.tvInfoDireccion);

                tvConcepto.setText(t.getConcepto());
                tvMonto.setText(formatoMoneda.format(t.getMonto()));
                tvMonto.setTextColor(ContextCompat.getColor(MapaActivity.this,
                        t.esIngreso() ? R.color.ingreso : R.color.gasto));

                String dir = t.getDireccion();
                tvDireccion.setText(dir != null && !dir.isEmpty() ? dir : getString(R.string.sin_direccion));
                return vista;
            }
        });

        mapa.setOnMapLoadedCallback(() -> {
            mapaCargado = true;
            ajustarCamara();
        });

        escucharTransacciones();
    }

    private void escucharTransacciones() {
        listenerRegistration = firestoreHelper.escucharTransacciones(new FirestoreHelper.TransaccionesCallback() {
            @Override
            public void onCambio(@NonNull List<Transaccion> transacciones) {
                ultimasTransacciones = transacciones;
                pintarMarcadores(transacciones);
            }

            @Override
            public void onError(@NonNull Exception e) {
                Toast.makeText(MapaActivity.this,
                        getString(R.string.error_cargar_mapa, e.getMessage()),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void pintarMarcadores(List<Transaccion> transacciones) {
        if (mapa == null) {
            return;
        }
        mapa.clear();

        int conUbicacion = 0;
        for (Transaccion t : transacciones) {
            if (!t.tieneUbicacion()) {
                continue;
            }
            conUbicacion++;

            Marker marcador = mapa.addMarker(new MarkerOptions()
                    .position(new LatLng(t.getLatitud(), t.getLongitud()))
                    .title(t.getConcepto())
                    .icon(BitmapDescriptorFactory.defaultMarker(t.esIngreso()
                            ? BitmapDescriptorFactory.HUE_GREEN
                            : BitmapDescriptorFactory.HUE_RED)));
            if (marcador != null) {
                marcador.setTag(t);
            }
        }

        if (conUbicacion == 0) {
            Toast.makeText(this, R.string.mapa_sin_marcadores, Toast.LENGTH_SHORT).show();
        }

        if (!camaraAjustada) {
            ajustarCamara();
        }
    }

    /** Encuadra todos los marcadores. Se espera a que el mapa tenga tamaño para evitar excepciones. */
    private void ajustarCamara() {
        if (mapa == null || !mapaCargado || ultimasTransacciones == null) {
            return;
        }

        LatLngBounds.Builder limites = new LatLngBounds.Builder();
        int cantidad = 0;
        LatLng ultimo = null;
        for (Transaccion t : ultimasTransacciones) {
            if (t.tieneUbicacion()) {
                ultimo = new LatLng(t.getLatitud(), t.getLongitud());
                limites.include(ultimo);
                cantidad++;
            }
        }

        if (cantidad == 0) {
            return;
        }

        if (cantidad == 1) {
            mapa.moveCamera(CameraUpdateFactory.newLatLngZoom(ultimo, 16f));
        } else {
            mapa.moveCamera(CameraUpdateFactory.newLatLngBounds(limites.build(), 150));
        }
        camaraAjustada = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }
}
