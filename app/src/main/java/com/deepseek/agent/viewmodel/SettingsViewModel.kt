package com.deepseek.agent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.agent.data.local.SettingsStore
import com.deepseek.agent.security.KeystoreHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    val keystore: KeystoreHelper,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val model: StateFlow<String> = settingsStore.model
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.MODEL_PRO)

    val allowWrite: StateFlow<Boolean> = settingsStore.allowWrite
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val allowShell: StateFlow<Boolean> = settingsStore.allowShell
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val userProfile: StateFlow<String> = settingsStore.userProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun hasApiKey(provider: String): Boolean = keystore.getApiKey(provider) != null

    fun saveApiKey(provider: String, key: String) {
        keystore.saveApiKey(provider, key.trim())
    }

    fun clearApiKey(provider: String) {
        keystore.clearApiKey(provider)
    }

    fun setModel(value: String) {
        viewModelScope.launch { settingsStore.setModel(value) }
    }

    fun setAllowWrite(value: Boolean) {
        viewModelScope.launch { settingsStore.setAllowWrite(value) }
    }

    fun setAllowShell(value: Boolean) {
        viewModelScope.launch { settingsStore.setAllowShell(value) }
    }

    fun setUserProfile(value: String) {
        viewModelScope.launch { settingsStore.setUserProfile(value) }
    }
}
