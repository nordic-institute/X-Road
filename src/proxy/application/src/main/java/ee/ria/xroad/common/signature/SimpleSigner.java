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
package ee.ria.xroad.common.signature;

import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.signer.SignerProxy;

import lombok.extern.slf4j.Slf4j;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;

@Slf4j
public class SimpleSigner implements MessageSigner {

    @Override
    public SignatureData sign(String keyId, SignAlgorithm signatureAlgorithm, SigningRequest request) throws Exception {
        log.trace("processing sign request");

        final var ctx = new SignatureCtx(signatureAlgorithm);
        ctx.add(request);

        final byte[] digest = calculateDigest(signatureAlgorithm.digest(), ctx.getDataToBeSigned());
        final byte[] response = SignerProxy.sign(keyId, signatureAlgorithm, digest);

        String signature = ctx.createSignatureXml(response);
        return ctx.createSignatureData(signature, 0);
    }


}
