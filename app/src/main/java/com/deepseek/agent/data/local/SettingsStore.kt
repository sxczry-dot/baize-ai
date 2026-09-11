package com.deepseek.agent.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "agent_settings")

/** 用户设置（非敏感）：模型选择、危险工具开关。API Key 另存于 KeystoreHelper。 */
class SettingsStore(private val context: Context) {

    companion object {
        val MODEL = stringPreferencesKey("model")
        val ALLOW_WRITE = booleanPreferencesKey("allow_write")
        val ALLOW_SHELL = booleanPreferencesKey("allow_shell")
        val USER_PROFILE = stringPreferencesKey("user_profile")

        const val MODEL_PRO = "deepseek-v4-pro"
        const val MODEL_FLASH = "deepseek-flash"
    }

    val model: Flow<String> = context.dataStore.data.map { prefs ->
        when (val v = prefs[MODEL]) {
            null -> MODEL_PRO
            // 中间版本误存过带 [1m] 后缀的名字，迁移回官方模型名
            "deepseek-v4-pro[1m]" -> MODEL_PRO
            // 旧 flash 与视觉版名字已由官方路由到 V4.1 Flash（deepseek-flash），统一迁移
            "deepseek-v4-flash", "deepseek-v4-flash-vision-exp" -> MODEL_FLASH
            else -> v
        }
    }
    val allowWrite: Flow<Boolean> = context.dataStore.data.map { it[ALLOW_WRITE] ?: false }
    val allowShell: Flow<Boolean> = context.dataStore.data.map { it[ALLOW_SHELL] ?: false }
    val userProfile: Flow<String> = context.dataStore.data.map { it[USER_PROFILE] ?: "" }

    suspend fun setModel(value: String) {
        context.dataStore.edit { it[MODEL] = value }
    }

    suspend fun setAllowWrite(value: Boolean) {
        context.dataStore.edit { it[ALLOW_WRITE] = value }
    }

    suspend fun setAllowShell(value: Boolean) {
        context.dataStore.edit { it[ALLOW_SHELL] = value }
    }

    suspend fun setUserProfile(value: String) {
        context.dataStore.edit { it[USER_PROFILE] = value }
    }
}
