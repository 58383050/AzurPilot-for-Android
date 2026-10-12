package com.azurpilot.ghio.proot

import org.json.JSONArray
import org.json.JSONObject

/**
 * 将配置导入转换为网关允许的批次，保留 Android 连接参数并跳过未变化的值。
 *
 * 在 IO 线程生成全部批次后再发送，避免格式错误发生在部分配置已写入之后。
 * 同一任务的参数组不拆开，以保留心情恢复等跨字段校验所需的完整输入。
 *
 * Converts a config import into gateway-sized batches, preserving Android connection settings
 * and skipping unchanged values.
 *
 * Builds all batches on an IO thread before sending, so malformed input cannot fail after a
 * partial write. Each task's argument group stays together for validation of related fields,
 * such as emotion recovery settings.
 */
internal object AzurPilotConfigImport {
    /**
     * 与上游 `PatchParams.changes` 的数量上限一致。
     *
     * Matches the upstream `PatchParams.changes` limit.
     */
    private const val MAX_CHANGES = 200

    /**
     * 返回按参数组划分的批次；兼容配置没有变化时返回空列表。
     *
     * Returns batches partitioned by argument group, or an empty list for an unchanged config.
     *
     * @throws IllegalArgumentException 没有兼容参数或单组超过协议上限。
     *   / No compatible fields exist, or one group exceeds the protocol limit.
     */
    fun batches(
        content: String,
        schema: AzurPilotSchema,
        current: ApConfigValues,
    ): List<JSONArray> {
        val root = JSONObject(content.trimStart().removePrefix("\uFEFF"))
        val values = root.optJSONObject("values") ?: root
        val batches = mutableListOf<JSONArray>()
        var batch = JSONArray()
        var compatible = false
        values.keys().forEach { task ->
            val groups = values.optJSONObject(task) ?: return@forEach
            groups.keys().forEach groupLoop@{ group ->
                if (task == "Alas" && group == "Emulator") return@groupLoop
                val fields = groups.optJSONObject(group) ?: return@groupLoop
                val groupChanges = mutableListOf<JSONObject>()
                fields.keys().forEach fieldLoop@{ argument ->
                    val field = schema.field(task, group, argument) ?: return@fieldLoop
                    if (field.readOnly || field.hidden) return@fieldLoop
                    compatible = true
                    val value = fields.opt(argument)
                    if (value.asApValue().sameValueAs(current[task]?.get(group)?.get(argument))) {
                        return@fieldLoop
                    }
                    groupChanges += JSONObject()
                        .put("path", "$task.$group.$argument")
                        .put("value", value)
                }
                require(groupChanges.size <= MAX_CHANGES) {
                    "Configuration group exceeds the import limit: $task.$group"
                }
                if (batch.length() + groupChanges.size > MAX_CHANGES) {
                    batches += batch
                    batch = JSONArray()
                }
                groupChanges.forEach { batch.put(it) }
            }
        }
        require(compatible) { "No compatible settings found" }
        if (batch.length() > 0) batches += batch
        return batches
    }
}
