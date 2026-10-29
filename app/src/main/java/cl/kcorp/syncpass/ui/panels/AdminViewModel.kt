package cl.kcorp.syncpass.ui.panels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val loading: Boolean = true,
)

class AdminViewModel : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init {
        loadUsuarios()
    }

    fun selectTab(index: Int) {
        _state.update { it.copy(tab = index) }
    }

    fun loadUsuarios() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val usuarios = SyncPassRepository.getUsuarios()
            _state.update { it.copy(usuarios = usuarios, loading = false) }
        }
    }
}
