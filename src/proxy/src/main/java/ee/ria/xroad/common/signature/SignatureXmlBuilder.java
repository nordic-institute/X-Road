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

import ee.ria.xroad.common.util.MessageFileNames;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.XmlUtils;

import jakarta.xml.bind.DatatypeConverter;
import org.apache.commons.codec.binary.Base64;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.signature.ObjectContainer;
import org.apache.xml.security.signature.SignedInfo;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.XMLSignatureException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import javax.xml.crypto.dsig.DigestMethod;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import static ee.ria.xroad.common.signature.Helper.ALGORITHM_ATTRIBUTE;
import static ee.ria.xroad.common.signature.Helper.CERTIFFICATE_VALUES_TAG;
import static ee.ria.xroad.common.signature.Helper.CERT_DIGEST_TAG;
import static ee.ria.xroad.common.signature.Helper.CERT_REFS_TAG;
import static ee.ria.xroad.common.signature.Helper.CERT_TAG;
import static ee.ria.xroad.common.signature.Helper.COMPLETE_CERTIFICATE_REFS_ID;
import static ee.ria.xroad.common.signature.Helper.COMPLETE_CERTIFICATE_REFS_TAG;
import static ee.ria.xroad.common.signature.Helper.DATAOBJECTFORMAT_TAG;
import static ee.ria.xroad.common.signature.Helper.DESCRIPTION_TAG;
import static ee.ria.xroad.common.signature.Helper.DIGEST_METHOD_TAG;
import static ee.ria.xroad.common.signature.Helper.DIGEST_VALUE_TAG;
import static ee.ria.xroad.common.signature.Helper.ENCAPSULATED_CERT_ID;
import static ee.ria.xroad.common.signature.Helper.ENCAPSULATED_OCSP_VALUE_TAG;
import static ee.ria.xroad.common.signature.Helper.ENCAPSULATED_X509_CERTIFICATE_TAG;
import static ee.ria.xroad.common.signature.Helper.IDENTIFIER_TAG;
import static ee.ria.xroad.common.signature.Helper.ID_ATTRIBUTE;
import static ee.ria.xroad.common.signature.Helper.ID_SIGNATURE;
import static ee.ria.xroad.common.signature.Helper.ISSUER_SERIAL_TAG;
import static ee.ria.xroad.common.signature.Helper.MIMETYPE_TAG;
import static ee.ria.xroad.common.signature.Helper.NS_SIG_PROP;
import static ee.ria.xroad.common.signature.Helper.OBJECTREFERENCE_ATTR;
import static ee.ria.xroad.common.signature.Helper.OCSP_RESPONSE_ID;
import static ee.ria.xroad.common.signature.Helper.OCSP_VALUES_TAG;
import static ee.ria.xroad.common.signature.Helper.PREFIX_DS;
import static ee.ria.xroad.common.signature.Helper.PREFIX_XADES;
import static ee.ria.xroad.common.signature.Helper.QUALIFIER_ATTR;
import static ee.ria.xroad.common.signature.Helper.QUALIFYING_PROPS_TAG;
import static ee.ria.xroad.common.signature.Helper.REVOCATION_VALUES_TAG;
import static ee.ria.xroad.common.signature.Helper.SIGNATURE_POLICY_IDENTIFIER_TAG;
import static ee.ria.xroad.common.signature.Helper.SIGNATURE_POLICY_ID_TAG;
import static ee.ria.xroad.common.signature.Helper.SIGNATURE_VALUE_ID;
import static ee.ria.xroad.common.signature.Helper.SIGNATURE_VALUE_TAG;
import static ee.ria.xroad.common.signature.Helper.SIGNED_DATAOBJ_TAG;
import static ee.ria.xroad.common.signature.Helper.SIGNED_PROPS_TAG;
import static ee.ria.xroad.common.signature.Helper.SIGNED_SIGNATURE_PROPS_TAG;
import static ee.ria.xroad.common.signature.Helper.SIGNING_CERTIFICATE_TAG;
import static ee.ria.xroad.common.signature.Helper.SIGNING_TIME_TAG;
import static ee.ria.xroad.common.signature.Helper.SIG_POLICY_HASH_TAG;
import static ee.ria.xroad.common.signature.Helper.SIG_POLICY_ID_TAG;
import static ee.ria.xroad.common.signature.Helper.SIG_POLICY_QUALIFIERS_TAG;
import static ee.ria.xroad.common.signature.Helper.SIG_POLICY_QUALIFIER_TAG;
import static ee.ria.xroad.common.signature.Helper.SPURI_TAG;
import static ee.ria.xroad.common.signature.Helper.TARGET_ATTR;
import static ee.ria.xroad.common.signature.Helper.UNSIGNED_PROPS_TAG;
import static ee.ria.xroad.common.signature.Helper.UNSIGNED_SIGNATURE_PROPS_TAG;
import static ee.ria.xroad.common.signature.Helper.URI_ATTRIBUTE;
import static ee.ria.xroad.common.signature.Helper.X509_ISSUER_NAME_TAG;
import static ee.ria.xroad.common.signature.Helper.X509_SERIAL_NUMBER_TAG;
import static ee.ria.xroad.common.signature.Helper.createDocument;
import static ee.ria.xroad.common.signature.Helper.createSignatureElement;
import static ee.ria.xroad.common.signature.Helper.elementNotFound;
import static ee.ria.xroad.common.signature.Helper.getSignatureRefereceIdForMessage;
import static ee.ria.xroad.common.signature.Helper.getSignatureReferenceIdForSignedProperties;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmURI;

