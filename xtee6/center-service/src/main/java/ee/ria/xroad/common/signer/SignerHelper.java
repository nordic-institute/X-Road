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
package ee.ria.xroad.common.signer;

import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;

import static ee.ria.xroad.common.util.CryptoUtils.*;

/**
 * Contains utility methods for interacting with the signer.
 */
@Slf4j
public final class SignerHelper {

    private SignerHelper() {
    }

    /**
     * @return a base64 encoded signature.
     * @param keyId signing key ID
     * @param signatureAlgorithmId ID of the signature algorithm to use
     * @param data the data to sign
     * @throws Exception in case of any errors
     */
    public static String sign(String keyId, String signatureAlgorithmId,
            String data) throws Exception {
        log.trace("sign({}, {})", keyId, signatureAlgorithmId);
        String digestAlgorithmId = getDigestAlgorithmId(signatureAlgorithmId);

        byte[] tbsData = data.getBytes(StandardCharsets.UTF_8);
        byte[] digest = calculateDigest(digestAlgorithmId, tbsData);

        SignResponse response =
                SignerClient.execute(
                        new Sign(keyId, digestAlgorithmId, digest));

        return encodeBase64(response.getSignature());
    }

    /**
     * @return a base64 encoded signature.
     * @param keyId signing key ID
     * @param signatureAlgorithmId ID of the signature algorithm to use
     * @param data the data to sign
     * @throws Exception in case of any errors
     */
    public static String sign(String keyId, String signatureAlgorithmId,
            byte[] data) throws Exception {
        log.trace("sign({}, {})", keyId, signatureAlgorithmId);
        String digestAlgorithmId = getDigestAlgorithmId(signatureAlgorithmId);

        byte[] digest = calculateDigest(digestAlgorithmId, data);

        SignResponse response =
                SignerClient.execute(
                        new Sign(keyId, digestAlgorithmId, digest));

        return encodeBase64(response.getSignature());
    }
}
