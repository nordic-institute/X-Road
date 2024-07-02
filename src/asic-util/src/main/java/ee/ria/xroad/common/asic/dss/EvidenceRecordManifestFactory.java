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

import eu.europa.esig.asic.manifest.jaxb.ASiCManifestType;
import eu.europa.esig.asic.manifest.jaxb.DataObjectReferenceType;
import eu.europa.esig.asic.manifest.jaxb.ObjectFactory;
import eu.europa.esig.xmldsig.jaxb.DigestMethodType;
import jakarta.xml.bind.JAXBElement;

public class EvidenceRecordManifestFactory {
    private static final ObjectFactory ASIC_OBJECT_FACTORY = new ObjectFactory();
    private static final eu.europa.esig.xmldsig.jaxb.ObjectFactory XMLDSIG_OBJECT_FACTORY = new eu.europa.esig.xmldsig.jaxb.ObjectFactory();

    JAXBElement<ASiCManifestType> createAsicManifest(String evidenceRecordURI, String signatureURI, byte[] digest, String digestAlgo) {
        var signRef = ASIC_OBJECT_FACTORY.createSigReferenceType();
        signRef.setURI(evidenceRecordURI);

        var asicManifest = ASIC_OBJECT_FACTORY.createASiCManifestType();
        asicManifest.setSigReference(signRef);
        asicManifest.getDataObjectReference().add(createDataObjectRef(signatureURI, digest, digestAlgo));

        return ASIC_OBJECT_FACTORY.createASiCManifest(asicManifest);
    }

    private DataObjectReferenceType createDataObjectRef(String signatureURI, byte[] digest, String digestAlgo) {
        var dataObjRef = ASIC_OBJECT_FACTORY.createDataObjectReferenceType();
        dataObjRef.setMimeType("text/xml");//TODO const
        dataObjRef.setURI(signatureURI);
        dataObjRef.setDigestMethod(createDigestMethod(digestAlgo));
        dataObjRef.setDigestValue(digest);
        return dataObjRef;
    }

    private DigestMethodType createDigestMethod(String digestAlgo) {
        var digestMethod = XMLDSIG_OBJECT_FACTORY.createDigestMethodType();
        digestMethod.setAlgorithm(digestAlgo);
        return digestMethod;
    }

}
