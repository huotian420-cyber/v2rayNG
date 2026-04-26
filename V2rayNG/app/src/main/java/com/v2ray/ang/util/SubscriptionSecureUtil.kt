package com.v2ray.ang.util

import com.v2ray.ang.AppConfig
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SubscriptionSecureUtil {
    const val SECURE_SUBSCRIPTION_KEY_FRAGMENT = "xray-sub-key"

    private const val SECURE_SUBSCRIPTION_VERSION = "xray-subscription-sealed-v1"
    private const val SECURE_SUBSCRIPTION_ALGORITHM = "aes-256-gcm"

    private data class SecureSubscriptionEnvelope(
        val version: String? = null,
        val algorithm: String? = null,
        val nonce: String? = null,
        val ciphertext: String? = null
    )

    fun resolveDownloadedContent(subscriptionUrl: String, responseContent: String?): String {
        val body = responseContent.orEmpty().trim()
        if (body.isEmpty()) {
            return ""
        }

        val key = resolveFragmentKey(subscriptionUrl) ?: return body
        val envelope = tryParseEnvelope(body) ?: return body
        if (envelope.version != SECURE_SUBSCRIPTION_VERSION || envelope.algorithm != SECURE_SUBSCRIPTION_ALGORITHM) {
            return body
        }

        val nonceValue = envelope.nonce ?: throw IllegalArgumentException("Secure subscription payload is incomplete")
        val cipherValue = envelope.ciphertext ?: throw IllegalArgumentException("Secure subscription payload is incomplete")
        val nonce = decodeBase64Url(nonceValue)
        val cipherPayload = decodeBase64Url(cipherValue)
        if (cipherPayload.size <= 16) {
            throw IllegalArgumentException("Secure subscription payload is invalid")
        }

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(SECURE_SUBSCRIPTION_VERSION.toByteArray(Charsets.UTF_8))
            cipher.doFinal(cipherPayload).toString(Charsets.UTF_8).trim()
        } catch (e: Exception) {
            logError("Failed to decrypt secure subscription payload", e)
            throw IllegalArgumentException("Failed to decrypt secure subscription payload", e)
        }
    }

    fun hasSecureFragmentKey(subscriptionUrl: String): Boolean {
        return resolveFragmentKey(subscriptionUrl) != null
    }

    private fun resolveFragmentKey(subscriptionUrl: String): ByteArray? {
        return try {
            val fragment = URI(Utils.fixIllegalUrl(subscriptionUrl)).rawFragment?.trim().orEmpty()
            if (fragment.isEmpty()) {
                return null
            }

            fragment.split("&")
                .mapNotNull { part ->
                    if (part.isBlank()) return@mapNotNull null
                    val pair = part.split("=", limit = 2)
                    val name = URLDecoder.decode(pair[0], Charsets.UTF_8.toString())
                    if (name != SECURE_SUBSCRIPTION_KEY_FRAGMENT) return@mapNotNull null
                    val value = if (pair.size > 1) URLDecoder.decode(pair[1], Charsets.UTF_8.toString()) else ""
                    if (value.isBlank()) null else decodeBase64Url(value)
                }
                .firstOrNull()
        } catch (e: Exception) {
            logError("Failed to resolve secure subscription key", e)
            null
        }
    }

    private fun tryParseEnvelope(body: String): SecureSubscriptionEnvelope? {
        if (!body.trimStart().startsWith("{")) {
            return null
        }

        return try {
            JsonUtil.fromJson(body, SecureSubscriptionEnvelope::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeBase64Url(value: String): ByteArray {
        val normalized = value.trim()
            .replace('-', '+')
            .replace('_', '/')
        val padded = when (normalized.length % 4) {
            0 -> normalized
            else -> normalized.padEnd(normalized.length + 4 - (normalized.length % 4), '=')
        }
        return Base64.getDecoder().decode(padded)
    }

    private fun logError(message: String, error: Exception) {
        try {
            LogUtil.e(AppConfig.TAG, message, error)
        } catch (_: Exception) {
        }
    }
}
