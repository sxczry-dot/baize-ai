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
    fun saveApiKey(key: String): Boolean =
        runCatching { prefs.edit().putString(KEY_API_KEY, key).commit() }.getOrDefault(false)

    fun getApiKey(): String? {
        // 加密存储文件损坏时不能拖垮整个 App
        val k = runCatching { prefs.getString(KEY_API_KEY, null) }.getOrNull()
        return if (k.isNullOrBlank()) null else k
    }

    fun clearApiKey() {
        runCatching { prefs.edit().remove(KEY_API_KEY).commit() }
    }

    companion object {
        private const val PREFS_NAME = "deepseek_agent_secure"
        private const val KEY_API_KEY = "api_key"
    }
}
