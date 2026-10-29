package cl.kcorp.syncpass.ui.panels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.kcorp.syncpass.data.auth.UserSession
import cl.kcorp.syncpass.data.model.Encuesta
import cl.kcorp.syncpass.data.model.Evento
import cl.kcorp.syncpass.data.model.Inscripcion
import cl.kcorp.syncpass.data.model.Usuario
import cl.kcorp.syncpass.data.remote.SupabaseConfig
import cl.kcorp.syncpass.data.repository.SyncPassRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BeneficiarioUiState(
    val tab: Int = 0,
    val usuario: Usuario? = null,
    val eventos: List<Evento> = emptyList(),
    val inscripciones: List<Inscripcion> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val notice: String? = null,
    val qrEvento: Int? = null,
    val encuestaEvento: Int? = null,
    val calificacion: Int = 4,
    val comentarios: String = "",
)

class BeneficiarioViewModel : ViewModel() {

    private val _state = MutableStateFlow(BeneficiarioUiState())
    val state: StateFlow<BeneficiarioUiState> = _state.asStateFlow()

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
            val eventos = SyncPassRepository.getEventos()
            val inscripciones = if (usuario != null) {
                SyncPassRepository.getInscripciones(usuario.idUsuario)
            } else {
                emptyList()
            }
            _state.update {
                it.copy(
                    usuario = usuario,
                    eventos = eventos,
                    inscripciones = inscripciones,
                    loading = false,
                    error = if (usuario == null && SupabaseConfig.isConfigured) {
                        "No se pudo cargar tu usuario desde Supabase."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun selectTab(index: Int) {
        _state.update { it.copy(tab = index) }
    }

    fun inscribirse(evento: Evento) {
        val usuario = _state.value.usuario ?: return
        viewModelScope.launch {
            _state.update { it.copy(error = null, notice = null) }
            val inscripcion = SyncPassRepository.inscribirse(usuario.idUsuario, evento.idEvento)
            if (inscripcion != null) {
                _state.update {
                    it.copy(
                        inscripciones = it.inscripciones.filterNot { i ->
                            i.idEvento == evento.idEvento
                        } + inscripcion,
                        qrEvento = evento.idEvento,
                        tab = 2,
                    )
                }
            } else {
                _state.update { it.copy(error = "No se pudo completar la inscripción.") }
            }
        }
    }

    fun desinscribirse(evento: Evento) {
        val usuario = _state.value.usuario ?: return
        viewModelScope.launch {
            _state.update { it.copy(error = null, notice = null) }
            val ok = SyncPassRepository.desinscribirse(usuario.idUsuario, evento.idEvento)
            if (ok) {
                _state.update {
                    it.copy(
                        inscripciones = it.inscripciones.map { inscripcion ->
                            if (inscripcion.idEvento == evento.idEvento) {
                                inscripcion.copy(estado = "CANCELADO")
                            } else {
                                inscripcion
                            }
                        },
                    )
                }
            } else {
                _state.update { it.copy(error = "No se pudo anular la inscripción.") }
            }
        }
    }

    fun verQr(evento: Evento) {
        _state.update { it.copy(qrEvento = evento.idEvento, tab = 2) }
    }

    fun toggleEncuesta(idEvento: Int) {
        _state.update {
            it.copy(
                encuestaEvento = if (it.encuestaEvento == idEvento) null else idEvento,
            )
        }
    }

    fun setCalificacion(valor: Int) {
        _state.update { it.copy(calificacion = valor) }
    }

    fun setComentarios(valor: String) {
        _state.update { it.copy(comentarios = valor) }
    }

    fun enviarEncuesta(evento: Evento) {
        val state = _state.value
        val inscripcion = state.inscripciones.firstOrNull { it.idEvento == evento.idEvento }
            ?: return
        viewModelScope.launch {
            _state.update { it.copy(error = null, notice = null) }
            val resultado = SyncPassRepository.registrarEncuesta(
                idInscripcion = inscripcion.idInscripcion,
                calificacion = state.calificacion,
                comentarios = state.comentarios,
            )
            if (resultado.isSuccess) {
                _state.update {
                    it.copy(
                        inscripciones = it.inscripciones.map { item ->
                            if (item.idEvento == evento.idEvento) {
                                item.copy(
                                    encuesta = Encuesta(
                                        calificacion = state.calificacion,
                                        utilidad = "MUY_UTIL",
                                        comentarios = state.comentarios.ifBlank { null },
                                    ),
                                )
                            } else {
                                item
                            }
                        },
                        encuestaEvento = null,
                        comentarios = "",
                        calificacion = 4,
                        error = null,
                        notice = if (resultado.getOrNull() == true) {
                            "Encuesta enviada correctamente."
                        } else {
                            "Encuesta guardada localmente. Se enviará al recuperar conexión."
                        },
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        notice = null,
                        error = "No se pudo enviar la encuesta.",
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }
}
