package cl.kcorp.syncpass.ui.panels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.kcorp.syncpass.data.location.LocationProvider
import cl.kcorp.syncpass.data.model.Evento
import cl.kcorp.syncpass.data.model.Inscrito
import cl.kcorp.syncpass.data.repository.SyncPassRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class VoluntarioView { EVENTOS, INSCRITOS, SCANNER }

data class VoluntarioUiState(
    val view: VoluntarioView = VoluntarioView.EVENTOS,
    val eventos: List<Evento> = emptyList(),
    val selectedEvento: Evento? = null,
    val inscritos: List<Inscrito> = emptyList(),
    val loading: Boolean = true,
    val scanStatus: String? = null,
    val scanResult: String? = null,
)

class VoluntarioViewModel : ViewModel() {

    private val _state = MutableStateFlow(VoluntarioUiState())
    val state: StateFlow<VoluntarioUiState> = _state.asStateFlow()

    private var processing = false

    fun loadEventos() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val eventos = SyncPassRepository.getEventos()
            _state.update { it.copy(eventos = eventos, loading = false) }
        }
    }

    fun openInscritos(evento: Evento) {
        _state.update {
            it.copy(
                view = VoluntarioView.INSCRITOS,
                selectedEvento = evento,
                scanStatus = null,
                scanResult = null,
            )
        }
        loadInscritos(evento.idEvento)
    }

    fun openScanner(evento: Evento) {
        _state.update {
            it.copy(
                view = VoluntarioView.SCANNER,
                selectedEvento = evento,
                scanStatus = null,
                scanResult = null,
            )
        }
        loadInscritos(evento.idEvento)
    }

    fun backToEventos() {
        _state.update {
            it.copy(
                view = VoluntarioView.EVENTOS,
                scanStatus = null,
                scanResult = null,
            )
        }
    }

    fun refreshInscritos() {
        _state.value.selectedEvento?.let { loadInscritos(it.idEvento) }
    }

    fun processCode(code: String, latitud: Double?, longitud: Double?) {
        if (processing) return
        val evento = _state.value.selectedEvento
        _state.update { it.copy(scanResult = code) }

        if (evento == null) {
            _state.update { it.copy(scanStatus = "❌ Selecciona un evento primero") }
            return
        }

        val parts = code.split(":")
        if (parts.size != 4 || parts[0] != "syncpass" || parts[1] != "asistencia") {
            _state.update { it.copy(scanStatus = "❌ QR no válido") }
            return
        }
        val idUsuario = parts[2].toIntOrNull()
        val idEvento = parts[3].toIntOrNull()
        if (idUsuario == null || idEvento == null) {
            _state.update { it.copy(scanStatus = "❌ QR no válido") }
            return
        }
        if (idEvento != evento.idEvento) {
            _state.update { it.copy(scanStatus = "❌ El QR no corresponde a este evento") }
            return
        }

        processing = true
        viewModelScope.launch {
            try {
                _state.update { it.copy(scanStatus = "Procesando...") }
                val lista = _state.value.inscritos.ifEmpty {
                    SyncPassRepository.getInscritosByEvento(idEvento)
                }
                val inscripcion = lista.firstOrNull { it.idUsuario == idUsuario }
                    ?: SyncPassRepository.getInscritosByEvento(idEvento)
                        .firstOrNull { it.idUsuario == idUsuario }

                if (inscripcion == null) {
                    _state.update { it.copy(scanStatus = "❌ No está en la lista de inscritos") }
                    return@launch
                }

                val validacion = validarUbicacion(evento, latitud, longitud)
                if (validacion.error != null) {
                    _state.update { it.copy(scanStatus = "❌ ${validacion.error}") }
                    return@launch
                }

                val resultado = SyncPassRepository.registrarAsistencia(
                    idInscripcion = inscripcion.idInscripcion,
                    latitud = latitud,
                    longitud = longitud,
                )
                if (resultado.isSuccess) {
                    val mensaje = if (resultado.getOrNull() == true) {
                        "✅ Asistencia confirmada"
                    } else {
                        "✅ Asistencia guardada. Se sincronizará al recuperar conexión."
                    }
                    val inscritosActualizados = SyncPassRepository.getInscritosByEvento(idEvento)
                    _state.update {
                        it.copy(
                            scanStatus = mensaje + validacion.sufijo,
                            inscritos = inscritosActualizados,
                        )
                    }
                    delay(1800)
                    _state.update {
                        it.copy(
                            scanStatus = null,
                            scanResult = null,
                            view = VoluntarioView.EVENTOS,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            scanStatus = "❌ ${resultado.exceptionOrNull()?.localizedMessage ?: "Error al registrar"}",
                        )
                    }
                }
            } finally {
                processing = false
            }
        }
    }

    private fun loadInscritos(idEvento: Int) {
        viewModelScope.launch {
            val inscritos = SyncPassRepository.getInscritosByEvento(idEvento)
            _state.update { it.copy(inscritos = inscritos) }
        }
    }

    private fun validarUbicacion(
        evento: Evento,
        latitud: Double?,
        longitud: Double?,
    ): ValidacionUbicacion {
        val eventoLat = evento.latitud
        val eventoLng = evento.longitud
        if (eventoLat == null || eventoLng == null) {
            return ValidacionUbicacion()
        }
        if (latitud == null || longitud == null) {
            return ValidacionUbicacion(sufijo = " (sin validación de ubicación)")
        }
        val radio = evento.radioValidacionMetros.takeIf { it > 0 } ?: 100
        val distancia = LocationProvider.distanceMeters(
            eventoLat,
            eventoLng,
            latitud,
            longitud,
        )
        if (distancia > radio) {
            return ValidacionUbicacion(
                error = "Estás fuera del radio permitido (${distancia.roundToInt()} m de $radio m).",
            )
        }
        return ValidacionUbicacion(sufijo = " (ubicación validada)")
    }

    private data class ValidacionUbicacion(
        val sufijo: String = "",
        val error: String? = null,
    )
}
