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
package org.niis.xroad.cli;

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Set;

public class DigestCalculator {
    private static final Set<DigestAlgorithm> SUPPORTED_DIGEST_ALGO_IDS = Set.of(
            DigestAlgorithm.SHA256,
            DigestAlgorithm.SHA384,
            DigestAlgorithm.SHA512
    );

    private final DigestAlgorithm digestAlgoId;

    public DigestCalculator(DigestAlgorithm digestAlgoId) {
        this.digestAlgoId = digestAlgoId;

        if (!SUPPORTED_DIGEST_ALGO_IDS.contains(digestAlgoId)) {
            throw new IllegalArgumentException("Digest algorithm id '" + digestAlgoId + "' is not supported, "
                    + "supported ones are:\n" + StringUtils.join(", ", SUPPORTED_DIGEST_ALGO_IDS));
        }

    }

    public String chainDigest(byte[] fileContent, String prevHexDigest) {
        String hexDigest = hexDigest(fileContent);
        String combinedDigests = prevHexDigest + hexDigest;

        return hexDigest(combinedDigests.getBytes(StandardCharsets.UTF_8));
    }

    @SneakyThrows
    private String hexDigest(byte[] fileBytes) {
        return Digests.hexDigest(digestAlgoId, fileBytes);
    }
}
