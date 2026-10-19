package pe.edu.cibertec.stockfacil

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth

/** Acceso por correo/contraseña. Las cuentas se crean en Firebase Console. */
class LoginActivity : AppCompatActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailLayout = findViewById<TextInputLayout>(R.id.layoutEmail)
        val passwordLayout = findViewById<TextInputLayout>(R.id.layoutPassword)
        val email = findViewById<TextInputEditText>(R.id.inputEmail)
        val password = findViewById<TextInputEditText>(R.id.inputPassword)
        val message = findViewById<TextView>(R.id.tvLoginMessage)
        val submit = findViewById<MaterialButton>(R.id.btnLogin)

        submit.setOnClickListener {
            val address = email.text?.toString()?.trim().orEmpty()
            val secret = password.text?.toString().orEmpty()
            emailLayout.error = null
            passwordLayout.error = null
            message.text = ""

            if (address.isBlank() ||
                !android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()
            ) {
                emailLayout.error = "Escribe un correo válido."
                return@setOnClickListener
            }

            if (secret.isBlank()) {
                passwordLayout.error = "Ingresa tu contraseña."
                return@setOnClickListener
            }

            submit.isEnabled = false
            message.text = "Verificando acceso…"

            auth.signInWithEmailAndPassword(address, secret)
                .addOnCompleteListener(this) { task ->
                    if (isFinishing || isDestroyed) {
                        return@addOnCompleteListener
                    }

                    submit.isEnabled = true

                    if (task.isSuccessful) {
                        openInventory()
                    } else {
                        when {
                            task.exception is
                                    com.google.firebase.FirebaseNetworkException -> {
                                message.text =
                                    "No hay conexión. Revisa tu internet e inténtalo nuevamente."
                            }

                            task.exception?.message?.contains(
                                "TOO_MANY_ATTEMPTS",
                                ignoreCase = true
                            ) == true -> {
                                message.text =
                                    "Demasiados intentos. Espera un momento e inténtalo nuevamente."
                            }

                            else -> {
                                message.text =
                                    "Correo o contraseña incorrectos. Inténtalo nuevamente."
                            }
                        }
                    }
                }
        }
    }

    override fun onStart() {
        super.onStart()

        // Firebase conserva la sesión mientras el usuario no la cierre.
        if (auth.currentUser != null) {
            openInventory()
        }
    }

    private fun openInventory() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
