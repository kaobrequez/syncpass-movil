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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.kcorp.syncpass.data.auth.UserSession
import cl.kcorp.syncpass.data.model.Evento
import cl.kcorp.syncpass.data.model.EventoInput
import cl.kcorp.syncpass.data.model.MetricasEvento
import cl.kcorp.syncpass.data.model.Proyecto
import cl.kcorp.syncpass.data.model.Usuario
import cl.kcorp.syncpass.data.remote.SupabaseConfig
import cl.kcorp.syncpass.ui.components.EmptyMessage
import cl.kcorp.syncpass.ui.components.ErrorBanner
import cl.kcorp.syncpass.ui.components.LoadingMessage
import cl.kcorp.syncpass.ui.components.MetricBox
import cl.kcorp.syncpass.ui.components.PanelHeader
import cl.kcorp.syncpass.ui.components.QrCodeImage
import cl.kcorp.syncpass.ui.components.StatusChip
import cl.kcorp.syncpass.ui.components.formatDate
import cl.kcorp.syncpass.ui.components.formatDateTime
import cl.kcorp.syncpass.ui.components.formatDateTimeInput

private val docenteTabs = listOf("Proyectos", "Eventos", "Informes")

private fun String.toCoordinate(): Double? = trim().replace(',', '.').toDoubleOrNull()

@Composable
fun DocentePanel(session: UserSession) {
    val viewModel: DocenteViewModel = viewModel(key = "docente-${session.email}")
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(session.email) {
        viewModel.load(session)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            docenteTabs.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title) },
                )
            }
        }

        when (tab) {
            0 -> ProyectosTab(
                usuario = state.usuario,
                proyectos = state.proyectos,
                selectedProyecto = state.selectedProyectoId,
                loading = state.loading,
                error = state.error,
                onSelect = viewModel::selectProyecto,
                onCreate = viewModel::createProyecto,
                onUpdate = viewModel::updateProyecto,
                onDelete = viewModel::deleteProyecto,
            )
            1 -> EventosTab(
                proyecto = state.proyectos.firstOrNull { it.idProyecto == state.selectedProyectoId },
                eventos = state.eventos,
                metricas = state.metricas,
                error = state.error,
                onCreate = viewModel::createEvento,
                onUpdate = viewModel::updateEvento,
                onDelete = viewModel::deleteEvento,
            )
            else -> InformesTab(
                proyectos = state.proyectos,
                selectedProyecto = state.selectedProyectoId,
                eventos = state.eventos,
                metricas = state.metricas,
                onSelect = viewModel::selectProyecto,
            )
        }
    }
}

