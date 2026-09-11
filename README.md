# 白泽 Baize AI — 安卓 AI 助手

原生安卓 AI 客户端（Kotlin + Jetpack Compose），直连 DeepSeek、Kimi、GLM、千问四家 API，支持流式对话、文件与图片上传、智能体工具（文件操作、执行命令、网页访问）。API 密钥存储于系统加密保险库。

## 功能

- 流式对话（SSE），消息气泡 + 打字效果
- 四家 AI 供应商、10 个模型可切换：
  - DeepSeek：v4-pro / flash（V4.1，原生看图）
  - Kimi：kimi-k3（100 万字上下文，原生看图）
  - GLM：GLM-5.3 / 5.3-Flash（可看图看视频）/ 5.2
  - 千问：qwen3.8-max / qwen3.7-plus / qwen3.8-flash / qwen3-vl-plus（视觉）
- 智能体工具 18 个（工作区默认限定应用私有目录，危险操作每次弹确认）：
  - 文件：list_directory / read_file / write_file / move_file / copy_file / delete_file / find_files / zip_files / unzip_file
  - 网络：web_search / fetch_url / open_url
  - 手机：get_clipboard / set_clipboard / notify / get_time
  - 计算与命令：eval_expr / execute_shell（需在设置中开启）
- API Key 通过 AndroidX Security EncryptedSharedPreferences（Keystore AES-GCM）安全存储
- MVVM；Room 会话持久化

## 快速开始

1. Android Studio (Jellyfish+, SDK 35) 打开本项目 → Gradle 同步。
2. 连 arm64 真机（API 26+）→ Run。
3. 首次打开 → 设置 → 填写所选 AI 厂商的 API Key（如 DeepSeek 在 <https://platform.deepseek.com> 申请）→ 保存 → 返回聊天。

## 模块结构

- ui/ : ChatScreen / SettingsScreen / ToolCallCard（Compose）
- viewmodel/ : ChatViewModel（Agent 循环）
- data/remote/ : DeepSeekApiService / DeepSeekRepository / SseParser（Retrofit + OkHttp）
- data/local/ : Room DAO/实体（SessionDao / MessageDao）
- data/tools/ : ToolRegistry + 18 个内置工具
- domain/model : ChatMessage / ToolCall / Session / ToolDefinition
- util : KeystoreHelper（EncryptedSharedPreferences）

## 安全

- API Key 不硬编码、不进 git；存于 Keystore 加密的 SharedPreferences。
- AndroidManifest 已声明 INTERNET 权限。
- 危险工具（写入、删除、移动、压缩解压、执行命令）每次弹确认，拒绝或 120 秒超时不会执行；工作区默认限定应用私有目录，手动授权后可访问整个手机存储。

## 协议

MIT
