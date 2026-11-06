package com.example.moneytrack.util;

import android.content.Context;

import com.example.moneytrack.R;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/** Traduce las excepciones de FirebaseAuth a mensajes comprensibles para el usuario. */
public final class AuthMensajes {

    private AuthMensajes() {
    }

    public static String desde(Context context, Exception e) {
        if (e instanceof FirebaseAuthInvalidUserException) {
            return context.getString(R.string.error_usuario_inexistente);
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return context.getString(R.string.error_credenciales);
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return context.getString(R.string.error_email_en_uso);
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return context.getString(R.string.error_password_debil);
        }
        if (e instanceof FirebaseNetworkException) {
            return context.getString(R.string.error_red);
        }
        return context.getString(R.string.error_auth_generico, e.getMessage());
    }
}
