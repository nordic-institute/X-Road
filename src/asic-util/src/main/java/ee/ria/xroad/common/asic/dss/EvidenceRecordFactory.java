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
package ee.ria.xroad.common.asic.dss;

import eu.europa.esig.xmlers.jaxb.ArchiveTimeStampSequenceType;
import eu.europa.esig.xmlers.jaxb.ArchiveTimeStampType;
import eu.europa.esig.xmlers.jaxb.CanonicalizationMethodType;
import eu.europa.esig.xmlers.jaxb.DigestMethodType;
import eu.europa.esig.xmlers.jaxb.EvidenceRecordType;
import eu.europa.esig.xmlers.jaxb.HashTreeType;
import eu.europa.esig.xmlers.jaxb.ObjectFactory;

import java.math.BigDecimal;

import static ee.ria.xroad.common.hashchain.HashChainConstants.CANONICALIZATION_METHOD;

public class EvidenceRecordFactory {
    private static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

    EvidenceRecordType createEvidenceRecord(HashTreeType hashTree, String hashTreeDigestAlgo, String timestampToken) {
        var evidenceRecordType = OBJECT_FACTORY.createEvidenceRecordType();
        evidenceRecordType.setVersion(new BigDecimal("1.0"));

        var archiveTimeStamp = createArchiveTimeStamp(hashTree, timestampToken);
        var chain = createArchiveTimeStampChain(archiveTimeStamp, hashTreeDigestAlgo);

        ArchiveTimeStampSequenceType archiveTimeStampSequenceType = createArchiveTimeStampSequence(chain);

        evidenceRecordType.setArchiveTimeStampSequence(archiveTimeStampSequenceType);

        return evidenceRecordType;
    }

    private ArchiveTimeStampSequenceType createArchiveTimeStampSequence(ArchiveTimeStampSequenceType.ArchiveTimeStampChain chain) {
        var archiveTimeStampSequenceType = OBJECT_FACTORY.createArchiveTimeStampSequenceType();
        archiveTimeStampSequenceType.getArchiveTimeStampChain().add(chain);
        return archiveTimeStampSequenceType;
    }

    private ArchiveTimeStampType createArchiveTimeStamp(HashTreeType hashTree, String timeStampTokenValue) {
        var archiveTimeStamp = OBJECT_FACTORY.createArchiveTimeStampType();

        var timeStamp = OBJECT_FACTORY.createTimeStampType();

        var timestampToken = OBJECT_FACTORY.createTimeStampTypeTimeStampToken();
        timestampToken.setType("RFC3161");
        timestampToken.getContent().add(timeStampTokenValue);
        timeStamp.setTimeStampToken(timestampToken);

        archiveTimeStamp.setTimeStamp(timeStamp);
        archiveTimeStamp.setHashTree(hashTree);
        archiveTimeStamp.setOrder(1);
        return archiveTimeStamp;
    }

    private ArchiveTimeStampSequenceType.ArchiveTimeStampChain createArchiveTimeStampChain(ArchiveTimeStampType archiveTimeStamp,
                                                                                           String hashTreeDigestAlgo) {
        var chain = OBJECT_FACTORY.createArchiveTimeStampSequenceTypeArchiveTimeStampChain();
        chain.setDigestMethod(createDigestMethod(hashTreeDigestAlgo));
        chain.setOrder(1); //TODO do we need more than 1 in our use cases?
        chain.setCanonicalizationMethod(createCanonicalizationMethod());
        chain.getArchiveTimeStamp().add(archiveTimeStamp);
        return chain;
    }

    private DigestMethodType createDigestMethod(String digestAlgo) {
        var digestMethod = OBJECT_FACTORY.createDigestMethodType();
        digestMethod.setAlgorithm(digestAlgo);
        return digestMethod;
    }

    private CanonicalizationMethodType createCanonicalizationMethod() {
        var canonicalizationMethod = OBJECT_FACTORY.createCanonicalizationMethodType();
        canonicalizationMethod.setAlgorithm(CANONICALIZATION_METHOD);
        return canonicalizationMethod;
    }
}
