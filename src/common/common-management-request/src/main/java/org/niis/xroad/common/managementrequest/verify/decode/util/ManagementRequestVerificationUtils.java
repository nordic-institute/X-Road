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
package org.niis.xroad.common.managementrequest.verify.decode.util;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.identifier.SecurityServerId;

import com.google.common.base.CharMatcher;
import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.IDN;
import java.security.Signature;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_CLIENT_IDENTIFIER;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.ErrorCodes.translateException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ManagementRequestVerificationUtils {
    private static final CharMatcher IDENTIFIER_PART_MATCHER = CharMatcher.javaIsoControl()
            .or(CharMatcher.anyOf(":;%/\\\ufeff\u200b"));

    public static boolean verifySignature(X509Certificate cert, byte[] signatureData,
                                          SignAlgorithm signatureAlgorithmId, byte[] dataToVerify) {
        try {
            Signature signature = Signature.getInstance(signatureAlgorithmId.name(), "BC");
            signature.initVerify(cert.getPublicKey());
            signature.update(dataToVerify);

            return signature.verify(signatureData);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    public static void assertAddress(String address) {
        boolean valid;
        try {
            valid = (address != null
                    && (InetAddresses.isInetAddress(address) || InternetDomainName.isValid(IDN.toASCII(address))));
        } catch (IllegalArgumentException e) {
            valid = false;
        }
        if (!valid) throw new CodedException(X_INVALID_REQUEST, "Invalid server address");
    }


    public static void validateServerId(SecurityServerId serverId) {
        if (isValidPart(serverId.getXRoadInstance())
                && isValidPart(serverId.getMemberClass())
                && isValidPart(serverId.getMemberCode())
                && isValidPart(serverId.getServerCode())) {
            return;
        }
        throw new CodedException(X_INVALID_CLIENT_IDENTIFIER, "The management request contains an invalid identifier.");
    }


    @SuppressWarnings("checkstyle:MagicNumber")
    private static boolean isValidPart(String part) {
        return part != null
                && !part.isEmpty()
                && part.length() <= 255
                && !IDENTIFIER_PART_MATCHER.matchesAnyOf(part);
    }
}
