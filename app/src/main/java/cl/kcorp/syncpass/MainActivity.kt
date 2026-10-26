package cl.kcorp.syncpass

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.kcorp.syncpass.ui.login.LoginScreen
import cl.kcorp.syncpass.ui.theme.SyncPassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SyncPassTheme {
                LoginScreen(
                    onLoginSuccess = { tokens ->
                        Log.i("SyncPassAuth", "¡Token Obtenido Exitosamente!")
                        Log.i("SyncPassAuth", "ID Token: ${tokens.idToken}")
                        Log.i("SyncPassAuth", "Access Token: ${tokens.accessToken}")
                        Toast.makeText(
                            this,
                            "Login exitoso como ${tokens.username}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }
}