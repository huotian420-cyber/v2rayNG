package com.v2ray.ang

import com.google.gson.Gson
import com.v2ray.ang.util.SubscriptionSecureUtil
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SubscriptionSecureUtilTest {
    @Test
    fun resolveDownloadedContent_decryptsSecureEnvelope() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val subscriptionUrl =
            "https://panel.example.com/panelx/subscriptions/v2r-secure.json?access_token=test#${SubscriptionSecureUtil.SECURE_SUBSCRIPTION_KEY_FRAGMENT}=${toBase64Url(key)}"
        val plainText = "dmxlc3M6Ly8xMjM0"
        val responseBody = buildEnvelopeJson(key, plainText)

        val resolved = SubscriptionSecureUtil.resolveDownloadedContent(subscriptionUrl, responseBody)

        assertEquals(plainText, resolved)
    }

    @Test
    fun resolveDownloadedContent_decryptsSecureEnvelopeWithQueryKeyFallback() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val subscriptionUrl =
            "https://panel.example.com/panelx/subscriptions/v2r-secure.json?access_token=test&sub_key=${toBase64Url(key)}"
        val plainText = "dmxlc3M6Ly8xMjM0"
        val responseBody = buildEnvelopeJson(key, plainText, nonceField = "iv", ciphertextField = "data")

        val resolved = SubscriptionSecureUtil.resolveDownloadedContent(subscriptionUrl, responseBody)

        assertEquals(plainText, resolved)
    }

    @Test
    fun toDownloadUrl_removesLocalSecureKeyButKeepsAccessToken() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val subscriptionUrl =
            "https://panel.example.com/panelx/subscriptions/v2r-secure.json?access_token=test&sub_key=${toBase64Url(key)}#${SubscriptionSecureUtil.SECURE_SUBSCRIPTION_KEY_FRAGMENT}=${toBase64Url(key)}"

        val downloadUrl = SubscriptionSecureUtil.toDownloadUrl(subscriptionUrl)

        assertEquals(
            "https://panel.example.com/panelx/subscriptions/v2r-secure.json?access_token=test",
            downloadUrl
        )
    }

    @Test
    fun toDownloadUrl_keepsRegularKeyQueryParameter() {
        val subscriptionUrl = "https://panel.example.com/sub?key=provider-token#name"

        val downloadUrl = SubscriptionSecureUtil.toDownloadUrl(subscriptionUrl)

        assertEquals("https://panel.example.com/sub?key=provider-token", downloadUrl)
    }

    @Test
    fun hasSecureFragmentKey_detectsPanelSecureUrl() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val subscriptionUrl =
            "https://panel.example.com/panelx/subscriptions/v2r-secure.json?access_token=test#${SubscriptionSecureUtil.SECURE_SUBSCRIPTION_KEY_FRAGMENT}=${toBase64Url(key)}"

        assertEquals(true, SubscriptionSecureUtil.hasSecureFragmentKey(subscriptionUrl))
    }

    @Test
    fun hasSecureFragmentKey_ignoresRegularKeyQueryParameter() {
        assertEquals(false, SubscriptionSecureUtil.hasSecureFragmentKey("https://panel.example.com/sub?key=provider-token#name"))
    }

    @Test
    fun resolveDownloadedContent_returnsOriginalWhenNoFragmentKey() {
        val responseBody = "{\"version\":\"xray-subscription-sealed-v1\"}"

        val resolved = SubscriptionSecureUtil.resolveDownloadedContent(
            "https://panel.example.com/panelx/subscriptions/v2r.txt",
            responseBody
        )

        assertEquals(responseBody, resolved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun resolveDownloadedContent_throwsWhenKeyDoesNotMatch() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrongKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val subscriptionUrl =
            "https://panel.example.com/panelx/subscriptions/v2r-secure.json?access_token=test#${SubscriptionSecureUtil.SECURE_SUBSCRIPTION_KEY_FRAGMENT}=${toBase64Url(wrongKey)}"
        val responseBody = buildEnvelopeJson(key, "payload")

        SubscriptionSecureUtil.resolveDownloadedContent(subscriptionUrl, responseBody)
    }

    private fun buildEnvelopeJson(
        key: ByteArray,
        plainText: String,
        nonceField: String = "nonce",
        ciphertextField: String = "ciphertext"
    ): String {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("xray-subscription-sealed-v1".toByteArray(Charsets.UTF_8))
        val payload = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return Gson().toJson(
            mapOf(
                "version" to "xray-subscription-sealed-v1",
                "algorithm" to "aes-256-gcm",
                nonceField to toBase64Url(nonce),
                ciphertextField to toBase64Url(payload)
            )
        )
    }

    private fun toBase64Url(value: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    }
}
