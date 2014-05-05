package ee.cyber.sdsb.common.signature;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.apache.xml.security.signature.Manifest;
import org.apache.xml.security.signature.ObjectContainer;
import org.apache.xml.security.signature.Reference;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.utils.Constants;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.OperatorCreationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.XmlUtils;

import static ee.cyber.sdsb.common.signature.Helper.*;

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
     */
    public Signature(String signatureXml) {
        this(new ByteArrayInputStream(
                signatureXml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Constructs new signature from specified input stream.
     */
    public Signature(InputStream signatureXml) {
        try {
            document = XmlUtils.parseDocument(signatureXml);
            readSignature();
            readObjectContainer();
        } catch (Exception e) {
            throw ErrorCodes.translateException(e);
        }
    }

    /**
     * Constructs new signature from specified parts.
     */
    public Signature(Document document, XMLSignature signature,
            ObjectContainer objectContainer) {
        this.document = document;
        this.xmlSignature = signature;
        this.objectContainer = objectContainer;
    }

    /**
     * Returns the XML document.
     */
    public Document getDocument() {
        return document;
    }

    /**
     * Returns the XML signature object.
     */
    public XMLSignature getXmlSignature() {
        return xmlSignature;
    }

    /**
     * Returns the object container.
     */
    public ObjectContainer getObjectContainer() {
        return objectContainer;
    }

    /**
     * Returns a list of existing timestamp manifests referenced in the
     * timestamp root manifest. If the timestamp root manifest does not
     * exist, returns empty list.
     */
    public List<Manifest> getTimestampManifests() throws Exception {
        List<Manifest> manifests = new ArrayList<>();

        Element tsRootManifestElement =
                XmlUtils.getElementById(document, ID_TS_ROOT_MANIFEST);
        if (tsRootManifestElement != null) {
            Manifest tsRootManifest = new Manifest(tsRootManifestElement, null);
            for (int i = 0; i < tsRootManifest.getLength(); i++) {
                Reference ref = tsRootManifest.item(i);
                Element tsManifestElement =
                        XmlUtils.getElementById(document, ref.getURI());
                if (tsManifestElement != null) {
                    manifests.add(new Manifest(tsManifestElement, null));
                }
            }
        }

        return manifests;
    }

    /**
     * Creates the time stamp manifest and adds it to the signature.
     */
    public Manifest createTimestampManifest(String rnd) throws Exception {
        Manifest manifest = new Manifest(document);
        manifest.setId(ID_TS_MANIFEST + "-" + rnd);

        // add references
        addManifestReference(manifest, SIGNATURE_VALUE_ID);
        addManifestReference(manifest, COMPLETE_REVOCATION_REFS_ID);

        // this reference is optional
        if (XmlUtils.getElementById(
                document, COMPLETE_CERTIFICATE_REFS_ID) != null) {
            addManifestReference(manifest, COMPLETE_CERTIFICATE_REFS_ID);
        }

        manifest.generateDigestValues();
        objectContainer.appendChild(manifest.getElement());
        return manifest;
    }

    /**
     * Returns reference to the specified manifest. Does not modify the object.
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
     */
    public void addTimestampManifest(String timestampManifestXml)
            throws Exception {
        Element object = getFirstElementByTagName(
                document, dsElement(Constants._TAG_OBJECT));

        Document timestampManifestDoc =
                parseDocument(timestampManifestXml, false);
        Element timestampManifestElement =
                timestampManifestDoc.getDocumentElement();
        object.appendChild(document.importNode(timestampManifestElement, true));
    }

    /**
     * Adds a XAdESTimeStamp element to the signature
     * @param timestampDERBase64 the base64 timestamp DER encoded data
     * @throws Exception
     */
    public void addXadesTimestamp(String timestampDERBase64)
            throws Exception {
        Element unsignedProperties = getFirstElementByTagName(document,
                xadesElement(UNSIGNED_SIGNATURE_PROPS_TAG));

        Element includeRootManifestElement =
                document.createElement(xadesElement(INCLUDE_TAG));
        includeRootManifestElement.setAttribute(
                URI_ATTRIBUTE, ID_TS_ROOT_MANIFEST);

        Element encapsulatedTimeStampElement =
                document.createElement(xadesElement(ENCAPSULATED_TIMESTAMP_TAG));
        encapsulatedTimeStampElement.setTextContent(timestampDERBase64);

        Element xadesTimeStamp =
                document.createElement(xadesElement(XADES_TIMESTAMP_TAG));
        xadesTimeStamp.appendChild(includeRootManifestElement);
        xadesTimeStamp.appendChild(encapsulatedTimeStampElement);

        unsignedProperties.appendChild(xadesTimeStamp);
    }

    public Element getXadesTimestamp() {
        return XmlUtils.getFirstElementByTagName(document,
                xadesElement(XADES_TIMESTAMP_TAG));
    }

    /**
     * Returns the signature as XML.
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
     * Returns the certificate used to sign this signature.
     */
    public X509Certificate getSigningCertificate() throws Exception {
        return xmlSignature.getKeyInfo().getX509Certificate();
    }

    /**
     * Returns list of additional certificates that are included in the
     * signature.
     */
    List<X509Certificate> getExtraCertificates() {
        List<X509Certificate> extraCertificates = new ArrayList<>();

        NodeList certificateRefs =
                getCertificateRefElements(objectContainer.getElement());
        if (certificateRefs == null || certificateRefs.getLength() == 0) {
            // returning empty list, since there are no extra certificates
            return extraCertificates;
        }

        for (int i = 0; i < certificateRefs.getLength(); i++) {
            Element certRef =  (Element) certificateRefs.item(i);
            String certId = certRef.getAttribute(URI_ATTRIBUTE);
            if (certId == null || certId.isEmpty()) {
                throw new CodedException(ErrorCodes.X_MALFORMED_SIGNATURE,
                        "Missing certificate id attribute");
            }

            // we have the ocsp response element id, let's find the response
            Element certElem = XmlUtils.getElementById(document, certId);
            if (certElem == null) {
                throw new CodedException(ErrorCodes.X_MALFORMED_SIGNATURE,
                        "Could not find certificate with id " + certId);
            }

            try {
                X509Certificate x509 =
                        CryptoUtils.readCertificate(certElem.getTextContent());

                // we now have the certificate constructed, verify the digest
                if (!verifyDigest(
                        (Element) certRef.getFirstChild(), x509.getEncoded())) {
                    throw new CodedException(ErrorCodes.X_MALFORMED_SIGNATURE,
                            "Certificate (%s) digest does not match",
                            x509.getSerialNumber());
                }

                extraCertificates.add(x509);
            } catch (CertificateException | NoSuchAlgorithmException
                    | IOException | OperatorCreationException  e) {
                throw new CodedException(ErrorCodes.X_MALFORMED_SIGNATURE, e);
            }
        }

        return extraCertificates;
    }

    /**
     * Return list of OCSP responses included in the signature.
     */
    List<OCSPResp> getOcspResponses() {
        // First, we retrieve a list of OCSPRef elements, where each element
        // references the OCSP response and contains information
        // about the response, such as the responder id etc.
        // For each OCSPRef element, we get the OCSP response referenced by
        // the URI and validate the digest.
        NodeList ocspRefs = getOcspRefElements(objectContainer.getElement());
        if (ocspRefs == null || ocspRefs.getLength() == 0) {
            throw new CodedException(ErrorCodes.X_MALFORMED_SIGNATURE,
                    "Could not get any OCSP reference elements from signature");
        }

        List<OCSPResp> ocspResponses = new ArrayList<>();
        for (int i = 0; i < ocspRefs.getLength(); i++) {
            Element ocspIdentifier = (Element) ocspRefs.item(i).getFirstChild();
            Element digAlgAndValue = (Element) ocspRefs.item(i).getLastChild();

            String ocspId = ocspIdentifier.getAttribute(URI_ATTRIBUTE);
            if (ocspId == null || ocspId.isEmpty()) {
                throw new CodedException(ErrorCodes.X_MALFORMED_SIGNATURE,
                        "Missing ocsp id attribute");
            }

            // we have the ocsp response element id, let's find the response
            Element ocspResponseElem = XmlUtils.getElementById(document, ocspId);
            if (ocspResponseElem == null) {
                throw new CodedException(ErrorCodes.X_MALFORMED_SIGNATURE,
                        "Could not find OCSP response with id " + ocspId);
            }

            // we have the ocsp response in base64 form, attempt to parse it
            String base64 = ocspResponseElem.getTextContent();
            try {
                OCSPResp ocspResponse =
                        new OCSPResp(CryptoUtils.decodeBase64(base64));

                // response was constructed successfully, verify the digest
                if (!verifyDigest(digAlgAndValue, ocspResponse.getEncoded())) {
                    throw new CodedException(ErrorCodes.X_MALFORMED_SIGNATURE,
                            "OCSP response digest does not match");
                }

                ocspResponses.add(ocspResponse);
            } catch (IOException| NoSuchAlgorithmException
                    | OperatorCreationException e) {
                throw new CodedException(ErrorCodes.X_MALFORMED_SIGNATURE, e);
            }
        }

        return ocspResponses;
    }

    /**
     * Reads the signature element from the document.
     */
    private void readSignature() throws Exception {
        Element signatureElement = getFirstElementByTagName(document,
                dsElement(Constants._TAG_SIGNATURE));
        xmlSignature = new XMLSignature(signatureElement, BASE_URI);
    }

    /**
     * Reads the object container element from the document.
     */
    private void readObjectContainer() throws Exception {
        Element objectElement = getFirstElementByTagName(document,
                dsElement(Constants._TAG_OBJECT));
        objectContainer =  new ObjectContainer(objectElement, BASE_URI);
    }

}
