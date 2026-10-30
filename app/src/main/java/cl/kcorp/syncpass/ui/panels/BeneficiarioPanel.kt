package cl.kcorp.syncpass.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.kcorp.syncpass.data.auth.UserSession
import cl.kcorp.syncpass.data.model.Evento
import cl.kcorp.syncpass.data.model.Inscripcion
import cl.kcorp.syncpass.data.model.Usuario
import cl.kcorp.syncpass.data.remote.SupabaseConfig
import cl.kcorp.syncpass.ui.components.EmptyMessage
import cl.kcorp.syncpass.ui.components.ErrorBanner
import cl.kcorp.syncpass.ui.components.InfoBanner
import cl.kcorp.syncpass.ui.components.InfoRow
import cl.kcorp.syncpass.ui.components.LoadingMessage
import cl.kcorp.syncpass.ui.components.PanelHeader
import cl.kcorp.syncpass.ui.components.PillTabRow
import cl.kcorp.syncpass.ui.components.QrCodeImage
import cl.kcorp.syncpass.ui.components.StatusChip
import cl.kcorp.syncpass.ui.components.formatDateTime

private val beneficiarioTabs = listOf("Mi información", "Eventos", "Mi pase QR")

@Composable
fun BeneficiarioPanel(session: UserSession) {
    val viewModel: BeneficiarioViewModel = viewModel(key = "beneficiario-${session.email}")
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(session.email) {
        viewModel.load(session)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            PillTabRow(
                items = beneficiarioTabs,
                selectedIndex = state.tab,
                onSelect = viewModel::selectTab,
            )
        }

        when (state.tab) {
            0 -> InfoTab(state.usuario, session, state.loading, state.error)
            1 -> EventosTab(
                eventos = state.eventos,
                loading = state.loading,
                error = state.error,
                notice = state.notice,
                estaInscrito = { idEvento ->
                    state.inscripciones.any {
                        it.idEvento == idEvento && it.estado != "CANCELADO"
                    }
                },
                tieneAsistencia = { idEvento ->
                    state.inscripciones.firstOrNull { it.idEvento == idEvento }
                        ?.asistencia?.estadoValidacion == "VALIDADA"
                },
                yaRespondioEncuesta = { idEvento ->
                    state.inscripciones.firstOrNull { it.idEvento == idEvento }?.encuesta != null
                },
                encuestaEvento = state.encuestaEvento,
                calificacion = state.calificacion,
                comentarios = state.comentarios,
                onCalificacion = viewModel::setCalificacion,
                onComentarios = viewModel::setComentarios,
                onEncuestaToggle = viewModel::toggleEncuesta,
                onInscribir = viewModel::inscribirse,
                onDesinscribir = viewModel::desinscribirse,
                onVerQr = viewModel::verQr,
                onEnviarEncuesta = viewModel::enviarEncuesta,
            )
            else -> QrTab(
                usuario = state.usuario,
                eventos = state.eventos,
                inscripciones = state.inscripciones,
                qrEvento = state.qrEvento,
                loading = state.loading,
            )
        }
    }
}

@Composable
private fun InfoTab(
    usuario: Usuario?,
    session: UserSession,
    loading: Boolean,
    error: String?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PanelHeader("Panel Beneficiario", "Tu información y eventos disponibles.") }
        if (!SupabaseConfig.isConfigured) {
            item {
                ErrorBanner(
                    "Supabase no está configurado. Agrega SUPABASE_URL y SUPABASE_ANON_KEY en local.properties.",
                )
            }
        }
        if (error != null) item { ErrorBanner(error) }
        if (loading) {
            item { LoadingMessage() }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InfoRow("Nombre", session.name)
                        InfoRow("Correo", usuario?.correo ?: session.email)
                        InfoRow("Rol", session.role.label)
                        InfoRow("Estado", usuario?.estado ?: "-")
                    }
                }
            }
        }
    }
}