@Composable
private fun ProyectosTab(
    usuario: Usuario?,
    proyectos: List<Proyecto>,
    selectedProyecto: Int?,
    loading: Boolean,
    error: String?,
    onSelect: (Int) -> Unit,
    onCreate: (String, String?, String, String) -> Unit,
    onUpdate: (Proyecto, String, String?, String, String, String) -> Unit,
    onDelete: (Proyecto) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Proyecto?>(null) }
    var deleting by remember { mutableStateOf<Proyecto?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PanelHeader("Panel Docente", "Proyectos, eventos, métricas e informes.")
        }

        if (!SupabaseConfig.isConfigured) {
            item {
                ErrorBanner(
                    "Supabase no está configurado. Agrega SUPABASE_URL y SUPABASE_ANON_KEY en local.properties.",
                )
            }
        }
        if (error != null) {
            item { ErrorBanner(error) }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Proyectos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = { showCreate = !showCreate }, enabled = usuario != null) {
                    Text(if (showCreate) "Cancelar" else "+ Crear")
                }
            }
        }

        if (showCreate && usuario != null) {
            item {
                CrearProyectoForm(
                    onCancel = { showCreate = false },
                    onCreate = { titulo, descripcion, inicio, termino ->
                        onCreate(titulo, descripcion, inicio, termino)
                        showCreate = false
                    },
                )
            }
        }

        if (loading) {
            item { LoadingMessage() }
        }
        if (!loading && proyectos.isEmpty()) {
            item { EmptyMessage("No tienes proyectos aún. Crea el primero.") }
        }

        items(proyectos, key = { it.idProyecto }) { proyecto ->
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
                            text = proyecto.titulo,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        StatusChip(
                            text = proyecto.estado,
                            positive = proyecto.estado != "CANCELADO",
                        )
                    }
                    Text(
                        text = proyecto.descripcion ?: "Sin descripción",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = "${formatDate(proyecto.fechaInicio)} → ${formatDate(proyecto.fechaTermino)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (selectedProyecto == proyecto.idProyecto) {
                        Text(
                            text = "Proyecto seleccionado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { onSelect(proyecto.idProyecto) }) {
                            Text("Ver eventos")
                        }
                        OutlinedButton(onClick = { editing = proyecto }) {
                            Text("Editar")
                        }
                    }
                    TextButton(onClick = { deleting = proyecto }) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    editing?.let { proyecto ->
        ProyectoEditDialog(
            proyecto = proyecto,
            onDismiss = { editing = null },
            onSave = { titulo, descripcion, inicio, termino, estado ->
                onUpdate(proyecto, titulo, descripcion, inicio, termino, estado)
                editing = null
            },
        )
    }

    deleting?.let { proyecto ->
        ConfirmDialog(
            title = "Eliminar proyecto",
            message = "¿Eliminar \"${proyecto.titulo}\" y todos sus eventos?",
            onDismiss = { deleting = null },
            onConfirm = {
                onDelete(proyecto)
                deleting = null
            },
        )
    }
}

