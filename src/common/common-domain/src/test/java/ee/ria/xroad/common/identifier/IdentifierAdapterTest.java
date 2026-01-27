/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 */
package ee.ria.xroad.common.identifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IdentifierAdapter demonstrating separate XSD approach.
 * 
 * <p>This test validates that the adapter pattern correctly bridges
 * v4 (legacy) and v5 (new) terminology through a unified interface.</p>
 */
class IdentifierAdapterTest {

    @Test
    void shouldAdaptLegacyIdentifierToUnifiedNaming() {
        // Given: v4 legacy identifier with old terminology
        var legacy = new ee.ria.xroad.common.identifier.v4.XRoadIdentifierType();
        legacy.setXRoadInstance("EE");
        legacy.setMemberClass("GOV");
        legacy.setMemberCode("12345");
        legacy.setServerCode("SS1");
        legacy.setSubsystemCode("SUBSYS");
        legacy.setObjectType(ee.ria.xroad.common.identifier.v4.XRoadObjectType.MEMBER);

        // When: adapt to unified naming
        var adapter = IdentifierAdapter.fromLegacy(legacy);

        // Then: uses new terminology
        assertEquals("EE", adapter.instance());
        assertEquals("GOV", adapter.participantClass());
        assertEquals("12345", adapter.participantCode());
        assertEquals("SS1", adapter.connectorCode());
        assertEquals("SUBSYS", adapter.subsystemCode());
        assertEquals("MEMBER", adapter.objectType());
    }

    @Test
    void shouldAdaptNewIdentifierToUnifiedNaming() {
        // Given: v5 new identifier with new terminology
        var newId = new ee.ria.xroad.common.identifier.v5.XRoadIdentifierType();
        newId.setDataspaceInstance("EE");
        newId.setParticipantClass("GOV");
        newId.setParticipantCode("12345");
        newId.setConnectorCode("SS1");
        newId.setSubsystemCode("SUBSYS");
        newId.setObjectType(ee.ria.xroad.common.identifier.v5.XRoadObjectType.PARTICIPANT);

        // When: adapt to unified naming
        var adapter = IdentifierAdapter.fromNew(newId);

        // Then: uses new terminology (passthrough)
        assertEquals("EE", adapter.instance());
        assertEquals("GOV", adapter.participantClass());
        assertEquals("12345", adapter.participantCode());
        assertEquals("SS1", adapter.connectorCode());
        assertEquals("SUBSYS", adapter.subsystemCode());
        assertEquals("PARTICIPANT", adapter.objectType());
    }

    @Test
    void shouldConvertAdapterToLegacyOutput() {
        // Given: unified adapter
        var adapter = new IdentifierAdapter(
            "EE", "GOV", "12345", "SS1", 
            "SUBSYS", null, null, null, "MEMBER"
        );

        // When: convert to legacy output
        var legacy = adapter.toLegacy();

        // Then: uses old terminology
        assertEquals("EE", legacy.getXRoadInstance());
        assertEquals("GOV", legacy.getMemberClass());
        assertEquals("12345", legacy.getMemberCode());
        assertEquals("SS1", legacy.getServerCode());
        assertEquals("SUBSYS", legacy.getSubsystemCode());
        assertEquals(ee.ria.xroad.common.identifier.v4.XRoadObjectType.MEMBER, legacy.getObjectType());
    }

    @Test
    void shouldConvertAdapterToNewOutput() {
        // Given: unified adapter
        var adapter = new IdentifierAdapter(
            "EE", "GOV", "12345", "SS1", 
            "SUBSYS", null, null, null, "PARTICIPANT"
        );

        // When: convert to new output
        var newId = adapter.toNew();

        // Then: uses new terminology
        assertEquals("EE", newId.getDataspaceInstance());
        assertEquals("GOV", newId.getParticipantClass());
        assertEquals("12345", newId.getParticipantCode());
        assertEquals("SS1", newId.getConnectorCode());
        assertEquals("SUBSYS", newId.getSubsystemCode());
        assertEquals(ee.ria.xroad.common.identifier.v5.XRoadObjectType.PARTICIPANT, newId.getObjectType());
    }

    @Test
    void shouldRoundTripLegacyThroughAdapter() {
        // Given: legacy identifier
        var original = new ee.ria.xroad.common.identifier.v4.XRoadIdentifierType();
        original.setXRoadInstance("FI");
        original.setMemberClass("COM");
        original.setMemberCode("67890");
        original.setObjectType(ee.ria.xroad.common.identifier.v4.XRoadObjectType.SUBSYSTEM);

        // When: roundtrip through adapter
        var adapter = IdentifierAdapter.fromLegacy(original);
        var roundtrip = adapter.toLegacy();

        // Then: values preserved
        assertEquals(original.getXRoadInstance(), roundtrip.getXRoadInstance());
        assertEquals(original.getMemberClass(), roundtrip.getMemberClass());
        assertEquals(original.getMemberCode(), roundtrip.getMemberCode());
    }

    @Test
    void shouldConvertBetweenProtocolVersions() {
        // Given: legacy identifier
        var legacy = new ee.ria.xroad.common.identifier.v4.XRoadIdentifierType();
        legacy.setXRoadInstance("DE");
        legacy.setMemberClass("PRIV");
        legacy.setMemberCode("company123");
        
        // When: convert legacy -> adapter -> new
        var adapter = IdentifierAdapter.fromLegacy(legacy);
        var newId = adapter.toNew();

        // Then: values translated to new terminology
        assertEquals("DE", newId.getDataspaceInstance());
        assertEquals("PRIV", newId.getParticipantClass());
        assertEquals("company123", newId.getParticipantCode());
    }
}
