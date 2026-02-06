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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POC Test: Verifies that header translation via SaxSoapParser is feasible.
 * <p>
 * This test proves Petteri's translation proposal: the SAX parser can translate
 * terminology during parsing AND pre-process input to accept V5 messages.
 * </p>
 */
class TerminologyTranslatingParserTest {

    // Protocol 4.0 message (old terminology)
    private static final String V4_MESSAGE = """
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

    // Protocol 5.0 message (new terminology)
    private static final String V5_MESSAGE = """
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

    @Nested
    @DisplayName("V5 Input → V4 Output (Petteri's main scenario)")
    class V5InputV4Output {

        @Test
        @DisplayName("PROVEN: V5 input message translated to V4 output")
        void shouldAcceptV5InputAndOutputV4() throws Exception {
            // Given - V5 message (new terminology)
            TerminologyTranslatingParser parser = new TerminologyTranslatingParser(OutputTerminology.V4_LEGACY);
            ByteArrayInputStream is = new ByteArrayInputStream(V5_MESSAGE.getBytes(StandardCharsets.UTF_8));

            // When - parse V5 input
            Soap result = parser.parse("text/xml; charset=utf-8", is);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(SoapMessageImpl.class);

            SoapMessageImpl soapMessage = (SoapMessageImpl) result;

            // Internal representation uses V4 getters (translated during input)
            assertThat(soapMessage.getClient().getXRoadInstance()).isEqualTo("EE");
            assertThat(soapMessage.getClient().getMemberClass()).isEqualTo("BUSINESS");
            assertThat(soapMessage.getClient().getMemberCode()).isEqualTo("consumer");

            // Output XML has V4 element names
            String outputXml = soapMessage.getXml();
            assertThat(outputXml).contains("<id:xRoadInstance>");
            assertThat(outputXml).contains("<id:memberClass>");
            assertThat(outputXml).contains("<id:memberCode>");
            assertThat(outputXml).doesNotContain("dataspaceInstance");
            assertThat(outputXml).doesNotContain("participantClass");
            assertThat(outputXml).doesNotContain("participantCode");

            // Verify output translations occurred (V5 -> V4 output normalization)
            // Note: Input translation is now native in SaxSoapParserImpl
            assertThat(parser.getOutputTranslationCount()).isEqualTo(6);

            System.out.println("=== V5 Input → V4 Output POC ===");
            System.out.println("✅ SUCCESS: V5 message accepted and output as V4");
            System.out.println("Output translations: " + parser.getOutputTranslationCount());
            System.out.println("(Input translation now native in SaxSoapParserImpl via V5_TO_V4_QNAME_MAP)");
            System.out.println("\nThis proves Petteri's scenario:");
            System.out.println("- Consumer sends V5 → Provider SS can translate → Provider IS receives V4");
        }
    }

    @Nested
    @DisplayName("V4 Input → V5 Output")
    class V4InputV5Output {

        @Test
        @DisplayName("V4 message can be translated to V5 terminology in output")
        void shouldTranslateV4ToV5InOutput() throws Exception {
            // Given - V4 message that parser can understand
            TerminologyTranslatingParser parser = new TerminologyTranslatingParser(OutputTerminology.V5_NEW);
            ByteArrayInputStream is = new ByteArrayInputStream(V4_MESSAGE.getBytes(StandardCharsets.UTF_8));

            // When
            Soap result = parser.parse("text/xml; charset=utf-8", is);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(SoapMessageImpl.class);

            SoapMessageImpl soapMessage = (SoapMessageImpl) result;

            // Output XML has V5 element names
            String outputXml = soapMessage.getXml();
            assertThat(outputXml).contains("dataspaceInstance");
            assertThat(outputXml).contains("participantClass");
            assertThat(outputXml).contains("participantCode");
            assertThat(outputXml).doesNotContain("<id:xRoadInstance>");
            assertThat(outputXml).doesNotContain("<id:memberClass>");
            assertThat(outputXml).doesNotContain("<id:memberCode>");

            // Verify output translations occurred (6 = 2 instances + 2 classes + 2 codes)
            assertThat(parser.getOutputTranslationCount()).isEqualTo(6);

            System.out.println("=== V4 Input → V5 Output POC ===");
            System.out.println("✅ SUCCESS: Translated " + parser.getOutputTranslationCount() + " elements");
        }
    }

    @Nested
    @DisplayName("V5 Input → V5 Output (passthrough)")
    class V5Passthrough {

        @Test
        @DisplayName("V5 message with V5 output stays in V5 terminology")
        void shouldPassthroughV5() throws Exception {
            // Given - V5 message with V5 output
            TerminologyTranslatingParser parser = new TerminologyTranslatingParser(OutputTerminology.V5_NEW);
            ByteArrayInputStream is = new ByteArrayInputStream(V5_MESSAGE.getBytes(StandardCharsets.UTF_8));

            // When
            Soap result = parser.parse("text/xml; charset=utf-8", is);

            // Then
            SoapMessageImpl soapMessage = (SoapMessageImpl) result;
            String outputXml = soapMessage.getXml();

            // Output should have V5 terminology (input translated to V4, then output back to V5)
            assertThat(outputXml).contains("dataspaceInstance");
            assertThat(outputXml).contains("participantClass");
            assertThat(outputXml).contains("participantCode");

            System.out.println("=== V5 Passthrough POC ===");
            System.out.println("✅ V5 in → V5 out works");
            System.out.println("Input translations: " + parser.getInputTranslationCount());
            System.out.println("Output translations: " + parser.getOutputTranslationCount());
        }
    }

    @Nested
    @DisplayName("Non-translating baseline")
    class NoTranslation {

        @Test
        @DisplayName("V4 message without translation should remain unchanged")
        void v4MessageShouldRemainUnchanged() throws Exception {
            // Given - use standard parser (no translation)
            var parser = new SaxSoapParserImplV2();
            ByteArrayInputStream is = new ByteArrayInputStream(V4_MESSAGE.getBytes(StandardCharsets.UTF_8));

            // When
            Soap result = parser.parse("text/xml; charset=utf-8", is);

            // Then
            assertThat(result).isNotNull();
            SoapMessageImpl soapMessage = (SoapMessageImpl) result;

            // Standard parser uses original XML
            assertThat(soapMessage.getClient().getXRoadInstance()).isEqualTo("EE");
            assertThat(soapMessage.getClient().getMemberClass()).isEqualTo("BUSINESS");
            assertThat(soapMessage.getClient().getMemberCode()).isEqualTo("consumer");

            System.out.println("=== Baseline Test ===");
            System.out.println("✅ V4 parsed correctly by standard parser");
        }
    }
}
