package cl.kcorp.syncpass.ui.panels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.kcorp.syncpass.data.auth.UserSession
import cl.kcorp.syncpass.data.model.Rol
import cl.kcorp.syncpass.data.model.UsuarioItem
import cl.kcorp.syncpass.data.repository.SyncPassRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val tab: Int = 0,
    val usuarios: List<UsuarioItem> = emptyList(),
    val roles: List<Rol> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class AdminViewModel : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private var initialized = false

    fun initialize(session: UserSession) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            SyncPassRepository.getOrCreateUsuario(session.email, session.name, session.role)
            loadAll()
        }
    }

    fun selectTab(index: Int) {
        _state.update { it.copy(tab = index) }
    }

    fun refresh() {
        viewModelScope.launch { loadAll() }
    }

    fun createUsuario(
        idRol: Int,
        nombreUsuario: String,
        nombreMostrar: String,
        correo: String,
        estado: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            val rolNombre = _state.value.roles.firstOrNull { it.idRol == idRol }?.nombre
            val ok = SyncPassRepository.createUsuario(
                idRol = idRol,
                nombreUsuario = nombreUsuario,
                nombreMostrar = nombreMostrar,
                correo = correo,
                estado = estado,
                rolNombre = rolNombre,
            )
            if (ok) {
                loadAll()
            } else {
                _state.update { it.copy(error = "No se pudo crear el usuario. Revisa la conexión o las políticas de Supabase.") }
            }
        }
    }

    fun updateUsuario(
        usuario: UsuarioItem,
        idRol: Int,
        nombreUsuario: String,
        nombreMostrar: String,
        correo: String,
        estado: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            val rolNombre = _state.value.roles.firstOrNull { it.idRol == idRol }?.nombre
            val ok = SyncPassRepository.updateUsuario(
                idUsuario = usuario.idUsuario,
                idRol = idRol,
                nombreUsuario = nombreUsuario,
                nombreMostrar = nombreMostrar,
                correo = correo,
                estado = estado,
                rolNombre = rolNombre,
            )
            if (ok) {
                loadAll()
            } else {
                _state.update { it.copy(error = "No se pudo actualizar el usuario.") }
            }
        }
    }

    fun deleteUsuario(usuario: UsuarioItem) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            val ok = SyncPassRepository.deleteUsuario(usuario.idUsuario)
            if (ok) {
                _state.update {
                    it.copy(usuarios = it.usuarios.filter { u -> u.idUsuario != usuario.idUsuario })
                }
            } else {
                _state.update { it.copy(error = "No se pudo eliminar el usuario.") }
            }
        }
    }

    private suspend fun loadAll() {
        _state.update { it.copy(loading = true) }
        val roles = SyncPassRepository.getRoles()
        val usuarios = SyncPassRepository.getUsuarios()
        _state.update { it.copy(usuarios = usuarios, roles = roles, loading = false) }
    }
}
