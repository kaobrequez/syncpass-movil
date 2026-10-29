package cl.kcorp.syncpass.ui.panels

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.kcorp.syncpass.data.auth.UserSession
import cl.kcorp.syncpass.data.location.LocationProvider
import cl.kcorp.syncpass.data.model.Evento
import cl.kcorp.syncpass.data.model.Inscrito
import cl.kcorp.syncpass.data.remote.SupabaseConfig
import cl.kcorp.syncpass.ui.components.EmptyMessage
import cl.kcorp.syncpass.ui.components.ErrorBanner
import cl.kcorp.syncpass.ui.components.LoadingMessage
import cl.kcorp.syncpass.ui.components.PanelHeader
import cl.kcorp.syncpass.ui.components.StatusChip
import cl.kcorp.syncpass.ui.components.formatDateTime
import cl.kcorp.syncpass.ui.scanner.QrScannerScreen
import kotlinx.coroutines.launch

@Composable
fun VoluntarioPanel(@Suppress("UNUSED_PARAMETER") session: UserSession) {
    val viewModel: VoluntarioViewModel = viewModel(key = "voluntario-${session.email}")
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    LaunchedEffect(Unit) {
        viewModel.loadEventos()
    }

    LaunchedEffect(state.view, state.selectedEvento?.idEvento) {
        val evento = state.selectedEvento
        val needsLocation = evento?.latitud != null && evento.longitud != null
        if (
            state.view == VoluntarioView.SCANNER &&
            needsLocation &&
            !LocationProvider.hasPermission(context)
        ) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    when (state.view) {
        VoluntarioView.EVENTOS -> EventosVoluntario(
            eventos = state.eventos,
            loading = state.loading,
            onInscritos = viewModel::openInscritos,
            onScanner = viewModel::openScanner,
        )

        VoluntarioView.INSCRITOS -> InscritosVoluntario(
            evento = state.selectedEvento,
            inscritos = state.inscritos,
            onBack = viewModel::backToEventos,
            onRefresh = viewModel::refreshInscritos,
        )

        VoluntarioView.SCANNER -> Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Escanear QR",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.selectedEvento?.nombre.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = viewModel::backToEventos) { Text("← Volver") }
            }

            QrScannerScreen(
                modifier = Modifier.weight(1f),
                onCode = { code ->
                    val evento = state.selectedEvento
                    val needsLocation = evento?.latitud != null && evento.longitud != null
                    scope.launch {
                        val location = if (needsLocation) {
                            LocationProvider.currentLocation(context)
                        } else {
                            null
                        }
                        viewModel.processCode(
                            code = code,
                            latitud = location?.latitude,
                            longitud = location?.longitude,
                        )
                    }
                },
                onClose = viewModel::backToEventos,
            )

            if (state.scanResult != null) {
                Text(
                    text = "Último: ${state.scanResult}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.scanStatus != null) {
                Text(
                    text = state.scanStatus.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun EventosVoluntario(
    eventos: List<Evento>,
    loading: Boolean,
    onInscritos: (Evento) -> Unit,
    onScanner: (Evento) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PanelHeader("Panel Voluntario", "Eventos, scanner de asistencia y lista de inscritos.") }
        if (!SupabaseConfig.isConfigured) {
            item {
                ErrorBanner(
                    "Supabase no está configurado. Agrega SUPABASE_URL y SUPABASE_ANON_KEY en local.properties.",
                )
            }
        }
        if (loading) item { LoadingMessage() }
        if (!loading && eventos.isEmpty()) item { EmptyMessage("No hay eventos.") }

        items(eventos, key = { it.idEvento }) { evento ->
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
                            text = evento.nombre,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        StatusChip(text = evento.estado, positive = evento.estado != "CANCELADO")
                    }
                    Text(
                        text = "${formatDateTime(evento.fechaInicio)} · ${evento.recinto ?: "Sin sala"} · Cupos: ${evento.cuposMaximos}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { onInscritos(evento) }) {
                            Text("Lista de inscritos")
                        }
                        Button(onClick = { onScanner(evento) }) {
                            Text("Escanear QR")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InscritosVoluntario(
    evento: Evento?,
    inscritos: List<Inscrito>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Inscritos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = evento?.nombre.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onBack) { Text("← Volver") }
            }
        }

        item {
            OutlinedButton(onClick = onRefresh) { Text("Actualizar") }
        }

        if (inscritos.isEmpty()) {
            item { EmptyMessage("Sin inscritos.") }
        }

        items(inscritos, key = { it.idInscripcion }) { inscrito ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = inscrito.nombre,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${inscrito.correo} · ${inscrito.estado}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusChip(
                        text = if (inscrito.asistio) "Presente" else "Pendiente",
                        positive = inscrito.asistio,
                    )
                }
            }
        }
    }
}
