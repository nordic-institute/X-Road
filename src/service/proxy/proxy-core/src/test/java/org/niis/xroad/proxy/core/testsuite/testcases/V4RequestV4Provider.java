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
public class V4RequestV4Provider extends AbstractProtocolTranslationTestCase {

    public V4RequestV4Provider() {
        requestFileName = "getstate.query"; // V4 Request
        responseFile = "getstate.answer";
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();
        // Configure V4 output (Matches input, so no translation should happen)
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
         if (receivedResponse == null || receivedResponse.getSoap() == null) {
            throw new Exception("Response is null or missing SOAP content");
        }
    }
}
