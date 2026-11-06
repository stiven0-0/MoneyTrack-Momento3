package com.example.moneytrack.modelo;

import com.google.firebase.firestore.Exclude;


public class Transaccion {

    public static final int TIPO_GASTO = 0;
    public static final int TIPO_INGRESO = 1;

    @Exclude
    private String documentId;

    private String concepto;
    private double monto;
    private int tipo;
    private Object creadoEn; // com.google.firebase.Timestamp asignado por el servidor (FieldValue.serverTimestamp())

    // ---- Momento 3 ----
    private String imagenUrl;   // URL de descarga en Firebase Storage
    private Double latitud;     // null si no se pudo obtener la ubicación
    private Double longitud;
    private String direccion;   // resultado de la geocodificación inversa
    private String creadoPor;   // correo del usuario que registró la transacción


    public Transaccion() {
    }

    public Transaccion(String concepto, double monto, int tipo) {
        this.concepto = concepto;
        this.monto = monto;
        this.tipo = tipo;
    }

    @Exclude
    public String getDocumentId() {
        return documentId;
    }

    @Exclude
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getConcepto() {
        return concepto;
    }

    public void setConcepto(String concepto) {
        this.concepto = concepto;
    }

    public double getMonto() {
        return monto;
    }

    public void setMonto(double monto) {
        this.monto = monto;
    }

    public int getTipo() {
        return tipo;
    }

    public void setTipo(int tipo) {
        this.tipo = tipo;
    }

    public Object getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Object creadoEn) {
        this.creadoEn = creadoEn;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }

    public Double getLatitud() {
        return latitud;
    }

    public void setLatitud(Double latitud) {
        this.latitud = latitud;
    }

    public Double getLongitud() {
        return longitud;
    }

    public void setLongitud(Double longitud) {
        this.longitud = longitud;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getCreadoPor() {
        return creadoPor;
    }

    public void setCreadoPor(String creadoPor) {
        this.creadoPor = creadoPor;
    }

    @Exclude
    public boolean esIngreso() {
        return tipo == TIPO_INGRESO;
    }

    @Exclude
    public boolean tieneUbicacion() {
        return latitud != null && longitud != null;
    }
}
