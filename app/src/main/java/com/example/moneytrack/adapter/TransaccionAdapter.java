package com.example.moneytrack.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.moneytrack.R;
import com.example.moneytrack.modelo.Transaccion;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransaccionAdapter extends RecyclerView.Adapter<TransaccionAdapter.TransaccionViewHolder> {

    public interface OnTransaccionClickListener {
        void onEditarClick(Transaccion transaccion);

        void onEliminarClick(Transaccion transaccion);
    }

    private final List<Transaccion> transacciones = new ArrayList<>();
    private final OnTransaccionClickListener listener;
    private final boolean esAdmin;
    private final NumberFormat formatoMoneda = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    /**
     * @param esAdmin true para el rol "admin": muestra los botones de editar y eliminar.
     *                Con rol "estandar" esos controles se ocultan.
     */
    public TransaccionAdapter(OnTransaccionClickListener listener, boolean esAdmin) {
        this.listener = listener;
        this.esAdmin = esAdmin;
    }

    public void setTransacciones(List<Transaccion> nuevasTransacciones) {
        transacciones.clear();
        if (nuevasTransacciones != null) {
            transacciones.addAll(nuevasTransacciones);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TransaccionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaccion, parent, false);
        return new TransaccionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransaccionViewHolder holder, int position) {
        holder.bind(transacciones.get(position));
    }

    @Override
    public int getItemCount() {
        return transacciones.size();
    }

    class TransaccionViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvConcepto;
        private final TextView tvMonto;
        private final TextView tvTipo;
        private final TextView tvDireccion;
        private final ImageView ivImagen;
        private final View indicadorTipo;
        private final ImageButton btnEditar;
        private final ImageButton btnEliminar;

        TransaccionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvConcepto = itemView.findViewById(R.id.tvConcepto);
            tvMonto = itemView.findViewById(R.id.tvMonto);
            tvTipo = itemView.findViewById(R.id.tvTipo);
            tvDireccion = itemView.findViewById(R.id.tvDireccion);
            ivImagen = itemView.findViewById(R.id.ivImagen);
            indicadorTipo = itemView.findViewById(R.id.indicadorTipo);
            btnEditar = itemView.findViewById(R.id.btnEditar);
            btnEliminar = itemView.findViewById(R.id.btnEliminar);
        }

        void bind(Transaccion transaccion) {
            Context context = itemView.getContext();
            tvConcepto.setText(transaccion.getConcepto());
            tvMonto.setText(formatoMoneda.format(transaccion.getMonto()));

            if (transaccion.esIngreso()) {
                tvTipo.setText(R.string.tipo_ingreso);
                tvTipo.setTextColor(ContextCompat.getColor(context, R.color.ingreso));
                tvMonto.setTextColor(ContextCompat.getColor(context, R.color.ingreso));
                indicadorTipo.setBackgroundResource(R.drawable.bg_tipo_ingreso);
            } else {
                tvTipo.setText(R.string.tipo_gasto);
                tvTipo.setTextColor(ContextCompat.getColor(context, R.color.gasto));
                tvMonto.setTextColor(ContextCompat.getColor(context, R.color.gasto));
                indicadorTipo.setBackgroundResource(R.drawable.bg_tipo_gasto);
            }

            // Dirección obtenida con el Geocoder (solo si existe)
            String direccion = transaccion.getDireccion();
            if (direccion != null && !direccion.trim().isEmpty()) {
                tvDireccion.setText(direccion);
                tvDireccion.setVisibility(View.VISIBLE);
            } else {
                tvDireccion.setVisibility(View.GONE);
            }

            // Imagen desde Firebase Storage con Glide (placeholder + error obligatorios)
            String url = transaccion.getImagenUrl();
            if (url != null && url.trim().isEmpty()) {
                url = null;
            }
            Glide.with(context)
                    .load(url)
                    .placeholder(R.drawable.ic_placeholder_image)
                    .error(R.drawable.ic_image_error)
                    .fallback(R.drawable.ic_placeholder_image)
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // caché local en disco
                    .centerCrop()
                    .into(ivImagen);

            // RBAC: solo el administrador puede editar o eliminar
            if (esAdmin) {
                btnEditar.setVisibility(View.VISIBLE);
                btnEliminar.setVisibility(View.VISIBLE);
                itemView.setOnClickListener(v -> listener.onEditarClick(transaccion));
                btnEditar.setOnClickListener(v -> listener.onEditarClick(transaccion));
                btnEliminar.setOnClickListener(v -> listener.onEliminarClick(transaccion));
            } else {
                btnEditar.setVisibility(View.GONE);
                btnEliminar.setVisibility(View.GONE);
                itemView.setOnClickListener(null);
                itemView.setClickable(false);
            }
        }
    }
}
