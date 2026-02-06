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

/**
 * Protocol Translation Test: Scenario 4 - V4 Client Request -> V5 Provider IS.
 * 
 * Tests that when a V4 client sends a request to a V5 provider,
 * the Server Proxy translates the request to V5 terminology before
 * forwarding to the Provider IS.
 */
@Slf4j
public class V4RequestToV5Provider extends AbstractProtocolTranslationTestCase {

    public V4RequestToV5Provider() {
        requestFileName = "getstate.query"; // Standard V4 request
        responseFile = "getstate.answer";
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();
        // Enable V5 output for Provider IS (Disable V4 enforcement)
        setOutputToProviderV4(false);
    }

    @Override
    protected void onServiceReceivedRequest(Message receivedRequest) throws Exception {
        String xml = getRequestXml(receivedRequest);
        log.info("Provider IS Received Request (Expecting V5):\\n{}", xml);
        
        assertV5TerminologyAndNotV4(xml);
    }
    
    @Override
    protected void validateNormalResponse(Message receivedResponse) throws Exception {
        if (receivedResponse == null || receivedResponse.getSoap() == null) {
            throw new Exception("Response is null or missing SOAP content");
        }
    }
}
