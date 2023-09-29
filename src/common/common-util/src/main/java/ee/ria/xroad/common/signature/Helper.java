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
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.util.XmlUtils;

import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.apache.xml.security.signature.Manifest;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.utils.Constants;
import org.bouncycastle.operator.OperatorCreationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_DIGEST_ALGORITHM_URI;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.getAlgorithmId;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmURI;

/**
 * Local helper class for constructing Xades signatures.
 */
final class Helper {

    static final String BASE_URI = null;

    static final String PREFIX_XADES = "xades:";
    static final String PREFIX_DS = "ds:";

    static final String XMLNS_DS = "xmlns:ds";
    static final String XMLNS_XSI = "xmlns:xsi";
    static final String XMLNS_XADES = "xmlns:xades";

    static final String NS_ASIC = "http://uri.etsi.org/02918/v1.2.1#";
    static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";
    static final String NS_XADES = "http://uri.etsi.org/01903/v1.3.2#";
    static final String NS_MANIFEST = "http://www.w3.org/2000/09/xmldsig#Manifest";
    static final String NS_SIG_PROP = "http://uri.etsi.org/01903#SignedProperties";

    static final String ASIC_TAG = "asic:XAdESSignatures";
    static final String QUALIFYING_PROPS_TAG = "QualifyingProperties";
    static final String TARGET_ATTR = "Target";
    static final String SIGNATURE_VALUE_TAG = "SignatureValue";
    static final String UNSIGNED_PROPS_TAG = "UnsignedProperties";
    static final String UNSIGNED_SIGNATURE_PROPS_TAG = "UnsignedSignatureProperties";
    static final String COMPLETE_CERTIFICATE_REFS_TAG = "CompleteCertificateRefs";
    static final String COMPLETE_REVOCATION_REFS_TAG = "CompleteRevocationRefs";
    static final String SIGNED_DATAOBJ_TAG = "SignedDataObjectProperties";
    static final String DATAOBJECTFORMAT_TAG = "DataObjectFormat";
    static final String MIMETYPE_TAG = "MimeType";
    static final String OBJECTREFERENCE_ATTR = "ObjectReference";
    static final String ISSUER_SERIAL_TAG = "IssuerSerial";
    static final String CERT_DIGEST_TAG = "CertDigest";
    static final String SIGNING_CERTIFICATE_TAG = "SigningCertificate";
    static final String SIGNING_TIME_TAG = "SigningTime";
    static final String SIGNATURE_POLICY_IDENTIFIER_TAG = "SignaturePolicyIdentifier";
    static final String SIGNATURE_POLICY_ID_TAG = "SignaturePolicyId";
    static final String SIG_POLICY_ID_TAG = "SigPolicyId";
    static final String IDENTIFIER_TAG = "Identifier";
    static final String QUALIFIER_ATTR = "Qualifier";
    static final String DESCRIPTION_TAG = "Description";
    static final String SIG_POLICY_HASH_TAG = "SigPolicyHash";
    static final String SIG_POLICY_QUALIFIERS_TAG = "SigPolicyQualifiers";
    static final String SIG_POLICY_QUALIFIER_TAG = "SigPolicyQualifier";
    static final String SPURI_TAG = "SPURI";
    static final String SIGNED_SIGNATURE_PROPS_TAG = "SignedSignatureProperties";
    static final String SIGNED_PROPS_TAG = "SignedProperties";
    static final String ID_ATTRIBUTE = "Id";
    static final String ENCAPSULATED_OCSP_VALUE_TAG = "EncapsulatedOCSPValue";
    static final String OCSP_VALUES_TAG = "OCSPValues";
    static final String REVOCATION_VALUES_TAG = "RevocationValues";
    static final String ENCAPSULATED_X509_CERTIFICATE_TAG = "EncapsulatedX509Certificate";
    static final String CERTIFFICATE_VALUES_TAG = "CertificateValues";
    static final String DIGEST_VALUE_TAG = "DigestValue";
    static final String DIGEST_METHOD_TAG = "DigestMethod";
    static final String DIGEST_ALG_AND_VALUE_TAG = "DigestAlgAndValue";
    static final String PRODUCED_AT_TAG = "ProducedAt";
    static final String RESPONDER_ID_TAG = "ResponderID";
    static final String BYNAME_TAG = "ByName";
    static final String URI_ATTRIBUTE = "URI";
    static final String OCSP_IDENTIFIER_TAG = "OCSPIdentifier";
    static final String OCSP_REF_TAG = "OCSPRef";
    static final String OCSP_REFS_TAG = "OCSPRefs";
    static final String CERT_TAG = "Cert";
    static final String CERT_REFS_TAG = "CertRefs";
    static final String ALGORITHM_ATTRIBUTE = "Algorithm";
    static final String X509_SERIAL_NUMBER_TAG = "X509SerialNumber";
    static final String X509_ISSUER_NAME_TAG = "X509IssuerName";
    static final String XADES_TIMESTAMP_TAG = "XAdESTimeStamp";
    static final String SIGNATURE_TIMESTAMP_TAG = "SignatureTimeStamp";
    static final String INCLUDE_TAG = "Include";
    static final String ENCAPSULATED_TIMESTAMP_TAG = "EncapsulatedTimeStamp";
    static final String REFERENCE_INFO_TAG = "ReferenceInfo";
    static final String ID_TS_ROOT_MANIFEST = "ts-root-manifest";
    static final String ID_TS_MANIFEST = "ts-manifest";
    static final String ID_SIGNATURE = "signature";
    static final String SIGNATURE_REFERENCE_ID = ID_SIGNATURE + "-reference-";
    static final String SIG_MANIFEST = "sig-manifest-";
    static final String SIGNATURE_VALUE_ID = "signature-value";
    static final String ENCAPSULATED_CERT_ID = "encapsulated-cert-";
    static final String OCSP_RESPONSE_ID = "ocsp-response-";
    static final String COMPLETE_REVOCATION_REFS_ID = "complete-revocation-refs";
    static final String COMPLETE_CERTIFICATE_REFS_ID = "complete-certificate-refs";

