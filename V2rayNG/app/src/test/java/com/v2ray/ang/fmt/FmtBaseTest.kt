package com.v2ray.ang.fmt

import org.junit.Assert.assertEquals
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
}
