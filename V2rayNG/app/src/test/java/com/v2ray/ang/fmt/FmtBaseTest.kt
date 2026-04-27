package com.v2ray.ang.fmt

import com.google.gson.JsonObject
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.V2rayConfigManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI

class FmtBaseTest {
    private val subject = FmtBase()

    @Test
    fun getQueryParam_preservesValueContainingEquals() {
        val uri = URI("vless://uuid@example.com:443?pbk=YWJjZA%3D%3D&sid=short#demo")

        val query = subject.getQueryParam(uri)

        assertEquals("YWJjZA==", query["pbk"])
        assertEquals("short", query["sid"])
    }

    @Test
    fun getQueryParam_preservesEmptyValue() {
        val uri = URI("vless://uuid@example.com:443?encryption=&security=tls#demo")

        val query = subject.getQueryParam(uri)

        assertEquals("", query["encryption"])
        assertEquals("tls", query["security"])
    }

    @Test
    fun getQueryParam_ignoresMissingKey() {
        val uri = URI("vless://uuid@example.com:443?=broken&security=tls#demo")

        val query = subject.getQueryParam(uri)

        assertNull(query[""])
        assertEquals("tls", query["security"])
    }

    @Test
    fun normalizeEncryption_defaultsBlankToNone() {
        assertEquals("none", VlessFmt.normalizeEncryption(null))
        assertEquals("none", VlessFmt.normalizeEncryption(""))
        assertEquals("none", VlessFmt.normalizeEncryption("   "))
        assertEquals("aes-128-gcm", VlessFmt.normalizeEncryption("aes-128-gcm"))
    }

    @Test
    fun getItemFormQuery_preservesXhttpExtraForTransportSettings() {
        val extraJson = """{"xPaddingBytes":"1000-2000","xmux":{"maxConcurrency":"16-32","cMaxReuseTimes":0,"hMaxRequestTimes":"600-900","hMaxReusableSecs":"1800-3000","hKeepAlivePeriod":0}}"""
        val uri = URI(
            "vless://15a97905-a451-4c93-bd4c-e16885cbc807@example.com:443" +
                "?encryption=mlkem768x25519plus.native.0rtt.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "&security=tls" +
                "&sni=example.com" +
                "&fp=chrome" +
                "&alpn=h2%2Chttp%2F1.1" +
                "&type=xhttp" +
                "&host=example.com" +
                "&path=%2Fej45ditxjo" +
                "&mode=auto" +
                "&extra=%7B%22xPaddingBytes%22%3A%221000-2000%22%2C%22xmux%22%3A%7B%22maxConcurrency%22%3A%2216-32%22%2C%22cMaxReuseTimes%22%3A0%2C%22hMaxRequestTimes%22%3A%22600-900%22%2C%22hMaxReusableSecs%22%3A%221800-3000%22%2C%22hKeepAlivePeriod%22%3A0%7D%7D" +
                "#demo"
        )

        val query = subject.getQueryParam(uri)
        val profile = ProfileItem.create(EConfigType.VLESS)
        profile.method = VlessFmt.normalizeEncryption(query["encryption"])
        subject.getItemFormQuery(profile, query, allowInsecure = false)

        assertEquals("mlkem768x25519plus.native.0rtt.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", profile.method)
        assertEquals("xhttp", profile.network)
        assertEquals("auto", profile.xhttpMode)
        assertEquals(extraJson, profile.xhttpExtra)

        val streamSettings = V2rayConfig.OutboundBean.StreamSettingsBean()
        V2rayConfigManager.populateTransportSettings(streamSettings, profile)

        assertEquals("xhttp", streamSettings.network)
        assertEquals("example.com", streamSettings.xhttpSettings?.host)
        assertEquals("/ej45ditxjo", streamSettings.xhttpSettings?.path)
        assertEquals("auto", streamSettings.xhttpSettings?.mode)

        val extra = streamSettings.xhttpSettings?.extra as? JsonObject
        assertNotNull(extra)
        assertEquals("1000-2000", extra?.get("xPaddingBytes")?.asString)
        assertEquals("16-32", extra?.getAsJsonObject("xmux")?.get("maxConcurrency")?.asString)
        assertEquals(0, extra?.getAsJsonObject("xmux")?.get("cMaxReuseTimes")?.asInt)
        assertEquals("600-900", extra?.getAsJsonObject("xmux")?.get("hMaxRequestTimes")?.asString)
    }
}