    private Helper() {
    }

    static String getSignatureRefereceIdForMessage() {
        return SIGNATURE_REFERENCE_ID + "0";
    }

    static String getSignatureReferenceIdForSignedProperties() {
        return SIGNATURE_REFERENCE_ID + "1";
    }

    static Document createDocument() throws Exception {
        Document document = XmlUtils.newDocumentBuilder(true).newDocument();

        // create the root element for XAdES signatures.
        Element root = document.createElementNS(NS_ASIC, ASIC_TAG);
        root.setAttributeNS(Constants.NamespaceSpecNS, XMLNS_DS, NS_DS);
        root.setAttributeNS(Constants.NamespaceSpecNS, XMLNS_XSI, NS_XSI);
        root.setAttributeNS(Constants.NamespaceSpecNS, XMLNS_XADES, NS_XADES);
        document.appendChild(root);

        return document;
    }

    static Document parseDocument(String documentXml, boolean namespaceAware) throws Exception {
        return XmlUtils.parseDocument(new ByteArrayInputStream(documentXml.getBytes(StandardCharsets.UTF_8)),
                namespaceAware);
    }

    static XMLSignature createSignatureElement(Document document, String signatureAlgorithmUri) throws Exception {
        XMLSignature signature = new XMLSignature(document, BASE_URI, signatureAlgorithmUri);
        signature.setId(ID_SIGNATURE);
        document.getDocumentElement().appendChild(signature.getElement());

        return signature;
    }

    /**
     * Creates and returns a ds:DigestMethod element.
     */
    static Element createDigestMethodElement(Document doc, String hashMethod) throws Exception {
        Element digestMethodElement = doc.createElement(PREFIX_DS + Constants._TAG_DIGESTMETHOD);
        digestMethodElement.setAttribute(Constants._ATT_ALGORITHM, getDigestAlgorithmURI(hashMethod));

        return digestMethodElement;
    }

    /**
     * Creates and returns a ds:DigestValue element.
     */
    static Element createDigestValueElement(Document doc, byte[] hashValue) {
        Element digestValueElement = doc.createElement(PREFIX_DS + Constants._TAG_DIGESTVALUE);
        digestValueElement.setTextContent(encodeBase64(hashValue));

        return digestValueElement;
    }

    /**
     * Verifies the digest contained in the digest algorithm and value element against the provided data.
     *
     * @param digAlgAndValueElement the element that contains the digest method
     *                              as the first child and digest value as the second child
     * @param data                  the data
     */
    static boolean verifyDigest(Element digAlgAndValueElement, byte[] data)
            throws NoSuchAlgorithmException, IOException, OperatorCreationException {
        String digestMethod = ((Element) digAlgAndValueElement.getFirstChild()).getAttribute(ALGORITHM_ATTRIBUTE);
        String digestValue = digAlgAndValueElement.getLastChild().getTextContent();

        byte[] digest = calculateDigest(getAlgorithmId(digestMethod), data);

        return MessageDigestAlgorithm.isEqual(decodeBase64(digestValue), digest);
    }

    /**
     * Shortcut for adding a reference to a manifest.
     */
    static void addManifestReference(Manifest manifest, String uri) throws Exception {
        manifest.addDocument(null, "#" + uri, null, DEFAULT_DIGEST_ALGORITHM_URI, null, null);
    }