/**
 * Encapsulates the AsiC XAdES signature profile. This class creates the signature used in signing the messages.
 */
final class SignatureXmlBuilder {

    private static final String SIGNATURE_POLICY_IDENTIFIER = "urn:oid:1.3.6.1.4.1.3516.16.2";
    private static final String SIGNATURE_POLICY_DESCRIPTION =
            "Profile for High Performance Digital Signatures (version 1.2)";
    private static final String SIGNATURE_POLICY_DIGEST_METHOD = DigestMethod.SHA512;
    private static final String SIGNATURE_POLICY_SHA512_DIGEST =
            "BuO0EDNfkxSVlUbxCzmQPzX1AUF1/xx9ytWHk3/6SAOePxQiniEfDYk+90QeYb3lWpV3Izhuz9fKaYyE+lTcXw==";
    private static final String SIGNATURE_POLICY_QUALIFIER_SPURI = "https://repo.cyber.ee/dsig-profile-1.2.pdf";

    // The identifier is an Object IDentifier encoded as an URN.
    private static final String OID_AS_URN = "OIDAsURN";

    private static final int MAX_LINE_LENGTH = 76;

    private final List<X509Certificate> extraCertificates = new ArrayList<>();
    private final List<OCSPResp> ocspResponses = new ArrayList<>();

    private final X509Certificate signingCert;
    private final String hashAlgorithmId;
    private final String hashAlgorithmURI;

    private Document document;
    private XMLSignature signature;
    private ObjectContainer objectContainer;
    private String documentName;

    SignatureXmlBuilder(SigningRequest request, String hashAlgorithmId) throws Exception {
        this.signingCert = request.getSigningCert();
        this.extraCertificates.addAll(request.getExtraCertificates());
        this.ocspResponses.addAll(request.getOcspResponses());
        this.hashAlgorithmId = hashAlgorithmId;
        this.hashAlgorithmURI = getDigestAlgorithmURI(hashAlgorithmId);
    }

    byte[] createDataToBeSigned(String docName, ResourceResolverSpi resourceResolver, String signatureAlgorithmUri)
            throws Exception {
        this.documentName = docName;

        document = createDocument();

        signature = createSignatureElement(document, signatureAlgorithmUri);
        signature.addKeyInfo(signingCert);

        signature.addResourceResolver(new IdResolver(document));
        signature.addResourceResolver(resourceResolver);

        signature.addDocument(docName, null, getHashAlgorithmURI(), getSignatureRefereceIdForMessage(), null);

        createObjectContainer();
        createQualifyingProperties();

        return createDataToBeSigned();
    }

