/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ee.ria.xroad.common.identifier.v2;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests demonstrating that xs:choice with dual terminology support
 * correctly parses both legacy and new element names.
 */
class XRoadIdentifierDualTerminologyTest {

    private static final String NS_ID = "http://x-road.eu/xsd/identifiers-v2";
    
    private static JAXBContext jaxbContext;
    private static Unmarshaller unmarshaller;

    @BeforeAll
    static void setUp() throws JAXBException {
        jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
        unmarshaller = jaxbContext.createUnmarshaller();
    }

    // ========================================================================
    // Test XML with LEGACY terminology
    // ========================================================================
    
    private static final String LEGACY_IDENTIFIER_XML = """
        <id:identifier xmlns:id="%s" id:objectType="MEMBER">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>12345678</id:memberCode>
        </id:identifier>
        """.formatted(NS_ID);
    
    // ========================================================================
    // Test XML with NEW terminology
    // ========================================================================
    
    private static final String NEW_IDENTIFIER_XML = """
        <id:identifier xmlns:id="%s" id:objectType="PARTICIPANT">
            <id:dataspaceInstance>EE</id:dataspaceInstance>
            <id:participantClass>GOV</id:participantClass>
            <id:participantCode>12345678</id:participantCode>
        </id:identifier>
        """.formatted(NS_ID);

    @Nested
    @DisplayName("Legacy Terminology Parsing")
    class LegacyTerminologyTests {
        
        @Test
        @DisplayName("Should parse xRoadInstance element")
        void shouldParseXRoadInstance() throws JAXBException {
            XRoadIdentifierType id = unmarshal(LEGACY_IDENTIFIER_XML);
            
            // Verify value is accessible via default method
            assertEquals("EE", id.getInstanceValue());
            
            // Verify legacy terminology was detected
            assertFalse(id.usesNewInstanceTerminology());
        }
        
        @Test
        @DisplayName("Should parse memberClass element")
        void shouldParseMemberClass() throws JAXBException {
            XRoadIdentifierType id = unmarshal(LEGACY_IDENTIFIER_XML);
            
            assertEquals("GOV", id.getParticipantClassValue());
            assertFalse(id.usesNewParticipantClassTerminology());
        }
        
        @Test
        @DisplayName("Should parse memberCode element")
        void shouldParseMemberCode() throws JAXBException {
            XRoadIdentifierType id = unmarshal(LEGACY_IDENTIFIER_XML);
            
            assertEquals("12345678", id.getParticipantCodeValue());
            assertFalse(id.usesNewParticipantCodeTerminology());
        }
        
        @Test
        @DisplayName("Should detect legacy terminology")
        void shouldDetectLegacyTerminology() throws JAXBException {
            XRoadIdentifierType id = unmarshal(LEGACY_IDENTIFIER_XML);
            
            assertFalse(id.usesAllNewTerminology());
        }
    }

    @Nested
    @DisplayName("New Terminology Parsing")
    class NewTerminologyTests {
        
        @Test
        @DisplayName("Should parse dataspaceInstance element")
        void shouldParseDataspaceInstance() throws JAXBException {
            XRoadIdentifierType id = unmarshal(NEW_IDENTIFIER_XML);
            
            assertEquals("EE", id.getInstanceValue());
            assertTrue(id.usesNewInstanceTerminology());
        }
        
        @Test
        @DisplayName("Should parse participantClass element")
        void shouldParseParticipantClass() throws JAXBException {
            XRoadIdentifierType id = unmarshal(NEW_IDENTIFIER_XML);
            
            assertEquals("GOV", id.getParticipantClassValue());
            assertTrue(id.usesNewParticipantClassTerminology());
        }
        
        @Test
        @DisplayName("Should parse participantCode element")
        void shouldParseParticipantCode() throws JAXBException {
            XRoadIdentifierType id = unmarshal(NEW_IDENTIFIER_XML);
            
            assertEquals("12345678", id.getParticipantCodeValue());
            assertTrue(id.usesNewParticipantCodeTerminology());
        }
        
        @Test
        @DisplayName("Should detect new terminology")
        void shouldDetectNewTerminology() throws JAXBException {
            XRoadIdentifierType id = unmarshal(NEW_IDENTIFIER_XML);
            
            assertTrue(id.usesAllNewTerminology());
        }
    }

