package cl.kcorp.syncpass.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.kcorp.syncpass.data.repository.SyncPassRepository
import cl.kcorp.syncpass.data.sync.SyncScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DashboardUiState(
    val pendingSync: Int = 0,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                syncNowInternal()
                delay(30_000)
            }
        }
    }

    fun syncNow() {
        SyncScheduler.syncNow(getApplication())
        viewModelScope.launch { syncNowInternal() }
    }

    private suspend fun syncNowInternal() {
        runCatching { SyncPassRepository.syncPending() }
        val pending = runCatching { SyncPassRepository.pendingCount() }
            .getOrDefault(_state.value.pendingSync)
        _state.update { it.copy(pendingSync = pending) }
    }
}
