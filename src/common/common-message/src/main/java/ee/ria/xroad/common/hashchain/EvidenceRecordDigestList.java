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
package ee.ria.xroad.common.hashchain;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.evidencerecord.common.validation.ByteArrayComparator;
import eu.europa.esig.dss.model.DSSMessageDigest;
import eu.europa.esig.dss.spi.DSSMessageDigestCalculator;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmURI;

@NoArgsConstructor
final class EvidenceRecordDigestList {

    /**
     * Takes as input a sequence of hashes, combines them using DigestList
     * data structure and computes hash of the data structure.
     */
    static byte[] digestHashStep(String digestMethod, byte[]... items)
            throws Exception {
        return concatDigests(getDigestAlgorithmURI(digestMethod), items);
    }

    /**
     * Takes as input a sequence of hashes and combines them using DigestList
     * data structure.
     */
    private static byte[] concatDigests(String digestMethodUri, byte[]... items) {
        //TODO xroad8 taken from EvidenceRecordTimeStampSequenceVerifier
        // 1. Create list of hash values
        List<byte[]> hashValueList = new ArrayList<>(Arrays.asList(items));
        // 2a. Exception
        var digestAlgorithm = DigestAlgorithm.forXML(digestMethodUri);
        if (hashValueList.size() == 1) {
            return new DSSMessageDigest(digestAlgorithm, hashValueList.get(0)).getValue();
        }
        // 2b. Binary ascending sort
        hashValueList.sort(ByteArrayComparator.getInstance());
        // 3. Concatenate
        final DSSMessageDigestCalculator digestCalculator = new DSSMessageDigestCalculator(digestAlgorithm);
        for (byte[] hashValue : hashValueList) {
            digestCalculator.update(hashValue);
        }
        // 4. Calculate hash value
        return digestCalculator.getMessageDigest().getValue();
    }
}
