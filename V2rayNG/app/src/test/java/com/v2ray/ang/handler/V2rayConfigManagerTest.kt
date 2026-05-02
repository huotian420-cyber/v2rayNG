package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V2rayConfigManagerTest {
    @Test
    fun remapPresetGeoipRule_usesExtAssetWhenAvailable() {
        assertEquals(
            "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:cn",
            V2rayConfigManager.remapPresetGeoipRule(AppConfig.GEOIP_CN, true)
        )
        assertEquals(
            "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:private",
            V2rayConfigManager.remapPresetGeoipRule(AppConfig.GEOIP_PRIVATE, true)
        )
    }

    @Test
    fun remapPresetGeoipRule_keepsBundledGeoipRulesWhenExtAssetMissing() {
        assertEquals(
            AppConfig.GEOIP_CN,
            V2rayConfigManager.remapPresetGeoipRule(AppConfig.GEOIP_CN, false)
        )
        assertEquals(
            AppConfig.GEOIP_PRIVATE,
            V2rayConfigManager.remapPresetGeoipRule(AppConfig.GEOIP_PRIVATE, false)
        )
    }

    @Test
    fun getHttpPortForSocksPort_usesSeparatePort() {
        assertEquals(10809, SettingsManager.getHttpPortForSocksPort(10808))
    }

    @Test
    fun buildHttpInboundFromSocksInbound_usesHttpProtocolAndClearsSocksSettings() {
        val socksInbound = V2rayConfig.InboundBean(
            tag = "socks",
            port = 10808,
            protocol = "socks",
            listen = AppConfig.LOOPBACK,
            settings = V2rayConfig.InboundBean.InSettingsBean(
                auth = "noauth",
                udp = true,
                userLevel = AppConfig.DEFAULT_LEVEL
            ),
            sniffing = V2rayConfig.InboundBean.SniffingBean(
                enabled = true,
                destOverride = arrayListOf("http", "tls")
            )
        )

        val httpInbound = V2rayConfigManager.buildHttpInboundFromSocksInbound(socksInbound, 10809)

        assertEquals("http", httpInbound?.tag)
        assertEquals("http", httpInbound?.protocol)
        assertEquals(10809, httpInbound?.port)
        assertEquals(AppConfig.LOOPBACK, httpInbound?.listen)
        assertNull(httpInbound?.settings?.auth)
        assertNull(httpInbound?.settings?.udp)
    }
}
