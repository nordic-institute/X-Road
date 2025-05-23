/*
 * The MIT License
 *
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
package org.niis.xroad.proxy.core.testsuite.testcases;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;

import jakarta.xml.soap.SOAPHeaderElement;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.proxy.core.test.Message;
import org.niis.xroad.proxy.core.test.MessageTestCase;

import java.io.ByteArrayInputStream;
import java.util.Iterator;

/**
 * Ensure no problems arise if a header value needs to be processed in multiple
 * characters events by the parser.
 */
public class SplitHeaderMessage extends MessageTestCase {
    private static final String EXPECTED_VALUE = buildExpected();

    /**
     * Constructs the test case.
     */
    public SplitHeaderMessage() {
        requestFileName = "split-header.query";
        responseFile = "split-header.answer";
    }

    private static String buildExpected() {
        StringBuilder sb = new StringBuilder();
        sb.append("w\n");
        sb.append("aa");
        sb.append('\n');
        sb.append("ww");
        sb.append('\n');
        return sb.toString();
    }

    @Override
    protected void onServiceReceivedRequest(Message receivedRequest)
            throws Exception {
        validateFieldValue(receivedRequest);
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        validateFieldValue(receivedResponse);
    }

    @SuppressWarnings("unchecked")
    private static void validateFieldValue(Message message) throws Exception {
        SoapMessageImpl msg = (SoapMessageImpl) new SoapParserImpl().parse(
                message.getContentType(),
                new ByteArrayInputStream(
                        ((SoapMessageImpl) message.getSoap()).getBytes()));
        String value = null;
        Iterator<SOAPHeaderElement> h = msg.getSoap().getSOAPHeader()
                .examineAllHeaderElements();
        while (h.hasNext()) {
            SOAPHeaderElement header = h.next();
            if (header.getElementName().getLocalName().equals("issue")) {
                value = header.getValue();
            }
        }
        if (!StringUtils.equals(EXPECTED_VALUE, value)) {
            String diff = StringUtils.difference(EXPECTED_VALUE, value);
            throw new Exception("Unexpected field value (difference starting at"
                    + " index : " + value.indexOf(diff) + ")");
        }
    }
}