    String createSignatureXml(byte[] signatureValue) throws Exception {
        Element signatureValueElement = XmlUtils.getFirstElementByTagName(document, PREFIX_DS + SIGNATURE_VALUE_TAG)
                .orElseThrow(() -> elementNotFound(PREFIX_DS + SIGNATURE_VALUE_TAG));

        while (signatureValueElement.hasChildNodes()) {
            signatureValueElement.removeChild(signatureValueElement.getFirstChild());
        }

        String base64codedValue = Base64.encodeBase64String(signatureValue);

        if (base64codedValue.length() > MAX_LINE_LENGTH && !org.apache.xml.security.utils.XMLUtils.ignoreLineBreaks()) {
            base64codedValue = "\n" + base64codedValue + "\n";
        }

        Text textNode = document.createTextNode(base64codedValue);

        signatureValueElement.appendChild(textNode);
        signatureValueElement.setAttribute(ID_ATTRIBUTE, SIGNATURE_VALUE_ID);

        return XmlUtils.toXml(document);
    }

    private String getHashAlgorithmId() {
        return hashAlgorithmId;
    }

    private String getHashAlgorithmURI() {
        return hashAlgorithmURI;
    }

    private byte[] createDataToBeSigned() throws Exception {
        try {
            SignedInfo si = signature.getSignedInfo();

            // Generate digest values for all References in this SignedInfo.
            si.generateDigestValues();

            return si.getCanonicalizedOctetStream();
        } catch (XMLSecurityException ex) {
            throw new XMLSignatureException(ex, "empty");
        }
    }

    private void createObjectContainer() throws Exception {
        objectContainer = new ObjectContainer(document);
        signature.appendObject(objectContainer);
    }

    private void createQualifyingProperties() throws Exception {
        Element qualifyingProperties = createXadesElement(QUALIFYING_PROPS_TAG);
        qualifyingProperties.setAttribute(TARGET_ATTR, "#" + ID_SIGNATURE);

        qualifyingProperties.appendChild(createSignedProperties());
        qualifyingProperties.appendChild(createUnsignedProperties());

        objectContainer.appendChild(qualifyingProperties);
    }

    private Element createSignedProperties() throws Exception {
        String id = "signed-properties";
        Element signedProperties = createXadesElement(SIGNED_PROPS_TAG);
        signedProperties.setAttribute(ID_ATTRIBUTE, id);

        createSignedSignatureProperties(signedProperties);
        createSignedDataObjectProperties(signedProperties);

        signature.addDocument("#" + id, null, getHashAlgorithmURI(), getSignatureReferenceIdForSignedProperties(),
                NS_SIG_PROP);

        return signedProperties;
    }

    private void createSignedSignatureProperties(Element signedProperties) throws Exception {
        Element signedSignatureProperties = createXadesElement(signedProperties, SIGNED_SIGNATURE_PROPS_TAG);

        createSigningTime(signedSignatureProperties);
        createSigningCertificate(signedSignatureProperties);
        createSignaturePolicyIdentifier(signedSignatureProperties);
    }

    private void createSigningTime(Element signedSignatureProperties) {
        Element signingTime = createXadesElement(signedSignatureProperties, SIGNING_TIME_TAG);
        Calendar signatureSigningTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        signingTime.setTextContent(DatatypeConverter.printDateTime(signatureSigningTime));
    }

    private void createSigningCertificate(Element signedSignatureProperties) throws Exception {
        Element signingCertificate = createXadesElement(signedSignatureProperties, SIGNING_CERTIFICATE_TAG);
        Element cert = createXadesElement(signingCertificate, CERT_TAG);

        createCertDigestAndIssuerSerial(signature.getKeyInfo().getX509Certificate(), cert);
    }

