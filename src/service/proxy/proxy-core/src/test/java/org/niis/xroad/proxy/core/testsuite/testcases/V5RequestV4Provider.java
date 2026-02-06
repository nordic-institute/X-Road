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
package org.niis.xroad.proxy.core.testsuite.testcases;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.proxy.core.test.AbstractProtocolTranslationTestCase;
import org.niis.xroad.proxy.core.test.Message;

@Slf4j
public class V5RequestV4Provider extends AbstractProtocolTranslationTestCase {

    public V5RequestV4Provider() {
        requestFileName = "getstate-v5.query"; // V5 Request
        responseFile = "getstate.answer";
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();
        // Configure V4 output for Provider IS
        setOutputToProviderV4(true);
    }

    @Override
    protected void onServiceReceivedRequest(Message receivedRequest) throws Exception {
        String xml = getRequestXml(receivedRequest);
        log.info("Provider IS Received Request (Expecting V4):\\n{}", xml);
        
        assertV4TerminologyAndNotV5(xml);
    }
    
    @Override
    protected void validateNormalResponse(Message receivedResponse) throws Exception {
         if (receivedResponse == null) {
            throw new Exception("Response is null");
        }
        
        // If SOAP parsing failed (expected for V5 response), check raw bytes
        if (receivedResponse.getSoap() == null) {
            byte[] rawBytes = receivedResponse.getRawSoapBytes();
            if (rawBytes != null && rawBytes.length > 0) {
                String xml = new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);
                log.warn("SOAP Parser failed. Verifying raw V5 response XML...");
                
                // Expecting V5 output (Response mirrors V5 request)
                if (xml.contains("dataspaceInstance") && xml.contains("participantClass")) {
                     log.info("✅ V5 terminology found in raw response!");
                     return; 
                } else {
                    throw new Exception("V5 terminology NOT found in raw response: " + xml);
                }
            }
            throw new Exception("Response is missing SOAP content AND raw bytes");
        }
    }
}
