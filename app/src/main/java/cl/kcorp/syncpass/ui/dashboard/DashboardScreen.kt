package cl.kcorp.syncpass.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.kcorp.syncpass.data.auth.Role
import cl.kcorp.syncpass.data.auth.UserSession
import cl.kcorp.syncpass.ui.components.BrandLogo
import cl.kcorp.syncpass.ui.components.InfoBanner
import cl.kcorp.syncpass.ui.panels.AdminPanel
import cl.kcorp.syncpass.ui.panels.BeneficiarioPanel
import cl.kcorp.syncpass.ui.panels.DocentePanel
import cl.kcorp.syncpass.ui.panels.VoluntarioPanel
import cl.kcorp.syncpass.ui.theme.BrandPrimaryLight
import cl.kcorp.syncpass.ui.theme.BrandSecondary

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
                    containerColor = BrandSecondary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BrandLogo(size = 38.dp, corner = 12.dp, textSize = 15.sp)
                        Column {
                            Text(
                                text = "SyncPass",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = "${session.role.label} · ${session.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Salir", color = BrandPrimaryLight)
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