    private void createSignaturePolicyIdentifier(Element signedSignatureProperties) throws Exception {
        Element signaturePolicyIdentifier = createXadesElement(signedSignatureProperties,
                SIGNATURE_POLICY_IDENTIFIER_TAG);
        Element signaturePolicyId = createXadesElement(signaturePolicyIdentifier, SIGNATURE_POLICY_ID_TAG);

        createSigPolicyId(signaturePolicyId);
        createSigPolicyHash(signaturePolicyId);
        createSigPolicyQualifiers(signaturePolicyId);
    }

    private void createSigPolicyId(Element signaturePolicyId) {
        Element sigPolicyId = createXadesElement(signaturePolicyId, SIG_POLICY_ID_TAG);

        Element identifier = createXadesElement(sigPolicyId, IDENTIFIER_TAG);
        identifier.setAttribute(QUALIFIER_ATTR, OID_AS_URN);
        identifier.setTextContent(SIGNATURE_POLICY_IDENTIFIER);

        Element description = createXadesElement(sigPolicyId, DESCRIPTION_TAG);
        description.setTextContent(SIGNATURE_POLICY_DESCRIPTION);
    }

    private void createSigPolicyHash(Element signaturePolicyId) throws Exception {
        Element sigPolicyHash = createXadesElement(signaturePolicyId, SIG_POLICY_HASH_TAG);

        createDigestAlgAndValue(SIGNATURE_POLICY_DIGEST_METHOD, SIGNATURE_POLICY_SHA512_DIGEST, sigPolicyHash);
    }

    private void createSigPolicyQualifiers(Element signaturePolicyId) {
        Element sigPolicyQualifiers = createXadesElement(signaturePolicyId, SIG_POLICY_QUALIFIERS_TAG);
        Element sigPolicyQualifier = createXadesElement(sigPolicyQualifiers, SIG_POLICY_QUALIFIER_TAG);
        Element spuri = createXadesElement(sigPolicyQualifier, SPURI_TAG);

        spuri.setTextContent(SIGNATURE_POLICY_QUALIFIER_SPURI);
    }

    private void createSignedDataObjectProperties(Element signedProperties) {
        Element signedDataObjectProperties = createXadesElement(signedProperties, SIGNED_DATAOBJ_TAG);

        Element dataObjectFormat = createXadesElement(signedDataObjectProperties, DATAOBJECTFORMAT_TAG);
        dataObjectFormat.setAttribute(OBJECTREFERENCE_ATTR, "#" + getSignatureRefereceIdForMessage());

        Element mimeType = createXadesElement(dataObjectFormat, MIMETYPE_TAG);

        if (MessageFileNames.SIG_HASH_CHAIN_RESULT.equals(documentName)) {
            mimeType.setTextContent(MimeTypes.HASH_CHAIN_RESULT);
        } else {
            mimeType.setTextContent(MimeTypes.TEXT_XML);
        }
    }

    private void createCertDigestAndIssuerSerial(X509Certificate cert, Element certElement) throws Exception {
        Element certDigest = createXadesElement(certElement, CERT_DIGEST_TAG);
        createCertDigestAlgAndValue(cert, certDigest);

        Element issuerSerial = createXadesElement(certElement, ISSUER_SERIAL_TAG);
        createCertId(cert, issuerSerial);
    }

    private void createCertDigestAlgAndValue(X509Certificate cert, Element element) throws Exception {
        createDigestAlgAndValue(getHashAlgorithmURI(), digest(cert, getHashAlgorithmId()), element);
    }

    private void createDigestAlgAndValue(String algorithmUri, String digest, Element element) throws Exception {
        Element digestMethod = createDsElement(element, DIGEST_METHOD_TAG);
        digestMethod.setAttribute(ALGORITHM_ATTRIBUTE, algorithmUri);

        Element digestValue = createDsElement(element, DIGEST_VALUE_TAG);
        digestValue.setTextContent(digest);
    }

    private void createCertId(X509Certificate cert, Element element) {
        Element issuerName = createDsElement(element, X509_ISSUER_NAME_TAG);
        issuerName.setTextContent(cert.getIssuerX500Principal().getName());

        Element issuerNumber = createDsElement(element, X509_SERIAL_NUMBER_TAG);
        issuerNumber.setTextContent(cert.getSerialNumber().toString());
    }

