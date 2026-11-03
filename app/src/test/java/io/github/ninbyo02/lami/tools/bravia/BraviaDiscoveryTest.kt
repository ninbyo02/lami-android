package io.github.ninbyo02.lami.tools.bravia

import org.junit.Assert.*
import org.junit.Test

class BraviaDiscoveryTest {
    private val description = """<?xml version="1.0" encoding="UTF-8"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0"><device>
        <manufacturer>Sony Corporation</manufacturer><modelName>KJ-55A9F</modelName>
        <friendlyName>テレビ</friendlyName></device></root>"""

    @Test fun identifiesSonyAndPreservesJapaneseNames() {
        assertEquals(DiscoveredBravia("KJ-55A9F", "テレビ", "192.168.1.20"),
            BraviaDiscovery.parseDescription(description.toByteArray(), "192.168.1.20"))
        assertNull(BraviaDiscovery.parseDescription(description.replace("Sony Corporation", "Other")
            .toByteArray(), "192.168.1.20"))
    }

    @Test fun rejectsExternalEntitiesAlternateEncodingsAndOversizedDescriptions() {
        val malicious = """<!DOCTYPE root [<!ENTITY external SYSTEM "file:///etc/hostname">]>""" + description
        listOf(malicious.toByteArray(), malicious.toByteArray(Charsets.UTF_16),
            ByteArray(262145) { 'x'.code.toByte() }, "<broken>".toByteArray()).forEach {
            assertNull(BraviaDiscovery.parseDescription(it, "192.168.1.20"))
        }
    }
}
