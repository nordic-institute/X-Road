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
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.proxy.core.test;

import ee.ria.xroad.common.message.TerminologyTranslationConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for protocol translation test cases.
 * Provides common setup, teardown, and assertion logic.
 */
@Slf4j
public abstract class AbstractProtocolTranslationTestCase extends MessageTestCase implements ProtocolTranslationTestCase {

    private boolean originalOutputToProviderV4;

    @Override
    protected void startUp() throws Exception {
        super.startUp();
        // Capture original state
        originalOutputToProviderV4 = TerminologyTranslationConfig.getInstance().isOutputToProviderIsInV4();
    }

    @Override
    protected void closeDown() throws Exception {
        // Restore original state
        TerminologyTranslationConfig.getInstance().setOutputToProviderIsInV4(originalOutputToProviderV4);
        super.closeDown();
    }

    protected void setOutputToProviderV4(boolean enable) {
        TerminologyTranslationConfig.getInstance().setOutputToProviderIsInV4(enable);
    }
    
    /**
     * Extracts XML from the received request message.
     */
    protected String getRequestXml(Message receivedRequest) throws Exception {
        if (receivedRequest.getSoap() != null) {
            return receivedRequest.getSoap().getXml();
        } else if (receivedRequest.getRawSoapBytes() != null) {
            return new String(receivedRequest.getRawSoapBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } else {
            throw new Exception("No SOAP content available");
        }
    }

    protected void assertContains(String xml, String element) throws Exception {
         assertContains(xml, element, "XML should contain element '" + element + "'");
    }

    protected void assertContains(String xml, String element, String message) throws Exception {
        if (!xml.contains(element)) {
            log.error("XML content:\n{}", xml);
            throw new Exception(message + " (element '" + element + "' not found)");
        }
    }
    
    protected void assertNotContains(String xml, String element) throws Exception {
         assertNotContains(xml, element, "XML should NOT contain element '" + element + "'");
    }

    protected void assertNotContains(String xml, String element, String message) throws Exception {
        if (xml.contains(element)) {
            log.error("XML content:\n{}", xml);
            throw new Exception(message + " (element '" + element + "' was found but should not be)");
        }
    }
    
    protected void assertV4TerminologyAndNotV5(String xml) throws Exception {
        assertContains(xml, "xRoadInstance"); // V4
        assertContains(xml, "memberClass");   // V4
        assertContains(xml, "memberCode");    // V4
        
        assertNotContains(xml, "dataspaceInstance"); // V5
        assertNotContains(xml, "participantClass");  // V5
        assertNotContains(xml, "participantCode");   // V5 (assuming mapped from memberCode)
    }


    protected void assertV5TerminologyAndNotV4(String xml) throws Exception {
        assertContains(xml, "dataspaceInstance"); // V5
        assertContains(xml, "participantClass");  // V5
        assertContains(xml, "participantCode");   // V5
        
        assertNotContains(xml, "xRoadInstance"); // V4
        assertNotContains(xml, "memberClass");   // V4
        assertNotContains(xml, "memberCode");    // V4
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) throws Exception {
        if (receivedResponse == null || receivedResponse.getSoap() == null) {
            throw new Exception("Response is null or missing SOAP content");
        }
    }
}
