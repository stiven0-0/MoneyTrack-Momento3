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

import com.example.moneytrack.data.FirestoreHelper;
import com.example.moneytrack.data.SessionManager;
import com.example.moneytrack.data.UsuarioRepository;
import com.example.moneytrack.util.AuthMensajes;
import com.example.moneytrack.util.SimpleTextWatcher;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends AppCompatActivity {

    private static final int LONGITUD_MINIMA_PASSWORD = 6;

    private FirebaseAuth auth;
    private UsuarioRepository usuarioRepository;

    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private TextInputLayout tilConfirmar;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmar;
    private Button btnRegistrar;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.registerRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        usuarioRepository = new UsuarioRepository();

        MaterialToolbar toolbar = findViewById(R.id.toolbarRegistro);
        tilEmail = findViewById(R.id.tilEmailReg);
        tilPassword = findViewById(R.id.tilPasswordReg);
        tilConfirmar = findViewById(R.id.tilConfirmarReg);
        etEmail = findViewById(R.id.etEmailReg);
        etPassword = findViewById(R.id.etPasswordReg);
        etConfirmar = findViewById(R.id.etConfirmarReg);
        btnRegistrar = findViewById(R.id.btnRegistrar);
        progress = findViewById(R.id.progressRegistro);
        TextView tvIrLogin = findViewById(R.id.tvIrLogin);

        toolbar.setNavigationOnClickListener(v -> finish());
        tvIrLogin.setOnClickListener(v -> finish());

        configurarValidacionEnTiempoReal();
        btnRegistrar.setOnClickListener(v -> registrar());
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
                if (s.length() == 0 || s.length() >= LONGITUD_MINIMA_PASSWORD) {
                    tilPassword.setError(null);
                } else {
                    tilPassword.setError(getString(R.string.error_password_corto));
                }
                validarCoincidencia();
            }
        });

        etConfirmar.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validarCoincidencia();
            }
        });
    }

    private void validarCoincidencia() {
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirmar = etConfirmar.getText() != null ? etConfirmar.getText().toString() : "";
        if (confirmar.isEmpty() || confirmar.equals(password)) {
            tilConfirmar.setError(null);
        } else {
            tilConfirmar.setError(getString(R.string.error_password_no_coincide));
        }
    }

    private void registrar() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirmar = etConfirmar.getText() != null ? etConfirmar.getText().toString() : "";

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
        if (password.length() < LONGITUD_MINIMA_PASSWORD) {
            tilPassword.setError(getString(R.string.error_password_corto));
            etPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmar)) {
            tilConfirmar.setError(getString(R.string.error_password_no_coincide));
            etConfirmar.requestFocus();
            return;
        }

        establecerCargando(true);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(resultado -> {
                    FirebaseUser usuario = resultado.getUser();
                    if (usuario == null) {
                        establecerCargando(false);
                        return;
                    }
                    crearPerfilYEntrar(usuario);
                })
                .addOnFailureListener(e -> {
                    establecerCargando(false);
                    Toast.makeText(this, AuthMensajes.desde(this, e), Toast.LENGTH_LONG).show();
                });
    }

    /** Todo usuario que se registra desde la app nace con rol "estandar". */
    private void crearPerfilYEntrar(@NonNull FirebaseUser usuario) {
        usuarioRepository.crearUsuario(usuario.getUid(), usuario.getEmail(), new FirestoreHelper.OperacionCallback() {
            @Override
            public void onExito() {
                SessionManager.guardar(RegisterActivity.this, usuario.getUid(), usuario.getEmail(),
                        SessionManager.ROL_ESTANDAR);
                Toast.makeText(RegisterActivity.this, R.string.mensaje_registro_exitoso, Toast.LENGTH_SHORT).show();
                irAMain();
            }

            @Override
            public void onError(@NonNull Exception e) {
                // La cuenta existe en Auth; el perfil se completará en el próximo login.
                SessionManager.guardar(RegisterActivity.this, usuario.getUid(), usuario.getEmail(),
                        SessionManager.ROL_ESTANDAR);
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

    private void establecerCargando(boolean cargando) {
        progress.setVisibility(cargando ? View.VISIBLE : View.GONE);
        btnRegistrar.setEnabled(!cargando);
    }
}