    private Element createUnsignedProperties() throws Exception {
        Element unsignedProperties = createXadesElement(UNSIGNED_PROPS_TAG);
        Element unsignedSignatureProperties = createXadesElement(unsignedProperties, UNSIGNED_SIGNATURE_PROPS_TAG);

        if (!extraCertificates.isEmpty()) {
            createCompleteCertificateRefs(unsignedSignatureProperties);
        }

        if (!extraCertificates.isEmpty()) {
            createCertificateValues(unsignedSignatureProperties);
        }

        createRevocationValues(unsignedSignatureProperties);

        return unsignedProperties;
    }

    private void createRevocationValues(Element unsignedSignatureProperties) throws Exception {
        Element revocationValues = createXadesElement(unsignedSignatureProperties, REVOCATION_VALUES_TAG);
        Element ocspValues = createXadesElement(revocationValues, OCSP_VALUES_TAG);

        int c = 1;

        for (OCSPResp ocspResp : ocspResponses) {
            createOcspValue(ocspValues, ocspResp, OCSP_RESPONSE_ID + (c++));
        }
    }

    private void createOcspValue(Element ocspValues, OCSPResp ocspResponse, String id) throws IOException {
        Element encapsulatedOcspValue = createXadesElement(ocspValues, ENCAPSULATED_OCSP_VALUE_TAG);
        encapsulatedOcspValue.setAttribute(ID_ATTRIBUTE, id);
        encapsulatedOcspValue.setTextContent(encodeBase64(ocspResponse.getEncoded()));
    }

    private void createCertificateValues(Element unsignedSignatureProperties) throws Exception {
        Element certificateValues = createXadesElement(unsignedSignatureProperties, CERTIFFICATE_VALUES_TAG);

        int c = 1;

        for (X509Certificate cert : extraCertificates) {
            Element encapsulatedX509Certificate = createXadesElement(certificateValues,
                    ENCAPSULATED_X509_CERTIFICATE_TAG);
            encapsulatedX509Certificate.setTextContent(encodeBase64(cert.getEncoded()));
            encapsulatedX509Certificate.setAttribute(ID_ATTRIBUTE, ENCAPSULATED_CERT_ID + (c++));
        }
    }

    private void createCompleteCertificateRefs(Element unsignedSignatureProperties) throws Exception {
        Element completeCertificateRefs = createXadesElement(unsignedSignatureProperties,
                COMPLETE_CERTIFICATE_REFS_TAG);
        completeCertificateRefs.setAttribute(ID_ATTRIBUTE, COMPLETE_CERTIFICATE_REFS_ID/* + masterHash*/);

        Element certRefs = createXadesElement(completeCertificateRefs, CERT_REFS_TAG);

        // add references to all the extra certificates
        int c = 1;

        for (X509Certificate cert : extraCertificates) {
            Element certElement = createXadesElement(certRefs, CERT_TAG);
            certElement.setAttribute(URI_ATTRIBUTE, "#" + ENCAPSULATED_CERT_ID + (c++));
            createCertDigestAndIssuerSerial(cert, certElement);
        }
    }

    private Element createXadesElement(Element parent, String name) {
        Element element = createXadesElement(name);
        parent.appendChild(element);

        return element;
    }

    private Element createXadesElement(String name) {
        return document.createElement(PREFIX_XADES + name);
    }

    private Element createDsElement(Element parent, String name) {
        Element element = createDsElement(name);
        parent.appendChild(element);

        return element;
    }

    private Element createDsElement(String name) {
        return document.createElement(PREFIX_DS + name);
    }

    private static String digest(X509Certificate cert, String method) throws Exception {
        return digest(cert.getEncoded(), method);
    }

    private static String digest(byte[] encoded, String method) throws Exception {
        return encodeBase64(calculateDigest(method, encoded));
    }
}
