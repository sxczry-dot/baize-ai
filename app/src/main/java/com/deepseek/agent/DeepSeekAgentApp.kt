package com.deepseek.agent

import android.app.Application
import androidx.room.Room
import com.deepseek.agent.data.local.AppDatabase
import com.deepseek.agent.data.local.MessageDao
import com.deepseek.agent.data.local.SessionDao
import com.deepseek.agent.data.local.SettingsStore
import com.deepseek.agent.data.remote.DeepSeekRepository
import com.deepseek.agent.data.tools.ToolRegistry
import com.deepseek.agent.security.KeystoreHelper
import com.deepseek.agent.viewmodel.ChatViewModel
import com.deepseek.agent.viewmodel.SessionsViewModel
import com.deepseek.agent.viewmodel.SettingsViewModel

class DeepSeekAgentApp : Application() {

    lateinit var keystore: KeystoreHelper
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var toolRegistry: ToolRegistry
        private set
    lateinit var repository: DeepSeekRepository
        private set
    private lateinit var sessionDao: SessionDao
    private lateinit var messageDao: MessageDao

    override fun onCreate() {
        super.onCreate()
        installCrashRecorder()
        keystore = KeystoreHelper(this)
        settingsStore = SettingsStore(this)
        toolRegistry = ToolRegistry(this)
        repository = DeepSeekRepository(keystore, toolRegistry)

        val db = Room.databaseBuilder(this, AppDatabase::class.java, "agent.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
        sessionDao = db.sessionDao()
        messageDao = db.messageDao()
    }

    /** 黑匣子：崩溃时把堆栈写进文件，设置页可查看，便于排查闪退。 */
    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val log = java.io.File(filesDir, "crash.log")
                val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                    .format(java.util.Date())
                val stack = android.util.Log.getStackTraceString(throwable)
                log.appendText("\n===== $time =====\n$stack\n")
                if (log.length() > 512 * 1024) {
                    log.writeText(log.readText().takeLast(256 * 1024))
                }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun createChatViewModel(): ChatViewModel =
        ChatViewModel(repository, toolRegistry, sessionDao, messageDao, settingsStore)

    fun createSessionsViewModel(): SessionsViewModel = SessionsViewModel(sessionDao)

    fun createSettingsViewModel(): SettingsViewModel =
        SettingsViewModel(keystore, settingsStore)
}
