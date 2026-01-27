/*
 * The MIT License
 *
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
package ee.ria.xroad.common.message;

import ee.ria.xroad.common.message.TerminologyTranslatingParser.OutputTerminology;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for terminology translation with feature toggle support.
 * <p>
 * Validates that:
 * 1. protocolVersion in request determines expected response schema
 * 2. Output to Provider IS is togglable (V4 or passthrough)
 * 3. Response matches original request terminology
 * </p>
 */
class TerminologyTranslationToggleTest {

    // Request with V4 terminology and protocol 4.0
    private static final String V4_REQUEST = """
            <?xml version="1.0" encoding="utf-8"?>
            <SOAP-ENV:Envelope
                    xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:xroad="http://x-road.eu/xsd/xroad.xsd"
                    xmlns:id="http://x-road.eu/xsd/identifiers">
                <SOAP-ENV:Header>
                    <xroad:client id:objectType="MEMBER">
                        <id:xRoadInstance>EE</id:xRoadInstance>
                        <id:memberClass>BUSINESS</id:memberClass>
                        <id:memberCode>consumer</id:memberCode>
                    </xroad:client>
                    <xroad:service id:objectType="SERVICE">
                        <id:xRoadInstance>EE</id:xRoadInstance>
                        <id:memberClass>BUSINESS</id:memberClass>
                        <id:memberCode>producer</id:memberCode>
                        <id:serviceCode>getState</id:serviceCode>
                    </xroad:service>
                    <xroad:userId>EE:PIN:abc4567</xroad:userId>
                    <xroad:id>test-query-id</xroad:id>
                    <xroad:protocolVersion>4.0</xroad:protocolVersion>
                </SOAP-ENV:Header>
                <SOAP-ENV:Body>
                    <xroad:getState>test</xroad:getState>
                </SOAP-ENV:Body>
            </SOAP-ENV:Envelope>
            """;

    // Request with V5 terminology and protocol 4.0 (V5 element names but still protocol 4.0)
    private static final String V5_ELEMENTS_REQUEST = """
            <?xml version="1.0" encoding="utf-8"?>
            <SOAP-ENV:Envelope
                    xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:xroad="http://x-road.eu/xsd/xroad.xsd"
                    xmlns:id="http://x-road.eu/xsd/identifiers">
                <SOAP-ENV:Header>
                    <xroad:client id:objectType="MEMBER">
                        <id:dataspaceInstance>EE</id:dataspaceInstance>
                        <id:participantClass>BUSINESS</id:participantClass>
                        <id:participantCode>consumer</id:participantCode>
                    </xroad:client>
                    <xroad:service id:objectType="SERVICE">
                        <id:dataspaceInstance>EE</id:dataspaceInstance>
                        <id:participantClass>BUSINESS</id:participantClass>
                        <id:participantCode>producer</id:participantCode>
                        <id:serviceCode>getState</id:serviceCode>
                    </xroad:service>
                    <xroad:userId>EE:PIN:abc4567</xroad:userId>
                    <xroad:id>test-query-id</xroad:id>
                    <xroad:protocolVersion>4.0</xroad:protocolVersion>
                </SOAP-ENV:Header>
                <SOAP-ENV:Body>
                    <xroad:getState>test</xroad:getState>
                </SOAP-ENV:Body>
            </SOAP-ENV:Envelope>
            """;

    private TerminologyTranslationConfig config;

    @BeforeEach
    void setUp() {
        config = TerminologyTranslationConfig.getInstance();
        config.reset();
    }

    @AfterEach
    void tearDown() {
        config.reset();
    }

    @Nested
    @DisplayName("Feature Toggle Tests")
    class FeatureToggleTests {

        @Test
        @DisplayName("Default: V5 request → V4 output to IS (toggle enabled)")
        void defaultToggleOutputsV4ToIs() throws Exception {
            // Given - default config (outputToProviderIsInV4 = true)
            assertThat(config.isOutputToProviderIsInV4()).isTrue();
            
            // When - determine output terminology for V5 request going to IS
            OutputTerminology output = config.getOutputTerminology("5.0", true);
            
            // Then - should output V4 to IS
            assertThat(output).isEqualTo(OutputTerminology.V4_LEGACY);
            
            System.out.println("=== Feature Toggle Test: Default IS Output ===");
            System.out.println("✅ V5 request → V4 output to IS (toggle enabled)");
        }

        @Test
        @DisplayName("Toggle off: V5 request → V5 output to IS (passthrough)")
        void toggleOffPassesThrough() {
            // Given - toggle disabled
            config.setOutputToProviderIsInV4(false);
            
            // When - V5 request
            OutputTerminology output = config.getOutputTerminology("5.0", true);
            
            // Then - passthrough V5
            assertThat(output).isEqualTo(OutputTerminology.V5_NEW);
            
            System.out.println("=== Feature Toggle Test: Passthrough ===");
            System.out.println("✅ Toggle off: V5 request → V5 output to IS");
        }

        @Test
        @DisplayName("Response matches request protocol 4.0")
        void responseMatchesV4Request() {
            // Given - V4 request
            OutputTerminology output = config.getOutputTerminology("4.0", false);
            
            // Then - response in V4
            assertThat(output).isEqualTo(OutputTerminology.V4_LEGACY);
            
            System.out.println("=== Response Matching Test ===");
            System.out.println("✅ V4 request → V4 response");
        }

        @Test
        @DisplayName("Response matches request protocol 5.0")
        void responseMatchesV5Request() {
            // Given - V5 request
            OutputTerminology output = config.getOutputTerminology("5.0", false);
            
            // Then - response in V5
            assertThat(output).isEqualTo(OutputTerminology.V5_NEW);
            
            System.out.println("=== Response Matching Test ===");
            System.out.println("✅ V5 request → V5 response");
        }

    }

    @Nested
    @DisplayName("End-to-End Translation with Toggle")
    class E2ETranslationTests {

        @Test
        @DisplayName("V5 elements parsed and output as V4 with toggle")
        void v5ElementsToV4Output() throws Exception {
            // Given - V5 elements request, toggle enabled
            assertThat(config.isOutputToProviderIsInV4()).isTrue();
            
            TerminologyTranslatingParser parser = new TerminologyTranslatingParser(OutputTerminology.V4_LEGACY);
            ByteArrayInputStream is = new ByteArrayInputStream(V5_ELEMENTS_REQUEST.getBytes(StandardCharsets.UTF_8));

            // When - parse
            Soap result = parser.parse("text/xml; charset=utf-8", is);
            SoapMessageImpl soapMessage = (SoapMessageImpl) result;

            // Then - internal representation correct
            assertThat(soapMessage.getClient().getXRoadInstance()).isEqualTo("EE");
            assertThat(soapMessage.getClient().getMemberClass()).isEqualTo("BUSINESS");
            
            // And output XML in V4
            String outputXml = soapMessage.getXml();
            assertThat(outputXml).contains("<id:xRoadInstance>");
            assertThat(outputXml).contains("<id:memberClass>");
            assertThat(outputXml).doesNotContain("dataspaceInstance");
            assertThat(outputXml).doesNotContain("participantClass");

            System.out.println("=== E2E Test: V5 Elements → V4 Output ===");
            System.out.println("✅ V5 elements successfully translated to V4 output");
            System.out.println("Output translations: " + parser.getOutputTranslationCount());
        }
    }
}
