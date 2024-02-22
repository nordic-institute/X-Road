/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.common.managemenetrequest.test;

import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MultiPartOutputStream;

import lombok.RequiredArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.Signature;

import static ee.ria.xroad.common.util.MimeUtils.mpRelatedContentType;

@RequiredArgsConstructor
public abstract class TestBaseManagementRequest {
    protected final byte[] message;

    public TestManagementRequestPayload createPayload() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MultiPartOutputStream multipart = new MultiPartOutputStream(out);

        writeSoap(multipart);
        writeMultipart(multipart);

        multipart.close();

        var contentType = mpRelatedContentType(multipart.getBoundary(), MimeTypes.BINARY);
        return new TestManagementRequestPayload(out.toByteArray(), contentType);
    }

    protected void writeMultipart(MultiPartOutputStream multipart) throws Exception {
        //do nothing by default.
    }

    private void writeSoap(MultiPartOutputStream multipart) throws IOException {
        multipart.startPart(MimeTypes.TEXT_XML_UTF8);
        multipart.write(message);
    }

    protected byte[] createSignature(PrivateKey key, String signAlgoId) throws Exception {
        var sig = Signature.getInstance(signAlgoId);
        sig.initSign(key);
        sig.update(message);
        return sig.sign();
    }

}
