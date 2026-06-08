package com.companion.chat.data.voice

import org.json.JSONArray
import org.json.JSONObject

class CloudAsrResponseParser {

    fun extractText(responseBody: String, fieldPath: String): String {
        val normalizedPath = fieldPath.trim().ifBlank { "text" }
        var current: Any? = JSONObject(responseBody)
        normalizedPath.split(".").forEach { segment ->
            current = when (val value = current) {
                is JSONObject -> value.opt(segment)
                is JSONArray -> segment.toIntOrNull()?.let { index -> value.opt(index) }
                else -> null
            }
        }
        return current?.toString().orEmpty()
    }
}
