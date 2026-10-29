package cl.kcorp.syncpass.ui.panels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.kcorp.syncpass.data.auth.UserSession
import cl.kcorp.syncpass.data.model.Evento
import cl.kcorp.syncpass.data.model.EventoInput
import cl.kcorp.syncpass.data.model.MetricasEvento
import cl.kcorp.syncpass.data.model.Proyecto
import cl.kcorp.syncpass.data.model.Usuario
import cl.kcorp.syncpass.data.remote.SupabaseConfig
import cl.kcorp.syncpass.data.repository.SyncPassRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocenteUiState(
    val usuario: Usuario? = null,
    val proyectos: List<Proyecto> = emptyList(),
    val selectedProyectoId: Int? = null,
    val eventos: List<Evento> = emptyList(),
    val metricas: Map<Int, MetricasEvento> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
)

class DocenteViewModel : ViewModel() {

    private val _state = MutableStateFlow(DocenteUiState())
    val state: StateFlow<DocenteUiState> = _state.asStateFlow()

    private var loadedEmail: String? = null

    fun load(session: UserSession) {
        if (loadedEmail == session.email) return
        loadedEmail = session.email
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val usuario = SyncPassRepository.getOrCreateUsuario(
                session.email,
                session.name,
                session.role,
            )
            val proyectos = if (usuario != null) {
                SyncPassRepository.getProyectosByCoordinador(usuario.idUsuario)
            } else {
                emptyList()
            }
            val selected = _state.value.selectedProyectoId
                ?.takeIf { id -> proyectos.any { it.idProyecto == id } }
                ?: proyectos.firstOrNull()?.idProyecto

            _state.update {
                it.copy(
                    usuario = usuario,
                    proyectos = proyectos,
                    selectedProyectoId = selected,
                    error = if (usuario == null && SupabaseConfig.isConfigured) {
                        "No se pudo cargar el usuario desde Supabase."
                    } else {
                        null
                    },
                )
            }

            if (selected != null) {
                loadEventosInternal(selected)
            } else {
                _state.update { it.copy(eventos = emptyList(), metricas = emptyMap()) }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun selectProyecto(idProyecto: Int) {
        _state.update {
            it.copy(
                selectedProyectoId = idProyecto,
                eventos = emptyList(),
                metricas = emptyMap(),
            )
        }
        viewModelScope.launch { loadEventosInternal(idProyecto) }
    }

    fun refreshEventos() {
        val idProyecto = _state.value.selectedProyectoId ?: return
        viewModelScope.launch { loadEventosInternal(idProyecto) }
    }

    fun createProyecto(
        titulo: String,
        descripcion: String?,
        fechaInicio: String,
        fechaTermino: String,
    ) {
        val usuario = _state.value.usuario ?: return
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            val proyecto = SyncPassRepository.createProyecto(
                idCoordinador = usuario.idUsuario,
                titulo = titulo,
                descripcion = descripcion,
                fechaInicio = fechaInicio,
                fechaTermino = fechaTermino,
            )
            if (proyecto != null) {
                _state.update {
                    it.copy(
                        proyectos = listOf(proyecto) + it.proyectos,
                        selectedProyectoId = proyecto.idProyecto,
                    )
                }
                loadEventosInternal(proyecto.idProyecto)
            } else {
                _state.update {
                    it.copy(error = "No se pudo crear el proyecto. Revisa la conexión o las políticas de Supabase.")
                }
            }
        }
    }

    fun updateProyecto(
        proyecto: Proyecto,
        titulo: String,
        descripcion: String?,
        fechaInicio: String,
        fechaTermino: String,
        estado: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            val updated = SyncPassRepository.updateProyecto(
                idProyecto = proyecto.idProyecto,
                titulo = titulo,
                descripcion = descripcion,
                fechaInicio = fechaInicio,
                fechaTermino = fechaTermino,
                estado = estado,
            )
            if (updated != null) {
                _state.update {
                    it.copy(
                        proyectos = it.proyectos.map { p ->
                            if (p.idProyecto == updated.idProyecto) updated else p
                        },
                    )
                }
            } else {
                _state.update { it.copy(error = "No se pudo actualizar el proyecto.") }
            }
        }
    }

    fun deleteProyecto(proyecto: Proyecto) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            val ok = SyncPassRepository.deleteProyecto(proyecto.idProyecto)
            if (ok) {
                val remaining = _state.value.proyectos.filter { it.idProyecto != proyecto.idProyecto }
                val selected = if (_state.value.selectedProyectoId == proyecto.idProyecto) {
                    remaining.firstOrNull()?.idProyecto
                } else {
                    _state.value.selectedProyectoId
                }
                _state.update {
                    it.copy(
                        proyectos = remaining,
                        selectedProyectoId = selected,
                        eventos = if (selected == null) emptyList() else it.eventos,
                        metricas = if (selected == null) emptyMap() else it.metricas,
                    )
                }
                if (selected != null) {
                    loadEventosInternal(selected)
                }
            } else {
                _state.update { it.copy(error = "No se pudo eliminar el proyecto.") }
            }
        }
    }

    fun createEvento(idProyecto: Int, input: EventoInput) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            val evento = SyncPassRepository.createEvento(
                idProyecto = idProyecto,
                nombre = input.nombre,
                descripcion = input.descripcion,
                fechaInicio = input.fechaInicio,
                fechaTermino = input.fechaTermino,
                cuposMaximos = input.cuposMaximos,
                recinto = input.recinto,
                direccion = input.direccion,
                latitud = input.latitud,
                longitud = input.longitud,
                radioValidacionMetros = input.radioValidacionMetros,
                estado = input.estado,
            )
            if (evento != null) {
                loadEventosInternal(idProyecto)
            } else {
                _state.update { it.copy(error = "No se pudo crear el evento.") }
            }
        }
    }

    fun updateEvento(evento: Evento, input: EventoInput) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            val updated = SyncPassRepository.updateEvento(
                idEvento = evento.idEvento,
                nombre = input.nombre,
                descripcion = input.descripcion,
                fechaInicio = input.fechaInicio,
                fechaTermino = input.fechaTermino,
                cuposMaximos = input.cuposMaximos,
                recinto = input.recinto,
                direccion = input.direccion,
                latitud = input.latitud,
                longitud = input.longitud,
                radioValidacionMetros = input.radioValidacionMetros,
                estado = input.estado,
            )
            if (updated != null) {
                _state.update {
                    it.copy(
                        eventos = it.eventos.map { e ->
                            if (e.idEvento == updated.idEvento) updated else e
                        },
                    )
                }
            } else {
                _state.update { it.copy(error = "No se pudo actualizar el evento.") }
            }
        }
    }

    fun deleteEvento(evento: Evento) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            val ok = SyncPassRepository.deleteEvento(evento.idEvento)
            if (ok) {
                _state.update {
                    it.copy(
                        eventos = it.eventos.filter { e -> e.idEvento != evento.idEvento },
                        metricas = it.metricas - evento.idEvento,
                    )
                }
            } else {
                _state.update { it.copy(error = "No se pudo eliminar el evento.") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun loadEventosInternal(idProyecto: Int) {
        val eventos = SyncPassRepository.getEventosByProyecto(idProyecto)
        val metricas = eventos.associate { evento ->
            evento.idEvento to SyncPassRepository.getMetricasEvento(evento.idEvento)
        }
        _state.update { it.copy(eventos = eventos, metricas = metricas) }
    }
}
