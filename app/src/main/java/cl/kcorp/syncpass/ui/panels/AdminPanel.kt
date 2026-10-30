package cl.kcorp.syncpass.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.kcorp.syncpass.data.auth.UserSession
import cl.kcorp.syncpass.data.model.Rol
import cl.kcorp.syncpass.data.model.UsuarioItem
import cl.kcorp.syncpass.data.remote.SupabaseConfig
import cl.kcorp.syncpass.ui.components.EmptyMessage
import cl.kcorp.syncpass.ui.components.ErrorBanner
import cl.kcorp.syncpass.ui.components.LoadingMessage
import cl.kcorp.syncpass.ui.components.PanelHeader
import cl.kcorp.syncpass.ui.components.PillTabRow
import cl.kcorp.syncpass.ui.components.StatusChip

private val adminTabs = listOf("Usuarios", "Docente", "Voluntario", "Beneficiario")
private val estadosUsuario = listOf("ACTIVO", "INACTIVO", "BLOQUEADO")

@Composable
fun AdminPanel(session: UserSession) {
    val viewModel: AdminViewModel = viewModel(key = "admin-${session.email}")
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(session.email) {
        viewModel.initialize(session)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            PillTabRow(
                items = adminTabs,
                selectedIndex = state.tab,
                onSelect = viewModel::selectTab,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (state.tab) {
                0 -> UsuariosTab(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onCreate = viewModel::createUsuario,
                    onUpdate = viewModel::updateUsuario,
                    onDelete = viewModel::deleteUsuario,
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
    onCreate: (Int, String, String, String, String) -> Unit,
    onUpdate: (UsuarioItem, Int, String, String, String, String) -> Unit,
    onDelete: (UsuarioItem) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UsuarioItem?>(null) }
    var deleting by remember { mutableStateOf<UsuarioItem?>(null) }

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

        if (state.error != null) {
            item { ErrorBanner(state.error) }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Usuarios (${state.usuarios.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = { showCreate = !showCreate }) {
                    Text(if (showCreate) "Cancelar" else "+ Nuevo usuario")
                }
            }
        }

        if (showCreate) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Nuevo usuario",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        UsuarioEditor(
                            roles = state.roles,
                            initial = null,
                            submitLabel = "Crear usuario",
                            onCancel = { showCreate = false },
                            onSubmit = { idRol, nombreUsuario, nombreMostrar, correo, estado ->
                                onCreate(idRol, nombreUsuario, nombreMostrar, correo, estado)
                                showCreate = false
                            },
                        )
                    }
                }
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
                shape = MaterialTheme.shapes.large,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = usuario.nombreMostrar.ifBlank { "Sin nombre" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (usuario.nombreUsuario.isNotBlank()) {
                                Text(
                                    text = "@${usuario.nombreUsuario}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { editing = usuario }) { Text("Editar") }
                        TextButton(onClick = { deleting = usuario }) {
                            Text("Eliminar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    editing?.let { usuario ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Editar usuario") },
            text = {
                UsuarioEditor(
                    roles = state.roles,
                    initial = usuario,
                    submitLabel = "Guardar cambios",
                    onCancel = { editing = null },
                    onSubmit = { idRol, nombreUsuario, nombreMostrar, correo, estado ->
                        onUpdate(usuario, idRol, nombreUsuario, nombreMostrar, correo, estado)
                        editing = null
                    },
                )
            },
            confirmButton = {},
        )
    }

    deleting?.let { usuario ->
        ConfirmDialog(
            title = "Eliminar usuario",
            message = "¿Eliminar a \"${usuario.nombreMostrar.ifBlank { usuario.correo }}\"?",
            onDismiss = { deleting = null },
            onConfirm = {
                onDelete(usuario)
                deleting = null
            },
        )
    }
}

@Composable
private fun UsuarioEditor(
    roles: List<Rol>,
    initial: UsuarioItem?,
    submitLabel: String,
    onCancel: () -> Unit,
    onSubmit: (Int, String, String, String, String) -> Unit,
) {
    val key = initial?.idUsuario ?: -1
    var nombreUsuario by remember(key) { mutableStateOf(initial?.nombreUsuario.orEmpty()) }
    var nombreMostrar by remember(key) { mutableStateOf(initial?.nombreMostrar.orEmpty()) }
    var correo by remember(key) { mutableStateOf(initial?.correo.orEmpty()) }
    var idRol by remember(key, roles) {
        mutableStateOf(initial?.idRol?.takeIf { it > 0 } ?: roles.firstOrNull()?.idRol ?: 1)
    }
    var estado by remember(key) { mutableStateOf(initial?.estado?.ifBlank { "ACTIVO" } ?: "ACTIVO") }

    val puedeGuardar = nombreUsuario.isNotBlank() && nombreMostrar.isNotBlank() && correo.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = nombreUsuario,
            onValueChange = { nombreUsuario = it },
            label = { Text("Nombre de usuario") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = nombreMostrar,
            onValueChange = { nombreMostrar = it },
            label = { Text("Nombre a mostrar") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = correo,
            onValueChange = { correo = it },
            label = { Text("Correo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RolDropdown(
                roles = roles,
                selectedId = idRol,
                onSelect = { idRol = it },
                modifier = Modifier.weight(1f),
            )
            EstadoDropdown(
                selected = estado,
                onSelect = { estado = it },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (puedeGuardar) {
                        onSubmit(
                            idRol,
                            nombreUsuario.trim(),
                            nombreMostrar.trim(),
                            correo.trim(),
                            estado,
                        )
                    }
                },
                enabled = puedeGuardar,
            ) {
                Text(submitLabel)
            }
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}

@Composable
private fun RolDropdown(
    roles: List<Rol>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = roles.firstOrNull { it.idRol == selectedId }?.nombre ?: "Rol"

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selectedName, maxLines = 1)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            roles.forEach { rol ->
                DropdownMenuItem(
                    text = { Text(rol.nombre) },
                    onClick = {
                        onSelect(rol.idRol)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EstadoDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selected, maxLines = 1)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            estadosUsuario.forEach { estado ->
                DropdownMenuItem(
                    text = { Text(estado) },
                    onClick = {
                        onSelect(estado)
                        expanded = false
                    },
                )
            }
        }
    }
}
