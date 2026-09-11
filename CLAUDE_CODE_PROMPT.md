# 任务：生成一个独立的安卓 AI Agent 应用（DeepSeek Agent for Android）

你是一个资深 Android 工程师。请生成一个**完整、可直接用 Android Studio 打开并编译运行**的安卓项目，
使安卓手机获得一个"电脑路线（Claude Code + cc-switch + DeepSeek API）"的独立原生替代：
原生 Kotlin + Jetpack Compose 聊天客户端，直连 DeepSeek API，具备完整 Agent 能力
（读文件 / 写文件 / 执行命令），并对危险操作弹出权限确认。

## 1. 技术栈与架构
- Kotlin + Jetpack Compose（Material 3）+ MVVM（ViewModel + StateFlow）
- 依赖注入：Hilt（或手动 DI，若用 Hilt 请在 README 说明 ksp 配置）
- 网络：Retrofit 2.11 + OkHttp 4.12 + kotlinx-coroutines
- 序列化：kotlinx.serialization（或 Gson，保持一致即可）
- 本地存储：Room（会话/消息历史）+ DataStore（用户设置）+ Android Keystore（API Key）
- 最低 SDK：26，编译/目标 SDK：35
- 包名建议：`com.deepseek.agent`

## 2. DeepSeek 后端（重要，务必按此对接）
DeepSeek API 兼容 OpenAI 格式，base_url = `https://api.deepseek.com`，
兼容 Anthropic 格式 base_url = `https://api.deepseek.com/anthropic`。
本项目**优先使用 OpenAI 兼容格式**（更简单），模型默认 `deepseek-v4-pro`，
快问快答可选 `deepseek-flash`（V4.1，原生看图）。API Key 由用户在设置页输入，存 Keystore，不硬编码。

OpenAI 兼容端点与请求体（参考实现）：
- 端点：`POST https://api.deepseek.com/chat/completions`
- Authorization: Bearer {API_KEY}
- 请求体示例：
```json
{
  "model": "deepseek-v4-pro",
  "messages": [
    {"role": "system", "content": "You are a helpful assistant with file/tools access."},
    {"role": "user", "content": "列出工作区根目录下的文件"}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "list_directory",
        "description": "列出指定目录下的文件和子目录",
        "parameters": {
          "type": "object",
          "properties": {"path": {"type": "string", "description": "目录路径，相对工作区根目录"}}
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "read_file",
        "description": "读取文本文件内容",
        "parameters": {
          "type": "object",
          "properties": {"path": {"type": "string", "description": "文件路径，相对工作区根目录"}}
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "write_file",
        "description": "写入文本文件（覆盖），危险操作需确认",
        "parameters": {
          "type": "object",
          "properties": {
            "path": {"type": "string", "description": "文件路径"},
            "content": {"type": "string", "description": "文件内容"}
          }
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "execute_shell",
        "description": "在沙箱内执行 shell 命令并返回输出",
        "parameters": {
          "type": "object",
          "properties": {"command": {"type": "string", "description": "要执行的 shell 命令"}}
        }
      }
    }
  ],
  "stream": true,
  "reasoning_effort": "high"
}
```
- 流式响应：SSE，`data: {...}` 行，`[DONE]` 结束。每个 data JSON 中取
  `choices[0].delta.content`（文本增量）与 `choices[0].delta.tool_calls`（工具调用增量）。
  请用 Okio `BufferedSource` 逐行读取，解析 `data:` 前缀，遇 `[DONE]` 终止。
- 非流式（工具调用结果回传时可用普通 JSON 解析）同理，取 `choices[0].message`。

## 3. Agent / 工具调度逻辑（核心）
实现如下循环（在 Repository 或 UseCase 中）：
1. 用户发消息 → 加入 messages → 调用 DeepSeek（stream=true）。
2. 若模型返回 `tool_calls`：
   a. 对每个 tool_call，先**通过 ViewModel 向 UI 发一个"待确认"事件**（写文件/执行 shell 等危险操作必须弹确认对话框，列出工具名+参数，用户确认/拒绝）；
   b. 用户确认后，本地执行对应工具，得到 result 字符串；
   c. 将 `{"role":"tool","tool_call_id":...,"content":result}` 追加到 messages；
   d. 再次调用 DeepSeek（带更新后的 messages），继续循环，直到模型不再调用工具且返回普通文本。
3. 若是普通文本 delta，则流式追加到当前助手消息并刷新 UI。

