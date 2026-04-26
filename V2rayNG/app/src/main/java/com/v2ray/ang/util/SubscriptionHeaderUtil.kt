package com.v2ray.ang.util

object SubscriptionHeaderUtil {
    fun parse(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()

        val result = linkedMapOf<String, String>()
        raw.replace("\r\n", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val idx = line.indexOf(':')
                if (idx <= 0 || idx >= line.length - 1) return@forEach

                val name = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    result[name] = value
                }
            }

        return result
    }

    fun isValid(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return true

        return raw.replace("\r\n", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .all { line ->
                val idx = line.indexOf(':')
                idx > 0 && idx < line.length - 1
            }
    }
}
