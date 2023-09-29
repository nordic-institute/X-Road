/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * The simplest case -- normal message and normal response.
 * Result: client receives message.
 */
public class XmlEncodedMessage extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public XmlEncodedMessage() {
        requestFileName = "xmlencoded.query";
        responseFile = "xmlencoded.answer";
    }

    @Override
    protected void onServiceReceivedRequest(Message receivedRequest)
            throws Exception {
        validateXmlEncoded(receivedRequest);
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) throws Exception {
        validateXmlEncoded(receivedResponse);
    }

    private void validateXmlEncoded(Message message) throws Exception {
        SoapMessageImpl soap = (SoapMessageImpl) message.getSoap();
        String expected = "<\"EE37702211234\" & 'xml encoded'>";
        if (!soap.getUserId().equals(expected)) {
            throw new Exception("XML encoded data is incorrect: " + soap.getUserId() + " should be " + expected);
        }
    }
}
