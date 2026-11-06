package com.example.moneytrack.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Guarda en SharedPreferences los datos mínimos de la sesión (uid, correo y rol)
 * para poder aplicar permisos de inmediato y funcionar sin red tras el primer login.
 * La fuente de verdad del rol sigue siendo Firestore: usuarios/{uid}.rol
 */
public final class SessionManager {

    public static final String ROL_ADMIN = "admin";
    public static final String ROL_ESTANDAR = "estandar";

    private static final String PREFS = "moneytrack_session";
    private static final String KEY_UID = "uid";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ROL = "rol";

    private SessionManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void guardar(Context context, String uid, String email, String rol) {
        prefs(context).edit()
                .putString(KEY_UID, uid)
                .putString(KEY_EMAIL, email)
                .putString(KEY_ROL, rol)
                .apply();
    }

    @Nullable
    public static String getUid(Context context) {
        return prefs(context).getString(KEY_UID, null);
    }

    @Nullable
    public static String getEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, null);
    }

    @Nullable
    public static String getRol(Context context) {
        return prefs(context).getString(KEY_ROL, null);
    }

    /** Devuelve el rol cacheado solo si pertenece al usuario indicado. */
    @Nullable
    public static String getRolCacheado(Context context, String uid) {
        if (uid != null && uid.equals(getUid(context))) {
            return getRol(context);
        }
        return null;
    }

    public static boolean isAdmin(Context context) {
        return ROL_ADMIN.equals(getRol(context));
    }

    public static void limpiar(Context context) {
        prefs(context).edit().clear().apply();
    }
}
