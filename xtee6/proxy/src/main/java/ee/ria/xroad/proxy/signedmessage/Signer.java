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
package ee.ria.xroad.proxy.signedmessage;

import ee.ria.xroad.common.signature.MessagePart;
import ee.ria.xroad.common.signature.SignatureBuilder;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.proxy.conf.SigningCtx;

/**
 * Encapsulates message signing functionality. This class does not
 * deal with the signed data itself, it is only interested in hashes
 * of the SOAP message and attachments.
 */
public class Signer {

    private SignatureBuilder builder = new SignatureBuilder();

    private SignatureData signature;

    /** Adds new part to be signed.
     * @param name name of the file in the BDOC container.
     * @param hashMethod identifier of the algorithm used to calculate the hash
     * @param data the data.
     */
    public void addPart(String name, String hashMethod, byte[] data) {
        builder.addPart(new MessagePart(name, hashMethod, data));
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
