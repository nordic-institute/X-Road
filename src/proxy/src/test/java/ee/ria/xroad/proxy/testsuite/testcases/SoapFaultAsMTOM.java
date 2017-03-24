/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Request message with MTOM content.
 */
public class SoapFaultAsMTOM extends MessageTestCase {

    private static final String EXPTECTED_RESPONSE_TYPE =  MimeUtils.TEXT_XML_UTF8;

    /**
     * Constructs the test case.
     */
    public SoapFaultAsMTOM() {
        requestContentType = "Multipart/Related; "
                + "start-info=\"application/soap+xml\"; "
                + "type=\"application/xop+xml\"; "
                + "boundary=\"jetty771207119h3h10dty\"";
        requestFileName = "soapfault-mtom.query";

        responseContentType = "Multipart/Related; "
                + "start-info=\"application/soap+xml\"; "
                + "type=\"application/xop+xml\"; "
                + "boundary=\"jetty771207119h3h10dty\";charset=UTF-8";
        responseFile = "soapfault-mtom.answer";
    }

    @Override
    protected void onServiceReceivedRequest(Message receivedRequest) throws Exception {
        super.onServiceReceivedRequest(receivedRequest);
//
//        if (!EXPTECTED_RESPONSE_TYPE.equals(receivedResponse.getContentType())) {
//            throw new RuntimeException(String.format(
//                    "Expected response content type '%s' but got '%s'",
//                    EXPTECTED_RESPONSE_TYPE, receivedResponse.getContentType()));
//        }


    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) throws Exception {

            if (!receivedResponse.getSoap().getXml()
                    .contains("<error>Unable to create payload, try increasing the size</error>")) {
                throw new Exception(
                        "The Soap fault in the multipart message was not returned");
            }



    }
}
