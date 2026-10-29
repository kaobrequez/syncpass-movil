package cl.kcorp.syncpass.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.kcorp.syncpass.data.auth.UserSession
import cl.kcorp.syncpass.data.remote.SupabaseConfig
import cl.kcorp.syncpass.ui.components.EmptyMessage
import cl.kcorp.syncpass.ui.components.ErrorBanner
import cl.kcorp.syncpass.ui.components.LoadingMessage
import cl.kcorp.syncpass.ui.components.PanelHeader
import cl.kcorp.syncpass.ui.components.StatusChip

private val adminTabs = listOf("Usuarios", "Docente", "Voluntario", "Beneficiario")

@Composable
fun AdminPanel(session: UserSession) {
    val viewModel: AdminViewModel = viewModel(key = "admin-${session.email}")
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = state.tab,
            edgePadding = 0.dp,
        ) {
            adminTabs.forEachIndexed { index, title ->
                Tab(
                    selected = state.tab == index,
                    onClick = { viewModel.selectTab(index) },
                    text = { Text(title) },
                )
            }
        }

        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
            when (state.tab) {
                0 -> UsuariosTab(
                    state = state,
                    onRefresh = viewModel::loadUsuarios,
                )
                1 -> DocentePanel(session)
                2 -> VoluntarioPanel(session)
                else -> BeneficiarioPanel(session)
            }
        }
    }
}

@Composable
private fun UsuariosTab(
    state: AdminUiState,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PanelHeader(
                title = "Panel Administrador",
                subtitle = "Gestión de usuarios y acceso a todas las vistas.",
            )
        }

        if (!SupabaseConfig.isConfigured) {
            item {
                ErrorBanner(
                    "Supabase no está configurado. Agrega SUPABASE_URL y SUPABASE_ANON_KEY en local.properties.",
                )
            }
        }

        item {
            OutlinedButton(onClick = onRefresh) { Text("Actualizar usuarios") }
        }

        if (state.loading) {
            item { LoadingMessage() }
        }

        if (!state.loading && state.usuarios.isEmpty()) {
            item { EmptyMessage("No se encontraron usuarios.") }
        }

        items(state.usuarios, key = { it.idUsuario }) { usuario ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = usuario.nombreMostrar.ifBlank { "Sin nombre" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        StatusChip(
                            text = usuario.estado.ifBlank { "-" },
                            positive = usuario.estado.equals("ACTIVO", ignoreCase = true),
                        )
                    }
                    Text(
                        text = usuario.correo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = "Rol: ${usuario.rol.ifBlank { "-" }}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
