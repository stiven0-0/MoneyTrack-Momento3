package com.example.moneytrack;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.moneytrack.data.SessionManager;
import com.example.moneytrack.data.UsuarioRepository;
import com.example.moneytrack.util.AuthMensajes;
import com.example.moneytrack.util.SimpleTextWatcher;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Pantalla de entrada de la app. Si ya existe una sesión activa de FirebaseAuth
 * salta directamente a MainActivity (auto-login) tras resolver el rol del usuario.
 */
public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private UsuarioRepository usuarioRepository;

    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private Button btnLogin;
    private View scrollLogin;
    private ProgressBar progressLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        usuarioRepository = new UsuarioRepository();

        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        scrollLogin = findViewById(R.id.scrollLogin);
        progressLogin = findViewById(R.id.progressLogin);
        TextView tvIrRegistro = findViewById(R.id.tvIrRegistro);

        configurarValidacionEnTiempoReal();

        btnLogin.setOnClickListener(v -> iniciarSesion());
        tvIrRegistro.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));

        // Persistencia de sesión: si hay un usuario autenticado no pedimos credenciales.
        FirebaseUser usuarioActual = auth.getCurrentUser();
        if (usuarioActual != null) {
            mostrarCargando(true);
            resolverRolYEntrar(usuarioActual);
        }
    }

    private void configurarValidacionEnTiempoReal() {
        etEmail.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String texto = s.toString().trim();
                if (texto.isEmpty() || Patterns.EMAIL_ADDRESS.matcher(texto).matches()) {
                    tilEmail.setError(null);
                } else {
                    tilEmail.setError(getString(R.string.error_email_invalido));
                }
            }
        });

        etPassword.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                tilPassword.setError(null);
            }
        });
    }

    private void iniciarSesion() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_email_vacio));
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_email_invalido));
            etEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_password_vacio));
            etPassword.requestFocus();
            return;
        }

        mostrarCargando(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(resultado -> {
                    FirebaseUser usuario = resultado.getUser();
                    if (usuario == null) {
                        mostrarCargando(false);
                        return;
                    }
                    resolverRolYEntrar(usuario);
                })
                .addOnFailureListener(e -> {
                    mostrarCargando(false);
                    Toast.makeText(this, AuthMensajes.desde(this, e), Toast.LENGTH_LONG).show();
                });
    }

    /** Consulta usuarios/{uid}.rol, lo guarda en SharedPreferences y abre MainActivity. */
    private void resolverRolYEntrar(@NonNull FirebaseUser usuario) {
        usuarioRepository.obtenerOCrearRol(usuario, new UsuarioRepository.RolCallback() {
            @Override
            public void onRol(@NonNull String rol) {
                SessionManager.guardar(LoginActivity.this, usuario.getUid(), usuario.getEmail(), rol);
                irAMain();
            }

            @Override
            public void onError(@NonNull Exception e) {
                // Sin red: usamos el último rol conocido de ESTE usuario o, en su defecto, el más restrictivo.
                String rolCacheado = SessionManager.getRolCacheado(LoginActivity.this, usuario.getUid());
                String rol = rolCacheado != null ? rolCacheado : SessionManager.ROL_ESTANDAR;
                if (rolCacheado == null) {
                    Toast.makeText(LoginActivity.this, R.string.error_verificar_rol, Toast.LENGTH_LONG).show();
                }
                SessionManager.guardar(LoginActivity.this, usuario.getUid(), usuario.getEmail(), rol);
                irAMain();
            }
        });
    }

    private void irAMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void mostrarCargando(boolean cargando) {
        progressLogin.setVisibility(cargando ? View.VISIBLE : View.GONE);
        scrollLogin.setVisibility(cargando ? View.INVISIBLE : View.VISIBLE);
        btnLogin.setEnabled(!cargando);
    }
}