    @Nested
    @DisplayName("Mixed Terminology Parsing")
    class MixedTerminologyTests {
        
        private static final String MIXED_IDENTIFIER_XML = """
            <id:identifier xmlns:id="%s" id:objectType="MEMBER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:participantClass>GOV</id:participantClass>
                <id:memberCode>12345678</id:memberCode>
            </id:identifier>
            """.formatted(NS_ID);
        
        @Test
        @DisplayName("Should parse mixed terminology correctly")
        void shouldParseMixedTerminology() throws JAXBException {
            XRoadIdentifierType id = unmarshal(MIXED_IDENTIFIER_XML);
            
            // All values are accessible
            assertEquals("EE", id.getInstanceValue());
            assertEquals("GOV", id.getParticipantClassValue());
            assertEquals("12345678", id.getParticipantCodeValue());
            
            // Terminology detection works per-field
            assertFalse(id.usesNewInstanceTerminology());        // xRoadInstance
            assertTrue(id.usesNewParticipantClassTerminology()); // participantClass
            assertFalse(id.usesNewParticipantCodeTerminology()); // memberCode
            
            // Not all new terminology
            assertFalse(id.usesAllNewTerminology());
        }
    }

    @Nested
    @DisplayName("Connector/Server Code Parsing")
    class ConnectorCodeTests {
        
        private static final String LEGACY_SERVER_XML = """
            <id:identifier xmlns:id="%s" id:objectType="SERVER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>GOV</id:memberClass>
                <id:memberCode>12345678</id:memberCode>
                <id:serverCode>SS1</id:serverCode>
            </id:identifier>
            """.formatted(NS_ID);
        
        private static final String NEW_CONNECTOR_XML = """
            <id:identifier xmlns:id="%s" id:objectType="CONNECTOR">
                <id:dataspaceInstance>EE</id:dataspaceInstance>
                <id:participantClass>GOV</id:participantClass>
                <id:participantCode>12345678</id:participantCode>
                <id:connectorCode>CONN1</id:connectorCode>
            </id:identifier>
            """.formatted(NS_ID);
        
        @Test
        @DisplayName("Should parse serverCode element")
        void shouldParseServerCode() throws JAXBException {
            XRoadIdentifierType id = unmarshal(LEGACY_SERVER_XML);
            
            assertEquals("SS1", id.getConnectorCodeValue());
            assertFalse(id.usesNewConnectorTerminology());
        }
        
        @Test
        @DisplayName("Should parse connectorCode element")
        void shouldParseConnectorCode() throws JAXBException {
            XRoadIdentifierType id = unmarshal(NEW_CONNECTOR_XML);
            
            assertEquals("CONN1", id.getConnectorCodeValue());
            assertTrue(id.usesNewConnectorTerminology());
        }
    }

    @Nested
    @DisplayName("Interface Implementation")
    class InterfaceImplementationTests {
        
        @Test
        @DisplayName("Generated class should implement XRoadIdentifier interface")
        void shouldImplementInterface() {
            XRoadIdentifierType id = new XRoadIdentifierType();
            
            assertInstanceOf(XRoadIdentifier.class, id);
        }
        
        @Test
        @DisplayName("Can use interface reference for polymorphism")
        void canUseInterfacePolymorphically() throws JAXBException {
            XRoadIdentifier legacyId = unmarshal(LEGACY_IDENTIFIER_XML);
            XRoadIdentifier newId = unmarshal(NEW_IDENTIFIER_XML);
            
            // Both can be accessed via the interface
            assertEquals("EE", legacyId.getInstanceValue());
            assertEquals("EE", newId.getInstanceValue());
            
            // Can distinguish terminology
            assertFalse(legacyId.usesNewInstanceTerminology());
            assertTrue(newId.usesNewInstanceTerminology());
        }
    }

    // ========================================================================
    // Helper methods
    // ========================================================================
    
    private XRoadIdentifierType unmarshal(String xml) throws JAXBException {
        StreamSource source = new StreamSource(new StringReader(xml));
        JAXBElement<XRoadIdentifierType> element = unmarshaller.unmarshal(source, XRoadIdentifierType.class);
        return element.getValue();
    }
}
