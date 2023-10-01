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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.XmlUtils;

import org.apache.xml.security.signature.Manifest;
import org.apache.xml.security.signature.ObjectContainer;
import org.apache.xml.security.signature.Reference;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.XMLSignatureException;
import org.apache.xml.security.utils.Constants;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.OperatorCreationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.signature.Helper.BASE_URI;
import static ee.ria.xroad.common.signature.Helper.COMPLETE_CERTIFICATE_REFS_ID;
import static ee.ria.xroad.common.signature.Helper.ENCAPSULATED_TIMESTAMP_TAG;
import static ee.ria.xroad.common.signature.Helper.ID_TS_MANIFEST;
import static ee.ria.xroad.common.signature.Helper.ID_TS_ROOT_MANIFEST;
import static ee.ria.xroad.common.signature.Helper.SIGNATURE_TIMESTAMP_TAG;
import static ee.ria.xroad.common.signature.Helper.SIGNATURE_VALUE_ID;
import static ee.ria.xroad.common.signature.Helper.UNSIGNED_SIGNATURE_PROPS_TAG;
import static ee.ria.xroad.common.signature.Helper.URI_ATTRIBUTE;
import static ee.ria.xroad.common.signature.Helper.addManifestReference;
import static ee.ria.xroad.common.signature.Helper.dsElement;
import static ee.ria.xroad.common.signature.Helper.elementNotFound;
import static ee.ria.xroad.common.signature.Helper.getCertificateRefElements;
import static ee.ria.xroad.common.signature.Helper.getEncapsulatedOCSPValueElements;
import static ee.ria.xroad.common.signature.Helper.getFirstElementByTagName;
import static ee.ria.xroad.common.signature.Helper.parseDocument;
import static ee.ria.xroad.common.signature.Helper.verifyDigest;
import static ee.ria.xroad.common.signature.Helper.xadesElement;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;

/**
 * Container class for the XML signature specific objects.
 */
public class Signature {

    static {
        org.apache.xml.security.Init.init();
    }

    /** The XML document structure. */
    private final Document document;

    /** The XML signature structure. */
    private XMLSignature xmlSignature;

    /** The object container structure. */
    private ObjectContainer objectContainer;

