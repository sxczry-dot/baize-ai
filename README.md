# deepseek-agent-android

独立原生安卓 AI Agent 客户端（Kotlin + Jetpack Compose），直连 DeepSeek API。

## 功能
- 流式对话（SSE），消息气泡 + 打字效果
- 模型切换：deepseek-v4-pro / deepseek-v4-flash
- Agent 工具：list_directory / read_file / write_file / execute_shell（工作区限定在应用私有 files/workspace）
- API Key 通过 AndroidX Security EncryptedSharedPreferences（Keystore AES-GCM）安全存储
- MVVM + Hilt；Room 会话持久化（DAO/实体见 domain/model 与 data/local）

## 快速开始
1. Android Studio (Jellyfish+, SDK 35) 打开本项目 → Gradle 同步。
2. 连 arm64 真机（API 26+）→ Run。
3. 首次打开 → 设置 → 填 DeepSeek API Key（https://platform.deepseek.com 申请）→ 保存 → 返回聊天。

## 模块结构
- ui/ : ChatScreen / SettingsScreen / ToolCallCard（Compose）
- viewmodel/ : ChatViewModel（Agent 循环）
- data/remote/ : DeepSeekApiService / DeepSeekRepository / SseParser（Retrofit + OkHttp）
- data/local/ : Room DAO/实体（SessionDao / MessageDao）
- domain/model : ChatMessage / ToolCall / Session / ToolDefinition
- domain/tool : ToolRegistry + 4 个内置工具
- util : KeystoreHelper（EncryptedSharedPreferences）

## 安全
- API Key 不硬编码、不进 git；存于 Keystore 加密的 SharedPreferences。
- AndroidManifest 已声明 INTERNET 权限。
- Agent 工具默认关，每次弹确认；工作区限定私有目录。

## 协议
MIT
