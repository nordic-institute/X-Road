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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-style POC test simulating the Provider Security Server flow.
 * <p>
 * Scenario: Consumer SS sends Protocol 5.0 message, Provider SS translates
 * to V4 for Provider IS, then translates response back to V5 for Consumer.
 * </p>
 */
class TerminologyTranslationIntegrationTest {

    // Consumer sends V5 request
    private static final String CONSUMER_REQUEST_V5 = """
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
                    <xroad:id>request-12345</xroad:id>
                    <xroad:protocolVersion>4.0</xroad:protocolVersion>
                </SOAP-ENV:Header>
                <SOAP-ENV:Body>
                    <ns:getStateRequest xmlns:ns="http://example.com">
                        <ns:query>status</ns:query>
                    </ns:getStateRequest>
                </SOAP-ENV:Body>
            </SOAP-ENV:Envelope>
            """;

    // Provider IS responds with V4 (it only knows V4)
    private static final String PROVIDER_IS_RESPONSE_V4 = """
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
                    <xroad:id>request-12345</xroad:id>
                    <xroad:protocolVersion>4.0</xroad:protocolVersion>
                </SOAP-ENV:Header>
                <SOAP-ENV:Body>
                    <ns:getStateResponse xmlns:ns="http://example.com">
                        <ns:status>OK</ns:status>
                    </ns:getStateResponse>
                </SOAP-ENV:Body>
            </SOAP-ENV:Envelope>
            """;

    @Test
    @DisplayName("Full flow: Consumer V5 → Provider SS → IS V4 → Provider SS → Consumer V5")
    void fullProviderSSTranslationFlow() throws Exception {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("INTEGRATION TEST: Provider SS Translation Flow (Petteri's Proposal)");
        System.out.println("=".repeat(70));

        // STEP 1: Consumer SS sends V5 message to Provider SS
        System.out.println("\n📨 STEP 1: Consumer SS sends Protocol 5.0 request to Provider SS");
        System.out.println("   Elements: dataspaceInstance, participantClass, participantCode");
        
        // STEP 2: Provider SS receives V5, translates to V4 for IS
        System.out.println("\n🔄 STEP 2: Provider SS translates V5 → V4 for Provider IS");
        
        TerminologyTranslatingParser requestParser = new TerminologyTranslatingParser(OutputTerminology.V4_LEGACY);
        ByteArrayInputStream requestIs = new ByteArrayInputStream(CONSUMER_REQUEST_V5.getBytes(StandardCharsets.UTF_8));
        SoapMessageImpl translatedRequest = (SoapMessageImpl) requestParser.parse("text/xml; charset=utf-8", requestIs);
        
        String requestToIS = translatedRequest.getXml();
        assertThat(requestToIS).contains("<id:xRoadInstance>");
        assertThat(requestToIS).contains("<id:memberClass>");
        assertThat(requestToIS).contains("<id:memberCode>");
        assertThat(requestToIS).doesNotContain("dataspaceInstance");
        assertThat(requestToIS).doesNotContain("participantClass");
        
        System.out.println("   ✅ Translated request now uses: xRoadInstance, memberClass, memberCode");
        System.out.println("   Input translations: " + requestParser.getInputTranslationCount());
        
        // STEP 3: Provider IS processes V4 request and returns V4 response
        System.out.println("\n📦 STEP 3: Provider IS (legacy system) processes V4 request, returns V4 response");
        System.out.println("   (Provider IS only knows Protocol 4.0 terminology)");
        
        // STEP 4: Provider SS receives V4 response, translates to V5 for Consumer
        System.out.println("\n🔄 STEP 4: Provider SS translates V4 response → V5 for Consumer SS");
        
        TerminologyTranslatingParser responseParser = new TerminologyTranslatingParser(OutputTerminology.V5_NEW);
        ByteArrayInputStream responseIs = new ByteArrayInputStream(PROVIDER_IS_RESPONSE_V4.getBytes(StandardCharsets.UTF_8));
        SoapMessageImpl translatedResponse = (SoapMessageImpl) responseParser.parse("text/xml; charset=utf-8", responseIs);
        
        String responseToConsumer = translatedResponse.getXml();
        assertThat(responseToConsumer).contains("<id:dataspaceInstance>");
        assertThat(responseToConsumer).contains("<id:participantClass>");
        assertThat(responseToConsumer).contains("<id:participantCode>");
        assertThat(responseToConsumer).doesNotContain("<id:xRoadInstance>");
        assertThat(responseToConsumer).doesNotContain("<id:memberClass>");
        
        System.out.println("   ✅ Translated response now uses: dataspaceInstance, participantClass, participantCode");
        System.out.println("   Output translations: " + responseParser.getOutputTranslationCount());
        
        // STEP 5: Consumer SS receives V5 response
        System.out.println("\n📬 STEP 5: Consumer SS receives Protocol 5.0 response");
        System.out.println("   Consumer sees consistent V5 terminology throughout");
        
        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("✅ POC SUCCESS: Full Provider SS translation flow works!");
        System.out.println("=".repeat(70));
        System.out.println("""
                
                Summary:
                --------
                1. Consumer sends V5 → Provider SS accepts and translates to V4
                2. Provider IS (legacy) receives V4, processes, returns V4
                3. Provider SS translates V4 response → V5 for Consumer
                4. Consumer receives consistent V5 terminology
                
                Key Implementation Points:
                - Input translation: V5 elements → V4 (preprocessing)
                - Output translation: V4 elements → V5 (SAX writing)
                - Protocol version: 5.0 → 4.0 for validation
                - Same approach works for response direction
                
                Pending for Full Integration:
                - Inject TerminologyTranslatingParser into ProxyMessageDecoder
                - Add configuration for per-service output terminology
                - Handle requestHash verification with translated messages
                """);
    }

    @Test
    @DisplayName("Verify request hash changes with terminology translation")
    void requestHashChangesWithTranslation() throws Exception {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("HASH VERIFICATION TEST");
        System.out.println("=".repeat(70));

        // Parse same message content with different output terminology
        TerminologyTranslatingParser v4Parser = new TerminologyTranslatingParser(OutputTerminology.V4_LEGACY);
        TerminologyTranslatingParser v5Parser = new TerminologyTranslatingParser(OutputTerminology.V5_NEW);
        
        // Both parse the same V5 input
        SoapMessageImpl v4Output = (SoapMessageImpl) v4Parser.parse("text/xml; charset=utf-8",
                new ByteArrayInputStream(CONSUMER_REQUEST_V5.getBytes(StandardCharsets.UTF_8)));
        SoapMessageImpl v5Output = (SoapMessageImpl) v5Parser.parse("text/xml; charset=utf-8",
                new ByteArrayInputStream(CONSUMER_REQUEST_V5.getBytes(StandardCharsets.UTF_8)));
        
        byte[] v4Hash = v4Output.getHash();
        byte[] v5Hash = v5Output.getHash();
        
        // Hashes should be DIFFERENT because output bytes differ
        assertThat(v4Hash).isNotEqualTo(v5Hash);
        
        System.out.println("✅ Confirmed: Different output terminology → different hash");
        System.out.println("   V4 output hash length: " + v4Hash.length);
        System.out.println("   V5 output hash length: " + v5Hash.length);
        System.out.println("""
                
                ⚠️ IMPORTANT FOR IMPLEMENTATION:
                   - Hash is computed on OUTPUT XML bytes
                   - Translation changes output → changes hash
                   - Consumer and Provider must agree on terminology
                     OR hash verification must account for translation
                """);
    }
}
