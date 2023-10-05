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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.CodedException;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.http.HttpField;

import java.util.HashMap;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_TRANSFER_ENCODING;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGORITHM_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_SIG_ALGO_ID;

final class ConfigurationSignature extends AbstractConfigurationPart {

    private static final String HEADER_VERIFICATION_CERT_HASH =
            "verification-certificate-hash";

    private final VerificationCertHash verificationCertHash;

    private ConfigurationSignature(Map<String, String> parameters,
            VerificationCertHash verificationCertHash) {
        super(parameters);

        this.verificationCertHash = verificationCertHash;
    }

    @Override
    public String getContentTransferEncoding() {
        return parameters.get(HEADER_CONTENT_TRANSFER_ENCODING);
    }

    String getSignatureAlgorithmId() {
        return parameters.get(HEADER_SIG_ALGO_ID);
    }

    String getVerificationCertHash() {
        return verificationCertHash.getHash();
    }

    String getVerificationCertHashAlgoId() {
        return verificationCertHash.getAlgoId();
    }

    static ConfigurationSignature of(Map<String, String> headers) {
        if (headers == null) {
            throw new IllegalArgumentException("headers must not be null");
        }

        verifyFieldExists(headers, HEADER_CONTENT_TYPE,
                "application/octet-stream");
        verifyFieldExists(headers, HEADER_CONTENT_TRANSFER_ENCODING, "base64");
        verifyFieldExists(headers, HEADER_SIG_ALGO_ID);
        verifyFieldExists(headers, HEADER_VERIFICATION_CERT_HASH);

        return new ConfigurationSignature(headers,
                getCertVerificationHash(
                        headers.get(HEADER_VERIFICATION_CERT_HASH)));
    }

    private static VerificationCertHash getCertVerificationHash(String value) {
        Map<String, String> p = new HashMap<>();

        String hash = HttpField.valueParameters(value, p);
        String algoId = p.get(HEADER_HASH_ALGORITHM_ID);

        if (StringUtils.isBlank(algoId)) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Field " + HEADER_VERIFICATION_CERT_HASH
                        + " is missing parameter " + HEADER_HASH_ALGORITHM_ID);
        }

        return new VerificationCertHash(hash, algoId);
    }

    @Data
    private static class VerificationCertHash {
        private final String hash;
        private final String algoId;
    }
}
