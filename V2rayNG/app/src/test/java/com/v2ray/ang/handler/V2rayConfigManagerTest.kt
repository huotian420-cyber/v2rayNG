package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import org.junit.Assert.assertEquals
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
}
