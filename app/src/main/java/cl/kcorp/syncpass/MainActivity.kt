package cl.kcorp.syncpass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cl.kcorp.syncpass.data.auth.CognitoAuthManager
import cl.kcorp.syncpass.data.auth.JwtUtils
import cl.kcorp.syncpass.data.auth.UserSession
import cl.kcorp.syncpass.data.repository.SyncPassRepository
import cl.kcorp.syncpass.data.sync.SyncScheduler
import cl.kcorp.syncpass.ui.dashboard.DashboardScreen
import cl.kcorp.syncpass.ui.login.LoginScreen
import cl.kcorp.syncpass.ui.theme.SyncPassTheme
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncPassRepository.init(applicationContext)
        SyncScheduler.schedule(applicationContext)
        enableEdgeToEdge()
        setContent {
            SyncPassTheme {
                val authManager = remember { CognitoAuthManager(this@MainActivity) }
                var session by remember { mutableStateOf<UserSession?>(null) }
                var restoringSession by remember { mutableStateOf(true) }

                LaunchedEffect(authManager) {
                    val restored = withTimeoutOrNull(5_000) {
                        authManager.restoreSession()?.getOrNull()
                    }
                    session = restored?.let { JwtUtils.session(it) }
                    restoringSession = false
                }

                val current = session
                when {
                    restoringSession -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    current == null -> LoginScreen(
                        onLoginSuccess = { tokens ->
                            session = JwtUtils.session(tokens)
                        },
                    )

                    else -> DashboardScreen(
                        session = current,
                        onLogout = {
                            authManager.signOut()
                            session = null
                            viewModelStore.clear()
                        },
                    )
                }
            }
        }
    }
}
