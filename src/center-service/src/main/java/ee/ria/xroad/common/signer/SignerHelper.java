/**
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
package ee.ria.xroad.common.signer;

import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.message.GetSignMechanism;
import ee.ria.xroad.signer.protocol.message.GetSignMechanismResponse;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * Contains utility methods for interacting with the signer.
 */
@Slf4j
public final class SignerHelper {

    private SignerHelper() {
    }

    /**
     * @return a signature algorithm id.
     * @param keyId signing key ID
     * @throws Exception in case of any errors
     */
    public static String getSignAlgorithmId(String keyId, String digestAlgorithmId) throws Exception {
        log.trace("getSignAlgorithmId({}, {})", keyId, digestAlgorithmId);

        GetSignMechanismResponse response = SignerClient.execute(new GetSignMechanism(keyId));

        return CryptoUtils.getSignatureAlgorithmId(digestAlgorithmId, response.getSignMechanismName());
    }

    /**
     * @return a base64 encoded signature.
     * @param keyId signing key ID
     * @param signatureAlgorithmId ID of the signature algorithm to use
     * @param data the data to sign
     * @throws Exception in case of any errors
     */
    public static String sign(String keyId, String signatureAlgorithmId, String data) throws Exception {
        log.trace("sign({}, {}, dataString)", keyId, signatureAlgorithmId);

        return sign(keyId, signatureAlgorithmId, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return a base64 encoded signature.
     * @param keyId signing key ID
     * @param signatureAlgorithmId ID of the signature algorithm to use
     * @param data the data to sign
     * @throws Exception in case of any errors
     */
    public static String sign(String keyId, String signatureAlgorithmId, byte[] data) throws Exception {
        log.trace("sign({}, {}, dataBytes)", keyId, signatureAlgorithmId);

        String digestAlgorithmId = CryptoUtils.getDigestAlgorithmId(signatureAlgorithmId);
        byte[] digest = CryptoUtils.calculateDigest(digestAlgorithmId, data);

        SignResponse response = SignerClient.execute(new Sign(keyId, signatureAlgorithmId, digest));

        return CryptoUtils.encodeBase64(response.getSignature());
    }
}
