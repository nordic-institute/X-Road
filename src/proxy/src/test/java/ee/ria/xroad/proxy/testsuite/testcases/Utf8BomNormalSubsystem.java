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

import ee.ria.xroad.common.message.RequestHash;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.util.Arrays;

/**
 * The simplest case -- normal message and normal response. Both messages
 * have the UTF-8 BOM bytes.
 * Result: client receives message.
 */
public class Utf8BomNormalSubsystem extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public Utf8BomNormalSubsystem() {
        requestFileName = "getstate-subsystem.query";
        responseFile = "getstate-subsystem.answer";

        addUtf8BomToRequestFile = true;
        addUtf8BomToResponseFile = true;
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {

        RequestHash requestHashFromResponse = ((SoapMessageImpl)
                receivedResponse.getSoap()).getHeader().getRequestHash();

        byte[] requestHash = CryptoUtils.calculateDigest(
                CryptoUtils.getAlgorithmId(
                        requestHashFromResponse.getAlgorithmId()),
                IOUtils.toByteArray(getRequestInput(
                        addUtf8BomToRequestFile).getRight()));

        if (!Arrays.areEqual(requestHash, CryptoUtils.decodeBase64(
                requestHashFromResponse.getHash()))) {
            throw new RuntimeException(
                    "Request message hash does not match request message");
        }
    }
}
