package com.miguelaetxio.mimoo.ui.pin

import androidx.lifecycle.ViewModel
import com.miguelaetxio.mimoo.data.access.AccessPinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel de la pantalla de PIN (H07 PARTE 2, PASO 2.7). Sin
 * corrutinas ni Room de por medio -- la comparación es síncrona y
 * rápida (un hash SHA-256), así que no hace falta viewModelScope
 * aquí, a diferencia de SettingsViewModel.
 * ---
 * PIN screen's ViewModel (H07 PART 2, STEP 2.7). No coroutines or
 * Room involved -- the comparison is synchronous and fast (a SHA-256
 * hash), so viewModelScope isn't needed here, unlike
 * SettingsViewModel.
 */
@HiltViewModel
class PinViewModel @Inject constructor(
    private val accessPinManager: AccessPinManager,
) : ViewModel() {

    private val _isUnlocked = MutableStateFlow(accessPinManager.isUnlocked())
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _showError = MutableStateFlow(false)
    val showError: StateFlow<Boolean> = _showError.asStateFlow()

    fun submitPin(input: String) {
        if (accessPinManager.submitPin(input)) {
            _showError.value = false
            _isUnlocked.value = true
        } else {
            _showError.value = true
        }
    }

    fun dismissError() {
        _showError.value = false
    }
}
