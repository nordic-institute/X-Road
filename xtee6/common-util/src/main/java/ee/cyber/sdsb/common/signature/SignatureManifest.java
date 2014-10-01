package ee.cyber.sdsb.common.signature;

import java.util.ArrayList;
import java.util.List;

import org.apache.xml.security.signature.Manifest;
import org.apache.xml.security.utils.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.ErrorCodes;

/**
 * Encapsulates the functionality of the manifest element. It is a collection
 * of references (PartHash) to data objects and their corresponding hash values.
 */
public class SignatureManifest {

    private final String rnd;

    private final List<MessagePart> references = new ArrayList<>();

    private Manifest base;

    /**
     * Creates a new empty manifest.
     */
    public SignatureManifest() {
        this.rnd = null;
    }

    /**
     * Creates a new manifest from a given random and list of references.
     */
    public SignatureManifest(String rnd, List<MessagePart> hashes) {
        this.rnd = rnd;

        for (MessagePart hash : hashes) {
            addReference(hash);
        }
    }

    /**
     * Creates a new empty manifest.
     */
    public SignatureManifest(Manifest base) {
        this.rnd = null;
        this.base = base;
    }

    /**
     * Returns the original base manifest element or null, if not set.
     */
    public Manifest getBase() {
        return base;
    }

    /**
     * Adds a reference to the manifest
     * @param reference the reference to add
     */
    public void addReference(MessagePart reference) {
        references.add(reference);
    }

    /**
     * Adds a new reference to the manifest
     * @param name the URI of the data object
     * @param hashMethod the hash method
     * @param hashValue the hash value (digest value)
     */
    public void addReference(String name, String hashMethod, String hashValue) {
        addReference(new MessagePart(name, hashMethod, hashValue));
    }

    /**
     * @return all the references contained in this manifest
     */
    public List<MessagePart> getReferences() {
        return references;
    }

    /**
     * Creates new document, appends the manifest XML element (created by
     * createXmlElement(2)) to it and returns the manifest element.
     * @param id the id of the manifest
     * @return the XML element
     */
    public Element createXmlElement(String id) throws Exception {
        Document doc = Helper.createDocument();
        Element el = createXmlElement(doc, id);
        doc.getDocumentElement().appendChild(el);
        return el;
    }

    /**
     * Returns the XML element for this manifest.
     * @param doc the document used to create DOM elements
     * @param id the id of the manifest
     * @return the XML element
     */
    public Element createXmlElement(Document doc, String id) throws Exception {
        Element manifestElement = doc.createElement(
                Helper.PREFIX_DS + Constants._TAG_MANIFEST);
        manifestElement.setAttribute(Constants._ATT_ID, id);
        for (MessagePart r : references) {
            manifestElement.appendChild(createReference(doc, r));
        }

        return manifestElement;
    }

    /**
     * Verifies this manifest against another manifest. For each reference
     * contained in this manifest, finds the corresponding reference in the
     * other manifest and compares the references.
     * @param anotherManifest the other manifest to verify against
     * @throws Exception when a reference does not exist in the other manifest
     *                   or if the references do not verify against each other
     */
    public void verifyAgainst(SignatureManifest anotherManifest)
            throws Exception {
        for (MessagePart thisRef : references) {
            MessagePart otherRef = anotherManifest.getReference(thisRef.getName());
            if (otherRef == null) {
                throw new CodedException(ErrorCodes.X_MALFORMED_SIGNATURE,
                        "Reference for URI '" + thisRef.getName()
                        + "' does not exist");
            }

            if (!thisRef.equals(otherRef)) {
                throw new CodedException(ErrorCodes.X_INVALID_REFERENCE,
                        "Failed to verify reference " + thisRef + " against "
                                + otherRef);
            }
        }
    }

    private MessagePart getReference(String uri) {
        for (MessagePart ph : references) {
            if (ph.getName().endsWith(uri)) {
                return ph;
            }
        }

        return null;
    }

    private String rndName(String name) {
        if (rnd != null) {
            return this.rnd + "-" + name;
        }

        return name;
    }

    private Element createReference(Document doc, MessagePart ph)
            throws Exception {
        Element referenceElement = doc.createElement(
                Helper.PREFIX_DS + Constants._TAG_REFERENCE);
        referenceElement.setAttribute(Constants._ATT_URI,
                rndName(ph.getName()));

        referenceElement.appendChild(Helper.createDigestMethodElement(doc,
                ph.getHashAlgoId()));
        referenceElement.appendChild(Helper.createDigestValueElement(doc,
                ph.getBase64Data()));

        return referenceElement;
    }

    /**
     * Creates and returns a xades:ReferenceInfo element.
     */
    public static Element createReferenceInfoElement(Document doc,
            String hashMethod, String hashValue) throws Exception {
        Element referenceElement = doc.createElement(Helper.PREFIX_XADES
                + Helper.REFERENCE_INFO_TAG);

        referenceElement.appendChild(Helper.createDigestMethodElement(doc,
                hashMethod));
        referenceElement.appendChild(Helper.createDigestValueElement(doc,
                hashValue));

        return referenceElement;
    }
}
