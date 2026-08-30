package com.deepseek.agent.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API Key 存储：使用 EncryptedSharedPreferences（AndroidX Security）。
 * 底层通过 Android Keystore 的 AES-GCM 主密钥加密 SP 文件，Key 不落盘。
 */
class KeystoreHelper(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** 同步写盘并返回是否成功；失败时 UI 应提示用户重新保存。 */
    fun saveApiKey(provider: String, key: String): Boolean =
        runCatching { prefs.edit().putString("api_key_$provider", key).commit() }.getOrDefault(false)

    fun getApiKey(provider: String): String? {
        // 加密存储文件损坏时不能拖垮整个 App
        val k = runCatching { prefs.getString("api_key_$provider", null) }.getOrNull()
        if (!k.isNullOrBlank()) return k
        // 兼容旧版：DeepSeek 的旧存储键
        if (provider == "DEEPSEEK") {
            val legacy = runCatching { prefs.getString(KEY_API_KEY, null) }.getOrNull()
            if (!legacy.isNullOrBlank()) return legacy
        }
        return null
    }

    fun clearApiKey(provider: String) {
        runCatching { prefs.edit().remove("api_key_$provider").commit() }
    }

    companion object {
        private const val PREFS_NAME = "deepseek_agent_secure"
        private const val KEY_API_KEY = "api_key"
    }
}