@Composable
private fun EventosTab(
    eventos: List<Evento>,
    loading: Boolean,
    error: String?,
    notice: String?,
    estaInscrito: (Int) -> Boolean,
    tieneAsistencia: (Int) -> Boolean,
    yaRespondioEncuesta: (Int) -> Boolean,
    encuestaEvento: Int?,
    calificacion: Int,
    comentarios: String,
    onCalificacion: (Int) -> Unit,
    onComentarios: (String) -> Unit,
    onEncuestaToggle: (Int) -> Unit,
    onInscribir: (Evento) -> Unit,
    onDesinscribir: (Evento) -> Unit,
    onVerQr: (Evento) -> Unit,
    onEnviarEncuesta: (Evento) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PanelHeader("Eventos", "Inscríbete, muestra tu QR y responde la encuesta.") }
        if (error != null) item { ErrorBanner(error) }
        if (notice != null) item { InfoBanner(notice) }
        if (loading) item { LoadingMessage() }
        if (!loading && eventos.isEmpty()) item { EmptyMessage("No hay eventos disponibles.") }

        items(eventos, key = { it.idEvento }) { evento ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
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
                        StatusChip(
                            text = if (estaInscrito(evento.idEvento)) "Inscrito" else "No inscrito",
                            positive = estaInscrito(evento.idEvento),
                        )
                    }
                    Text(
                        text = "${formatDateTime(evento.fechaInicio)} · ${evento.recinto ?: "Sin sala"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (evento.descripcion != null) {
                        Text(
                            text = evento.descripcion,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!estaInscrito(evento.idEvento)) {
                            Button(onClick = { onInscribir(evento) }) { Text("Inscribirme") }
                        } else if (!tieneAsistencia(evento.idEvento)) {
                            OutlinedButton(onClick = { onVerQr(evento) }) { Text("Mostrar QR") }
                            TextButton(onClick = { onDesinscribir(evento) }) {
                                Text("Desuscribirme", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (estaInscrito(evento.idEvento) && tieneAsistencia(evento.idEvento)) {
                        if (yaRespondioEncuesta(evento.idEvento)) {
                            Text(
                                text = "Encuesta enviada ⭐",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            OutlinedButton(
                                onClick = { onEncuestaToggle(evento.idEvento) },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(if (encuestaEvento == evento.idEvento) "Cerrar encuesta" else "Responder encuesta")
                            }
                            if (encuestaEvento == evento.idEvento) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Satisfacción (1 = pésimo, 4 = excelente)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        (1..4).forEach { n ->
                                            if (calificacion == n) {
                                                Button(
                                                    onClick = { onCalificacion(n) },
                                                    modifier = Modifier.size(48.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                ) { Text("★", fontSize = 20.sp) }
                                            } else {
                                                OutlinedButton(
                                                    onClick = { onCalificacion(n) },
                                                    modifier = Modifier.size(48.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                ) {
                                                    Text(
                                                        "★",
                                                        fontSize = 20.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            "${calificacion} de 4",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 4.dp),
                                        )
                                    }
                                    OutlinedTextField(
                                        value = comentarios,
                                        onValueChange = onComentarios,
                                        label = { Text("Comentarios (opcional)") },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Button(onClick = { onEnviarEncuesta(evento) }) {
                                        Text("Enviar encuesta")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrTab(
    usuario: Usuario?,
    eventos: List<Evento>,
    inscripciones: List<Inscripcion>,
    qrEvento: Int?,
    loading: Boolean,
) {
    val activas = inscripciones.filter { it.estado != "CANCELADO" }
    val mostrar = if (qrEvento != null) {
        activas.filter { it.idEvento == qrEvento }.ifEmpty { activas }
    } else {
        activas
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PanelHeader("Mi pase QR", "Muestra este código al ingresar al evento.") }

        if (loading) {
            item { LoadingMessage() }
        }
        if (!loading && activas.isEmpty()) {
            item { EmptyMessage("No tienes inscripciones activas. Inscríbete en un evento.") }
        }

        items(mostrar, key = { it.idInscripcion }) { inscripcion ->
            val evento = eventos.firstOrNull { it.idEvento == inscripcion.idEvento }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = evento?.nombre ?: "Evento #${inscripcion.idEvento}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${formatDateTime(evento?.fechaInicio)} · ${evento?.recinto ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (usuario != null) {
                        QrCodeImage(
                            content = "syncpass:asistencia:${usuario.idUsuario}:${inscripcion.idEvento}",
                            size = 240.dp,
                        )
                        Text(
                            text = "syncpass:asistencia:${usuario.idUsuario}:${inscripcion.idEvento}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LoadingMessage("Cargando usuario...")
                    }
                }
            }
        }
    }
}
