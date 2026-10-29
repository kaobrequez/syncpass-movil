package cl.kcorp.syncpass.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.kcorp.syncpass.data.auth.Role
import cl.kcorp.syncpass.data.auth.UserSession
import cl.kcorp.syncpass.ui.components.InfoBanner
import cl.kcorp.syncpass.ui.panels.AdminPanel
import cl.kcorp.syncpass.ui.panels.BeneficiarioPanel
import cl.kcorp.syncpass.ui.panels.DocentePanel
import cl.kcorp.syncpass.ui.panels.VoluntarioPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    session: UserSession,
    onLogout: () -> Unit,
) {
    val viewModel: DashboardViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    Column {
                        Text(
                            text = "SyncPass",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${session.name} · ${session.email}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Text(
                        text = session.role.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = onLogout) {
                        Text("Salir")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.pendingSync > 0) {
                InfoBanner(
                    message = "${state.pendingSync} registro(s) pendiente(s) de sincronizar.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                when (session.role) {
                    Role.ADMINISTRADOR -> AdminPanel(session)
                    Role.DOCENTE -> DocentePanel(session)
                    Role.VOLUNTARIO -> VoluntarioPanel(session)
                    Role.BENEFICIARIO -> BeneficiarioPanel(session)
                    Role.DESCONOCIDO -> {
                        Text(
                            text = "No se reconoció tu rol. Contacta al administrador.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}