    /**
     * Constructs new signature from specified signature XML string.
     * @param signatureXml signature XML string from which to construct the signature object
     */
    public Signature(String signatureXml) {
        this(new ByteArrayInputStream(signatureXml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Constructs new signature from specified input stream.
     * @param signatureXml input stream from which to construct the signature object
     */
    public Signature(InputStream signatureXml) {
        try {
            document = XmlUtils.parseDocument(signatureXml);
            readSignature();
            readObjectContainer();
        } catch (XMLSignatureException e) {
            throw new CodedException(X_MALFORMED_SIGNATURE, e);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    /**
     * Constructs new signature from specified parts.
     * @param document        document part of the signature object
     * @param signature       signature part of the signature object
     * @param objectContainer object container part of the signature object
     */
    public Signature(Document document, XMLSignature signature, ObjectContainer objectContainer) {
        this.document = document;
        this.xmlSignature = signature;
        this.objectContainer = objectContainer;
    }

    /**
     * Returns the XML document.
     * @return Document
     */
    public Document getDocument() {
        return document;
    }

    /**
     * Returns the XML signature object.
     * @return XMLSignature
     */
    public XMLSignature getXmlSignature() {
        return xmlSignature;
    }

    /**
     * Returns the object container.
     * @return ObjectContainer
     */
    public ObjectContainer getObjectContainer() {
        return objectContainer;
    }

    /**
     * @param uri the uri that is expected to be found in the signature
     * @return true, if the signature contains the specified URI, false otherwise.
     * @throws Exception if errors occur when reading the signature
     */
    public boolean references(String uri) throws Exception {
        for (int i = 0; i < xmlSignature.getSignedInfo().getLength(); i++) {
            Reference ref = xmlSignature.getSignedInfo().item(i);

            if (uri.equals(ref.getURI())) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return a list of existing timestamp manifests referenced in the timestamp root manifest. If the timestamp
     * root manifest does not exist, returns empty list.
     * @throws Exception if errors occur when reading the signature
     */
    List<Manifest> getTimestampManifests() throws Exception {
        List<Manifest> manifests = new ArrayList<>();
        Element tsRootManifestElement = XmlUtils.getElementById(document, ID_TS_ROOT_MANIFEST);

        if (tsRootManifestElement != null) {
            Manifest tsRootManifest = new Manifest(tsRootManifestElement, null);

            for (int i = 0; i < tsRootManifest.getLength(); i++) {
                Reference ref = tsRootManifest.item(i);
                Element tsManifestElement = XmlUtils.getElementById(document, ref.getURI());

                if (tsManifestElement != null) {
                    manifests.add(new Manifest(tsManifestElement, null));
                }
            }
        }

        return manifests;
    }

    /**
     * Creates the time stamp manifest and adds it to the signature.
     * @param rnd random string to append to the manifest ID
     * @return the newly created Manifest
     * @throws Exception in case of any errors
     */
    public Manifest createTimestampManifest(String rnd) throws Exception {
        Manifest manifest = new Manifest(document);
        manifest.setId(ID_TS_MANIFEST + "-" + rnd);

        // add references
        addManifestReference(manifest, SIGNATURE_VALUE_ID);
        //addManifestReference(manifest, COMPLETE_REVOCATION_REFS_ID);

        // this reference is optional
        if (XmlUtils.getElementById(document, COMPLETE_CERTIFICATE_REFS_ID) != null) {
            addManifestReference(manifest, COMPLETE_CERTIFICATE_REFS_ID);
        }

        manifest.addResourceResolver(new IdResolver(document));
        manifest.generateDigestValues();

        objectContainer.appendChild(manifest.getElement());

        return manifest;
    }

    /**
     * @param manifestId ID of the manifest to retrieve
     * @return reference to the specified manifest. Does not modify the object.
     * @throws Exception if errors occurred during manifest retrieval
     */
    public Reference getManifestRef(String manifestId) throws Exception {
        Manifest manifest = new Manifest(document);

        addManifestReference(manifest, manifestId);
        manifest.generateDigestValues();

        return manifest.item(0);
    }

    /**
     * Adds a timestamp manifest to this signature.
     * @param timestampManifestXml the timestamp manifest XML
     * @throws Exception if errors occur when parsing the signature document part
     */
    public void addTimestampManifest(String timestampManifestXml) throws Exception {
        Element object = getFirstElementByTagName(document, dsElement(Constants._TAG_OBJECT));

        Document timestampManifestDoc = parseDocument(timestampManifestXml, false);
        Element timestampManifestElement = timestampManifestDoc.getDocumentElement();
        object.appendChild(document.importNode(timestampManifestElement, true));
    }

    /**
     * Adds the SignatureTimeStamp element containing the base64 encoded timestamp DER.
     * @param timestampDer the timestamp bytes
     * @throws Exception if errors occur when parsing the signature document part
     */
    public void addSignatureTimestamp(byte[] timestampDer) throws Exception {
        Element encapsulatedTimeStampElement = document.createElement(xadesElement(ENCAPSULATED_TIMESTAMP_TAG));
        encapsulatedTimeStampElement.setTextContent(encodeBase64(timestampDer));

        Element signatureTimeStampElement = document.createElement(xadesElement(SIGNATURE_TIMESTAMP_TAG));
        signatureTimeStampElement.appendChild(encapsulatedTimeStampElement);

        Element unsignedProperties = getFirstElementByTagName(document, xadesElement(UNSIGNED_SIGNATURE_PROPS_TAG));
        unsignedProperties.insertBefore(signatureTimeStampElement, unsignedProperties.getFirstChild());
    }

    /**
     * @return the timestamp value as base64 encoded string.
     * @throws Exception if errors occur when parsing the signature document part
     */
    public String getSignatureTimestamp() throws Exception {
        return getXadesElement(ENCAPSULATED_TIMESTAMP_TAG)
                .orElseGet(() -> getXadesElement(SIGNATURE_TIMESTAMP_TAG) // For backward compatibility.
                        .orElseThrow(() -> elementNotFound(ENCAPSULATED_TIMESTAMP_TAG)))
                .getTextContent();
    }

    private Optional<Element> getXadesElement(String elementTag) {
        return XmlUtils.getFirstElementByTagName(document, xadesElement(elementTag));
    }

    /**
     * @return the signature as XML String.
     * @throws Exception if an error occurs
     */
    public String toXml() throws Exception {
        return XmlUtils.toXml(document);
    }

    @Override
    public String toString() {
        try {
            return toXml();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the certificate used to sign this signature.
     * @throws Exception if errors occur when resolving the certificate
     */
    public X509Certificate getSigningCertificate() throws Exception {
        return xmlSignature.getKeyInfo().getX509Certificate();
    }

    /**
     * Returns list of additional certificates that are included in the signature.
     */
    List<X509Certificate> getExtraCertificates() {
        List<X509Certificate> extraCertificates = new ArrayList<>();
        NodeList certificateRefs = getCertificateRefElements(objectContainer.getElement());

        if (certificateRefs == null || certificateRefs.getLength() == 0) {
            // returning empty list, since there are no extra certificates
            return extraCertificates;
        }

        for (int i = 0; i < certificateRefs.getLength(); i++) {
            Element certRef = (Element)certificateRefs.item(i);
            String certId = certRef.getAttribute(URI_ATTRIBUTE);

            if (certId == null || certId.isEmpty()) {
                throw new CodedException(X_MALFORMED_SIGNATURE, "Missing certificate id attribute");
            }

            // we have the ocsp response element id, let's find the response
            Element certElem = XmlUtils.getElementById(document, certId);

            if (certElem == null) {
                throw new CodedException(X_MALFORMED_SIGNATURE, "Could not find certificate with id " + certId);
            }

            try {
                X509Certificate x509 = CryptoUtils.readCertificate(certElem.getTextContent());

                // we now have the certificate constructed, verify the digest
                if (!verifyDigest((Element)certRef.getFirstChild(), x509.getEncoded())) {
                    throw new CodedException(X_MALFORMED_SIGNATURE, "Certificate (%s) digest does not match",
                            x509.getSerialNumber());
                }

                extraCertificates.add(x509);
            } catch (CertificateException | NoSuchAlgorithmException | IOException | OperatorCreationException e) {
                throw new CodedException(X_MALFORMED_SIGNATURE, e);
            }
        }

        return extraCertificates;
    }

    /**
     * Return list of OCSP responses included in the signature.
     */
    List<OCSPResp> getOcspResponses() {
        List<OCSPResp> ocspResponses = new ArrayList<>();

        NodeList ocspValueElements = getEncapsulatedOCSPValueElements(objectContainer.getElement());

        if (ocspValueElements == null || ocspValueElements.getLength() == 0) {
            throw new CodedException(X_MALFORMED_SIGNATURE, "Could not get any OCSP elements from signature");
        }

        for (int i = 0; i < ocspValueElements.getLength(); i++) {
            Element ocspResponseElem = (Element)ocspValueElements.item(i);

            // we have the ocsp response in base64 form, attempt to parse it
            String base64 = ocspResponseElem.getTextContent();

            try {
                ocspResponses.add(new OCSPResp(decodeBase64(base64)));
            } catch (IOException e) {
                throw new CodedException(X_MALFORMED_SIGNATURE, e);
            }
        }

        return ocspResponses;
    }

    /**
     * Reads the signature element from the document.
     */
    private void readSignature() throws Exception {
        Element signatureElement = getFirstElementByTagName(document, dsElement(Constants._TAG_SIGNATURE));
        xmlSignature = new XMLSignature(signatureElement, BASE_URI);
    }

    /**
     * Reads the object container element from the document.
     */
    private void readObjectContainer() throws Exception {
        Element objectElement = getFirstElementByTagName(document, dsElement(Constants._TAG_OBJECT));
        objectContainer = new ObjectContainer(objectElement, BASE_URI);
    }
}
