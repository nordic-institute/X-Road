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
package org.niis.xroad.signer.shared;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import lombok.experimental.UtilityClass;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.Set;

import static org.niis.xroad.common.core.exception.ErrorCode.UNSUPPORTED_SIGN_ALGORITHM;

@UtilityClass
public class SigningUtil {

    public static final Set<SignAlgorithm> SUPPORTED_ALGORITHMS = Set.of(
            SignAlgorithm.SHA1_WITH_RSA,
            SignAlgorithm.SHA256_WITH_RSA,
            SignAlgorithm.SHA384_WITH_RSA,
            SignAlgorithm.SHA512_WITH_RSA,

            SignAlgorithm.SHA1_WITH_ECDSA,
            SignAlgorithm.SHA256_WITH_ECDSA,
            SignAlgorithm.SHA384_WITH_ECDSA,
            SignAlgorithm.SHA512_WITH_ECDSA
    );

    public static void checkSignatureAlgorithm(SignAlgorithm signatureAlgorithmId, KeyAlgorithm algorithm) {
        if (!SUPPORTED_ALGORITHMS.contains(signatureAlgorithmId)) {
            throw XrdRuntimeException.systemException(UNSUPPORTED_SIGN_ALGORITHM,
                    "Unsupported signature algorithm '%s'".formatted(signatureAlgorithmId.name()));
        }

        if (!algorithm.equals(signatureAlgorithmId.algorithm())) {
            throw XrdRuntimeException.systemException(UNSUPPORTED_SIGN_ALGORITHM,
                    "Unsupported signature algorithm '%s' for key algorithm '%s'".formatted(signatureAlgorithmId.name(), algorithm));
        }
    }
}
