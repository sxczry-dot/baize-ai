package com.deepseek.agent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deepseek.agent.DeepSeekAgentApp
import com.deepseek.agent.ui.chat.ChatScreen
import com.deepseek.agent.ui.settings.SettingsScreen
import com.deepseek.agent.ui.theme.DeepSeekAgentTheme
import com.deepseek.agent.viewmodel.ChatViewModel
import com.deepseek.agent.viewmodel.SessionsViewModel
import com.deepseek.agent.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private lateinit var chatVm: ChatViewModel
    private lateinit var sessionsVm: SessionsViewModel
    private lateinit var settingsVm: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DeepSeekAgentApp
        chatVm = app.createChatViewModel()
        sessionsVm = app.createSessionsViewModel()
        settingsVm = app.createSettingsViewModel()

        // 通知权限（notify 工具用，Android 13+）
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        setContent {
            DeepSeekAgentTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AgentNav(chatVm, sessionsVm, settingsVm)
                }
            }
        }
    }
}

@Composable
private fun AgentNav(
    chatVm: ChatViewModel,
    sessionsVm: SessionsViewModel,
    settingsVm: SettingsViewModel
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                vm = chatVm,
                sessionsVm = sessionsVm,
                onOpenSettings = { nav.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                vm = settingsVm,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