    /**
     * Returns the nodelist of OCSPRef elements using XPath evaluation.
     */
    static NodeList getOcspRefElements(Element objectContainer) {
        // the OCSP refs are located in the XML:
        // asic:XAdESSignatures
        // - ds:Signature
        // -- ds:Object
        // --- xades:QualifiyingProperties
        // ---- xades:UnsignedProperties
        // ----- xades:UnsignedSignatureProperties
        // ------ xades:CompleteRevocationRefs
        // ------- xades:OCSPRefs
        // -------- xades:OCSPRef

        StringBuilder xpath = new StringBuilder();
        xpath.append(PREFIX_XADES);
        xpath.append(QUALIFYING_PROPS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(UNSIGNED_PROPS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(UNSIGNED_SIGNATURE_PROPS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(COMPLETE_REVOCATION_REFS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(OCSP_REFS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(OCSP_REF_TAG);

        return XmlUtils.getElementsXPathNS(objectContainer, xpath.toString(), getNamespaceCtx());
    }

    /**
     * Returns a node list of EncapsulatedOCSPValue elements using XPath evaluation.
     */
    static NodeList getEncapsulatedOCSPValueElements(Element objectContainer) {
        // the EncapsulatedOCSPValues are located in the XML:
        // asic:XAdESSignatures
        // - ds:Signature
        // -- ds:Object
        // --- xades:QualifiyingProperties
        // ---- xades:UnsignedProperties
        // ----- xades:UnsignedSignatureProperties
        // ------ xades:RevocationValues
        // ------- xades:OCSPValues
        // -------- xades:EncapsulatedOCSPValue

        StringBuilder xpath = new StringBuilder();
        xpath.append(PREFIX_XADES);
        xpath.append(QUALIFYING_PROPS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(UNSIGNED_PROPS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(UNSIGNED_SIGNATURE_PROPS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(REVOCATION_VALUES_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(OCSP_VALUES_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(ENCAPSULATED_OCSP_VALUE_TAG);

        return XmlUtils.getElementsXPathNS(objectContainer, xpath.toString(), getNamespaceCtx());
    }

    /**
     * Returns the nodelist of Cert elements using XPath evaluation.
     */
    static NodeList getCertificateRefElements(Element objectContainer) {
        // the Certificate refs are located in the XML:
        // asic:XAdESSignatures
        // - ds:Signature
        // -- ds:Object
        // --- xades:QualifiyingProperties
        // ---- xades:UnsignedProperties
        // ----- xades:UnsignedSignatureProperties
        // ------ xades:CompleteCertificateRefs
        // ------- xades:CertRefs
        // -------- xades:Cert

        StringBuilder xpath = new StringBuilder();
        xpath.append(PREFIX_XADES);
        xpath.append(QUALIFYING_PROPS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(UNSIGNED_PROPS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(UNSIGNED_SIGNATURE_PROPS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(COMPLETE_CERTIFICATE_REFS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(CERT_REFS_TAG).append("/");
        xpath.append(PREFIX_XADES);
        xpath.append(CERT_TAG);

        return XmlUtils.getElementsXPathNS(objectContainer, xpath.toString(), getNamespaceCtx());
    }

    /***
     * Returns the element name with 'ds:' prefix.
     */
    static String dsElement(String name) {
        return PREFIX_DS + name;
    }

    /***
     * Returns the element name with 'xades:' prefix.
     */
    static String xadesElement(String name) {
        return PREFIX_XADES + name;
    }

    /**
     * Returns the first element by tag name.
     *
     * @param document the document
     * @param tagName  the tag name
     * @throws Exception throws CodedException with code MALFORMED_SIGNATURE if the element cannot be found.
     */
    static Element getFirstElementByTagName(Document document, String tagName) throws Exception {
        return XmlUtils.getFirstElementByTagName(document, tagName)
                .orElseThrow(() -> elementNotFound(tagName));
    }

    static CodedException elementNotFound(String elementTag) {
        return new CodedException(ErrorCodes.X_MALFORMED_SIGNATURE, "Could not find element \"%s\"", elementTag);
    }

    private static NamespaceContext getNamespaceCtx() {
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                switch (prefix) {
                    case "asic":
                        return NS_ASIC;
                    case "ds":
                        return NS_DS;
                    case "xades":
                        return NS_XADES;
                    default:
                        return null;
                }
            }

            @SuppressWarnings("rawtypes")
            @Override
            public Iterator getPrefixes(String val) {
                return null;
            }

            @Override
            public String getPrefix(String uri) {
                return null;
            }
        };
    }
}