@Composable
private fun CrearProyectoForm(
    onCancel: () -> Unit,
    onCreate: (String, String?, String, String) -> Unit,
) {
    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var inicio by remember { mutableStateOf("") }
    var termino by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Nuevo proyecto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = inicio,
                onValueChange = { inicio = it },
                label = { Text("Fecha inicio (AAAA-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            OutlinedTextField(
                value = termino,
                onValueChange = { termino = it },
                label = { Text("Fecha término (AAAA-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (titulo.isNotBlank() && inicio.isNotBlank() && termino.isNotBlank()) {
                            onCreate(titulo.trim(), descripcion.trim().ifBlank { null }, inicio.trim(), termino.trim())
                        }
                    },
                ) {
                    Text("Crear proyecto")
                }
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun ProyectoEditDialog(
    proyecto: Proyecto,
    onDismiss: () -> Unit,
    onSave: (String, String?, String, String, String) -> Unit,
) {
    var titulo by remember(proyecto.idProyecto) { mutableStateOf(proyecto.titulo) }
    var descripcion by remember(proyecto.idProyecto) { mutableStateOf(proyecto.descripcion.orEmpty()) }
    var inicio by remember(proyecto.idProyecto) { mutableStateOf(formatDate(proyecto.fechaInicio)) }
    var termino by remember(proyecto.idProyecto) { mutableStateOf(formatDate(proyecto.fechaTermino)) }
    var estado by remember(proyecto.idProyecto) { mutableStateOf(proyecto.estado) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar proyecto") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = titulo, onValueChange = { titulo = it }, label = { Text("Título") })
                OutlinedTextField(value = descripcion, onValueChange = { descripcion = it }, label = { Text("Descripción") })
                OutlinedTextField(value = inicio, onValueChange = { inicio = it }, label = { Text("Inicio (AAAA-MM-DD)") })
                OutlinedTextField(value = termino, onValueChange = { termino = it }, label = { Text("Término (AAAA-MM-DD)") })
                OutlinedTextField(value = estado, onValueChange = { estado = it }, label = { Text("Estado") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (titulo.isNotBlank() && inicio.isNotBlank() && termino.isNotBlank()) {
                        onSave(titulo.trim(), descripcion.trim().ifBlank { null }, inicio.trim(), termino.trim(), estado.trim())
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun EventosTab(
    proyecto: Proyecto?,
    eventos: List<Evento>,
    metricas: Map<Int, MetricasEvento>,
    error: String?,
    onCreate: (Int, EventoInput) -> Unit,
    onUpdate: (Evento, EventoInput) -> Unit,
    onDelete: (Evento) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Evento?>(null) }
    var deleting by remember { mutableStateOf<Evento?>(null) }
    var qrEvento by remember { mutableStateOf<Evento?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PanelHeader(
                "Eventos del proyecto",
                proyecto?.titulo ?: "Selecciona un proyecto en la pestaña Proyectos.",
            )
        }

        if (error != null) {
            item { ErrorBanner(error) }
        }

        if (proyecto == null) {
            item { EmptyMessage("Primero debes seleccionar un proyecto.") }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Eventos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Button(onClick = { showCreate = !showCreate }) {
                        Text(if (showCreate) "Cancelar" else "+ Crear")
                    }
                }
            }

            if (showCreate) {
                item {
                    EventoForm(
                        tituloBoton = "Crear evento",
                        onCancel = { showCreate = false },
                        onSave = { input ->
                            onCreate(proyecto.idProyecto, input)
                            showCreate = false
                        },
                    )
                }
            }

            if (eventos.isEmpty()) {
                item { EmptyMessage("No hay eventos en este proyecto.") }
            }

            items(eventos, key = { it.idEvento }) { evento ->
                val m = metricas[evento.idEvento]
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
                            StatusChip(
                                text = evento.estado,
                                positive = evento.estado != "CANCELADO",
                            )
                        }
                        Text(
                            text = "${formatDateTime(evento.fechaInicio)} · ${evento.recinto ?: "Sin sala"} · Cupos: ${evento.cuposMaximos}",
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

                        if (m != null) {
                            Row(
                                modifier = Modifier.padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MetricBox(
                                    label = "Asistencia",
                                    value = "${m.totalAsistencias}/${m.totalInscritos} (${m.porcentajeAsistencia}%)",
                                    modifier = Modifier.weight(1f),
                                )
                                MetricBox(
                                    label = "Encuestas",
                                    value = "${m.encuestasRespondidas}/${m.totalInscritos} (${m.porcentajeEncuestas}%)",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MetricBox(
                                    label = "Satisfacción",
                                    value = "${m.satisfaccionPromedio}/4",
                                    modifier = Modifier.weight(1f),
                                )
                                MetricBox(
                                    label = "Comentarios",
                                    value = m.comentarios.size.toString(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (m.comentarios.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
                                Text(
                                    text = "Comentarios",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                m.comentarios.forEach { comentario ->
                                    Text(
                                        text = "• $comentario",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = { qrEvento = evento }) { Text("Ver QR") }
                            Button(onClick = { editing = evento }) { Text("Editar") }
                        }
                        TextButton(onClick = { deleting = evento }) {
                            Text("Eliminar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    editing?.let { evento ->
        EventoEditDialog(
            evento = evento,
            onDismiss = { editing = null },
            onSave = { input ->
                onUpdate(evento, input)
                editing = null
            },
        )
    }

    deleting?.let { evento ->
        ConfirmDialog(
            title = "Eliminar evento",
            message = "¿Eliminar \"${evento.nombre}\"?",
            onDismiss = { deleting = null },
            onConfirm = {
                onDelete(evento)
                deleting = null
            },
        )
    }

    qrEvento?.let { evento ->
        AlertDialog(
            onDismissRequest = { qrEvento = null },
            title = { Text(evento.nombre) },
            text = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    QrCodeImage(content = "syncpass:evento:${evento.idEvento}", size = 220.dp)
                    Text(
                        text = "syncpass:evento:${evento.idEvento}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { qrEvento = null }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun EventoForm(
    tituloBoton: String,
    onCancel: () -> Unit,
    onSave: (EventoInput) -> Unit,
) {
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var inicio by remember { mutableStateOf("") }
    var termino by remember { mutableStateOf("") }
    var cupos by remember { mutableStateOf("25") }
    var recinto by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var latitud by remember { mutableStateOf("") }
    var longitud by remember { mutableStateOf("") }
    var radio by remember { mutableStateOf("100") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Datos del evento", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre del evento") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = inicio,
                onValueChange = { inicio = it },
                label = { Text("Inicio (AAAA-MM-DDTHH:mm)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = termino,
                onValueChange = { termino = it },
                label = { Text("Término (AAAA-MM-DDTHH:mm)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cupos,
                    onValueChange = { cupos = it.filter { c -> c.isDigit() } },
                    label = { Text("Cupos") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = recinto,
                    onValueChange = { recinto = it },
                    label = { Text("Recinto/Sala") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = direccion,
                onValueChange = { direccion = it },
                label = { Text("Dirección (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                "Coordenadas opcionales para validar la asistencia dentro del radio.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latitud,
                    onValueChange = { latitud = it },
                    label = { Text("Latitud") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                OutlinedTextField(
                    value = longitud,
                    onValueChange = { longitud = it },
                    label = { Text("Longitud") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                OutlinedTextField(
                    value = radio,
                    onValueChange = { radio = it.filter { c -> c.isDigit() } },
                    label = { Text("Radio (m)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (nombre.isNotBlank() && inicio.isNotBlank() && termino.isNotBlank()) {
                            val lat = latitud.toCoordinate()
                            val lng = longitud.toCoordinate()
                            val coordenadas = if (lat != null && lng != null) lat to lng else null
                            onSave(
                                EventoInput(
                                    nombre = nombre.trim(),
                                    descripcion = descripcion.trim().ifBlank { null },
                                    fechaInicio = inicio.trim(),
                                    fechaTermino = termino.trim(),
                                    cuposMaximos = cupos.toIntOrNull() ?: 25,
                                    recinto = recinto.trim().ifBlank { null },
                                    direccion = direccion.trim().ifBlank { null },
                                    latitud = coordenadas?.first,
                                    longitud = coordenadas?.second,
                                    radioValidacionMetros = radio.toIntOrNull() ?: 100,
                                    estado = "PROGRAMADO",
                                ),
                            )
                        }
                    },
                ) { Text(tituloBoton) }
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun EventoEditDialog(
    evento: Evento,
    onDismiss: () -> Unit,
    onSave: (EventoInput) -> Unit,
) {
    var nombre by remember(evento.idEvento) { mutableStateOf(evento.nombre) }
    var descripcion by remember(evento.idEvento) { mutableStateOf(evento.descripcion.orEmpty()) }
    var inicio by remember(evento.idEvento) { mutableStateOf(formatDateTimeInput(evento.fechaInicio)) }
    var termino by remember(evento.idEvento) { mutableStateOf(formatDateTimeInput(evento.fechaTermino)) }
    var cupos by remember(evento.idEvento) { mutableStateOf(evento.cuposMaximos.toString()) }
    var recinto by remember(evento.idEvento) { mutableStateOf(evento.recinto.orEmpty()) }
    var direccion by remember(evento.idEvento) { mutableStateOf(evento.direccion.orEmpty()) }
    var latitud by remember(evento.idEvento) { mutableStateOf(evento.latitud?.toString().orEmpty()) }
    var longitud by remember(evento.idEvento) { mutableStateOf(evento.longitud?.toString().orEmpty()) }
    var radio by remember(evento.idEvento) {
        mutableStateOf(evento.radioValidacionMetros.toString())
    }
    var estado by remember(evento.idEvento) { mutableStateOf(evento.estado) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar evento") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre") })
                OutlinedTextField(value = descripcion, onValueChange = { descripcion = it }, label = { Text("Descripción") })
                OutlinedTextField(value = inicio, onValueChange = { inicio = it }, label = { Text("Inicio (AAAA-MM-DDTHH:mm)") })
                OutlinedTextField(value = termino, onValueChange = { termino = it }, label = { Text("Término (AAAA-MM-DDTHH:mm)") })
                OutlinedTextField(
                    value = cupos,
                    onValueChange = { cupos = it.filter { c -> c.isDigit() } },
                    label = { Text("Cupos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(value = recinto, onValueChange = { recinto = it }, label = { Text("Recinto/Sala") })
                OutlinedTextField(value = direccion, onValueChange = { direccion = it }, label = { Text("Dirección") })
                OutlinedTextField(
                    value = latitud,
                    onValueChange = { latitud = it },
                    label = { Text("Latitud") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                OutlinedTextField(
                    value = longitud,
                    onValueChange = { longitud = it },
                    label = { Text("Longitud") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                OutlinedTextField(
                    value = radio,
                    onValueChange = { radio = it.filter { c -> c.isDigit() } },
                    label = { Text("Radio de validación (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(value = estado, onValueChange = { estado = it }, label = { Text("Estado") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nombre.isNotBlank() && inicio.isNotBlank() && termino.isNotBlank()) {
                        val lat = latitud.toCoordinate()
                        val lng = longitud.toCoordinate()
                        val coordenadas = if (lat != null && lng != null) lat to lng else null
                        onSave(
                            EventoInput(
                                nombre = nombre.trim(),
                                descripcion = descripcion.trim().ifBlank { null },
                                fechaInicio = inicio.trim(),
                                fechaTermino = termino.trim(),
                                cuposMaximos = cupos.toIntOrNull() ?: 25,
                                recinto = recinto.trim().ifBlank { null },
                                direccion = direccion.trim().ifBlank { null },
                                latitud = coordenadas?.first,
                                longitud = coordenadas?.second,
                                radioValidacionMetros = radio.toIntOrNull() ?: 100,
                                estado = estado.trim(),
                            ),
                        )
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun InformesTab(
    proyectos: List<Proyecto>,
    selectedProyecto: Int?,
    eventos: List<Evento>,
    metricas: Map<Int, MetricasEvento>,
    onSelect: (Int) -> Unit,
) {
    var informe by remember { mutableStateOf("") }
    var guardado by remember { mutableStateOf(false) }
    val proyecto = proyectos.firstOrNull { it.idProyecto == selectedProyecto }
    val totalInscritos = metricas.values.sumOf { it.totalInscritos }
    val totalAsistencias = metricas.values.sumOf { it.totalAsistencias }
    val totalEncuestas = metricas.values.sumOf { it.encuestasRespondidas }
    val promedio = if (totalEncuestas > 0) {
        Math.round(metricas.values.sumOf { it.satisfaccionPromedio * it.encuestasRespondidas } / totalEncuestas * 100.0) / 100.0
    } else {
        0.0
    }
    val comentarios = metricas.values.flatMap { it.comentarios }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PanelHeader("Informes de impacto", proyecto?.titulo ?: "Selecciona un proyecto.")
        }

        if (proyectos.size > 1) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    proyectos.take(4).forEach { p ->
                        OutlinedButton(onClick = { onSelect(p.idProyecto) }) {
                            Text(p.titulo, maxLines = 1)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Resumen del proyecto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    InfoLine("Eventos", eventos.size.toString())
                    InfoLine("Inscritos", totalInscritos.toString())
                    InfoLine("Asistencias", totalAsistencias.toString())
                    InfoLine("Encuestas respondidas", totalEncuestas.toString())
                    InfoLine("Satisfacción promedio", "$promedio/4")
                    if (comentarios.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Comentarios", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        comentarios.forEach { c ->
                            Text(
                                "• $c",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = informe,
                onValueChange = { informe = it; guardado = false },
                label = { Text("Redacta el informe de impacto") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
            )
        }
        item {
            Button(onClick = { guardado = true }) { Text("Guardar informe") }
            if (guardado) {
                Text(
                    "Informe guardado en esta sesión.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
