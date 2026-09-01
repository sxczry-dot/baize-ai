# 白泽 Baize AI — 安卓 AI 助手

直连 DeepSeek 的原生安卓 AI 客户端（Kotlin + Jetpack Compose），支持流式对话和智能体工具（文件操作、执行命令、网页访问）。API 密钥存储于系统加密保险库。

## 功能

- 流式对话（SSE），消息气泡 + 打字效果
- 模型切换：deepseek-v4-pro / deepseek-v4-flash
- 智能体工具（工作区限定在应用私有 files/workspace，每次弹确认）：
  - list_directory — 列出目录
  - read_file — 读取文件
  - write_file — 写入文件
  - execute_shell — 执行命令
  - web_search — 网页搜索
  - fetch_url — 抓取网页内容
- API Key 通过 AndroidX Security EncryptedSharedPreferences（Keystore AES-GCM）安全存储
- MVVM + Hilt；Room 会话持久化

## 快速开始

1. Android Studio (Jellyfish+, SDK 35) 打开本项目 → Gradle 同步。
2. 连 arm64 真机（API 26+）→ Run。
3. 首次打开 → 设置 → 填 DeepSeek API Key（https://platform.deepseek.com 申请）→ 保存 → 返回聊天。

## 模块结构

- ui/ : ChatScreen / SettingsScreen / ToolCallCard（Compose）
- viewmodel/ : ChatViewModel（Agent 循环）
- data/remote/ : DeepSeekApiService / DeepSeekRepository / SseParser（Retrofit + OkHttp）
- data/local/ : Room DAO/实体（SessionDao / MessageDao）
- data/tools/ : ToolRegistry + 6 个内置工具
- domain/model : ChatMessage / ToolCall / Session / ToolDefinition
- util : KeystoreHelper（EncryptedSharedPreferences）

## 安全

- API Key 不硬编码、不进 git；存于 Keystore 加密的 SharedPreferences。
- AndroidManifest 已声明 INTERNET 权限。
- 智能体工具默认关，每次弹确认；工作区限定私有目录。

## 协议

MIT
