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
package org.niis.xroad.proxy.core.signedmessage;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.MessagePart;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.MessageFileNames;

import org.niis.xroad.proxy.core.conf.SigningCtx;
import org.niis.xroad.proxy.core.signature.SignatureBuilder;

/**
 * Encapsulates message signing functionality. This class does not
 * deal with the signed data itself, it is only interested in hashes
 * of the SOAP message and attachments.
 */
public class Signer {

    private final SignatureBuilder builder = new SignatureBuilder();

    private SignatureData signature;

    /** Adds new part to be signed.
     * @param name name of the file in the BDOC container.
     * @param hashMethod identifier of the algorithm used to calculate the hash
     * @param data the data.
     */
    public void addPart(String name, DigestAlgorithm hashMethod, byte[] data) {
        builder.addPart(new MessagePart(name, hashMethod, data, null));

    }

    /** Adds new part to be signed.
     * @param name name of the file in the BDOC container.
     * @param hashMethod identifier of the algorithm used to calculate the hash
     * @param data the data.
     * @param message the message
     */
    public void addPart(String name, DigestAlgorithm hashMethod, byte[] data, byte[] message) {
        builder.addPart(new MessagePart(name, hashMethod, data, message));

    }

    /**
     * Adds the message part to be signed.
     * @param hashMethod identifier of the algorithm used to calculate the hash
     * @param soap the message to be signed
     */
    public void addMessagePart(DigestAlgorithm hashMethod, SoapMessageImpl soap) {
        builder.addPart(new MessagePart(MessageFileNames.MESSAGE, hashMethod,
                soap.getHash(), soap.getBytes()));
    }

    /**
     * Signs the hashes and creates the signature.
     * @param ctx signing context used for signing
     * @throws Exception in case of any errors
     */
    public void sign(SigningCtx ctx) throws Exception {
        signature = ctx.buildSignature(builder);
    }

    /**
     * @return the signature data
     */
    public SignatureData getSignatureData() {
        return signature;
    }

}
