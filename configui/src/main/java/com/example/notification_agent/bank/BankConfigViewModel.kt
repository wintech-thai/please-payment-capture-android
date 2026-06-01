package com.example.notification_agent.bank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State + actions for the Compose bank-config screens. */
class BankConfigViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: BankConfigRepository = BankConfigRepository(app)

    val configs: StateFlow<List<BankConfig>> = repository.configs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pinEnabled: StateFlow<Boolean> = repository.pinEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun save(config: BankConfig, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew) repository.add(config) else repository.update(config)
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    suspend fun verifyPin(pin: String): Boolean = repository.verifyPin(pin)

    fun setPin(pin: String) {
        viewModelScope.launch { repository.setPin(pin) }
    }

    fun clearPin() {
        viewModelScope.launch { repository.clearPin() }
    }
}
