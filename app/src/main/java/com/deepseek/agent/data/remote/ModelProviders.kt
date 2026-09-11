package com.deepseek.agent.data.remote

/** 模型供应商 */
enum class Provider(val displayName: String, val baseUrl: String) {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/"),
    KIMI("Kimi", "https://api.moonshot.cn/v1/"),
    GLM("GLM", "https://open.bigmodel.cn/api/paas/v4/"),
    QWEN("千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/")
}

/** 模型定义 */
data class ModelDef(
    val id: String,
    val provider: Provider,
    val label: String,
    val vision: Boolean = false,
    val tools: Boolean = true
)

object ModelCatalog {

    val all: List<ModelDef> = listOf(
        // DeepSeek
        ModelDef("deepseek-v4-pro", Provider.DEEPSEEK, "专业版 · 最强，稍慢"),
        ModelDef("deepseek-flash", Provider.DEEPSEEK, "极速版 V4.1 · 快，可看图", vision = true),
        // Kimi
        ModelDef("kimi-k3", Provider.KIMI, "旗舰 · 最强，100 万字上下文，原生看图", vision = true),
        // GLM
        ModelDef("GLM-5.3", Provider.GLM, "编程与智能体 · 100 万字上下文"),
        ModelDef("GLM-5.3-Flash", Provider.GLM, "多模态普惠 · 可看图看视频", vision = true),
        ModelDef("GLM-5.2", Provider.GLM, "长程任务 · 稳定执行"),
        // 千问
        ModelDef("qwen3.8-max", Provider.QWEN, "旗舰 · 100 万字上下文"),
        ModelDef("qwen3.7-plus", Provider.QWEN, "中档 · 平衡"),
        ModelDef("qwen3.8-flash", Provider.QWEN, "高速 · 便宜"),
        ModelDef("qwen3-vl-plus", Provider.QWEN, "视觉版 · 看图", vision = true, tools = false)
    )

    fun of(id: String): ModelDef? = all.firstOrNull { it.id == id }
}