工具执行说明（沙箱化，工作区根目录建议默认 `Context.getFilesDir()/workspace`，用户可在设置更改）：
- `list_directory(path)`：用 `File(root, path).listFiles()`，返回名称+大小+是否目录的 JSON 列表；越界（路径逃逸出 root）拒绝。
- `read_file(path)`：读取 UTF-8 文本，返回内容；文件不存在/过大（>1MB）报错。
- `write_file(path, content)`：写入 UTF-8；若父目录不存在则创建；路径越界拒绝。
- `execute_shell(command)`：通过 `Runtime.getRuntime().exec(arrayOf("sh","-c",command))`，
  读取 stdout/stderr（限时 15 秒，输出截断 8KB），返回合并结果。**默认禁用**，需在设置显式开启，且每次调用弹确认。

## 4. UI（Jetpack Compose）
- 单 Activity（`MainActivity`），用 Compose Navigation：
  - `ChatScreen`：消息气泡列表（用户右/助手左，Markdown 用 `com.mikepenz:multiplatform-markdown-renderer` 或简单 Text；
    若引入 Markdown 库请在 build.gradle.kts 加依赖并在 README 说明），底部输入框+发送按钮，
    流式输出时显示打字动画。消息含"工具调用"时显示可折叠的 ToolCallCard（工具名+参数+结果+状态）。
  - `SettingsScreen`：API Key 输入（密码框，从 Keystore 读取/保存）、默认模型下拉
    （deepseek-v4-pro / deepseek-flash）、工作区路径显示/重置、危险操作开关
    （"允许写入文件"/"允许执行 Shell"，默认关闭）、清空会话按钮。
  - `SessionsScreen`（侧边抽屉）：会话列表，新建/重命名/删除，点击切换。
- 权限确认弹窗：`AlertDialog` 显示工具名、参数预览（截断 500 字）、确认/拒绝。
- 深色主题跟随系统。

## 5. 安全与权限（务必落实）
- API Key 仅存 Android Keystore（`javax.crypto` + Keystore，封装 `KeystoreHelper`），
  不写 SP 明文，不进 git（`local.properties` 仅放调试默认值且被 .gitignore）。
- `AndroidManifest.xml` 仅声明 `INTERNET` 权限；文件访问用应用私有目录，不申请存储权限。
- 工具执行路径必须限制在 workspace 根目录内（防路径遍历 `../`）。
- 危险工具（write_file/execute_shell）默认关闭 + 每次弹确认。
- OkHttp 日志拦截器 Debug 级别即可，Release 关闭 body 日志，绝不打印 Authorization 头。

## 6. 输出要求
请输出**完整项目目录**（与 Android Gradle 项目结构一致），至少包含：
- `build.gradle.kts`（Project）、`settings.gradle.kts`、`gradle.properties`、`.gitignore`
- `app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`、
  `app/src/main/java/com/deepseek/agent/...`（按下面包结构）
- 包结构：
  - `ui/`：`MainActivity.kt`、`theme/Theme.kt`、`chat/ChatScreen.kt`、
    `settings/SettingsScreen.kt`、`sessions/SessionsScreen.kt`、`components/ToolCallCard.kt`
  - `viewmodel/`：`ChatViewModel.kt`、`SettingsViewModel.kt`、`SessionsViewModel.kt`
  - `data/remote/`：`DeepSeekApiService.kt`（Retrofit 接口）、
    `DeepSeekRepository.kt`、`SseParser.kt`（Okio 逐行解析 SSE）
  - `data/local/`：`AppDatabase.kt`、`SessionDao.kt`、`MessageDao.kt`、
    `entity/SessionEntity.kt`、`entity/MessageEntity.kt`
  - `data/tools/`：`ToolRegistry.kt`、`ListDirectoryTool.kt`、`ReadFileTool.kt`、
    `WriteFileTool.kt`、`ExecuteShellTool.kt`、`ToolConfirmationBus.kt`
  - `domain/`：`model/ChatMessage.kt`、`model/ToolCall.kt`、`model/Session.kt`、
    `usecase/SendMessageUseCase.kt`
  - `di/`：`AppModule.kt`（若用 Hilt）
  - `security/`：`KeystoreHelper.kt`
  - `DeepSeekAgentApp.kt`（Application 类）
- `app/src/main/res/values/strings.xml`、`themes.xml`（如需）
- 根目录 `README.md`：说明如何用 Android Studio 打开、首次启动填 Key、工作区位置、
  模型切换、Agent 工具开关与确认机制、以及"本项目为独立原生客户端，非 DeepSeek 官方应用"的声明。

代码务必可编译运行（Kotlin 2.x / AGP 8.5+ 兼容）。如引入第三方库，统一版本并写进 build.gradle.kts。
优先保证架构清晰与 Agent 循环正确；UI 可简洁但须可用。
