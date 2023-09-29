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

import org.apache.xml.security.signature.Manifest;
import org.apache.xml.security.utils.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

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
     * @param rnd random string
     * @param hashes list of references
     */
    public SignatureManifest(String rnd, List<MessagePart> hashes) {
        this.rnd = rnd;

        for (MessagePart hash : hashes) {
            addReference(hash);
        }
    }

    /**
     * Creates a new empty manifest.
     * @param base original base Manifest element
     */
    public SignatureManifest(Manifest base) {
        this.rnd = null;
        this.base = base;
    }

    /**
     * @return the original base Manifest element or null, if not set.
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
    public void addReference(String name, String hashMethod, byte[] hashValue) {
        addReference(new MessagePart(name, hashMethod, hashValue, null));
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
     * @throws Exception if errors occur when creating the DOM elements
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
     * @throws Exception if errors occur when creating the DOM elements
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
                ph.getData()));

        return referenceElement;
    }

    /**
     * Creates and returns a xades:ReferenceInfo element.
     * @param doc the document used to create DOM elements
     * @param hashMethod method used to compute the hash value
     * @param hashValue hash value to place in the element
     * @return the XML element
     * @throws Exception if errors occur when creating the DOM elements
     */
    public static Element createReferenceInfoElement(Document doc,
            String hashMethod, byte[] hashValue) throws Exception {
        Element referenceElement = doc.createElement(Helper.PREFIX_XADES
                + Helper.REFERENCE_INFO_TAG);

        referenceElement.appendChild(Helper.createDigestMethodElement(doc,
                hashMethod));
        referenceElement.appendChild(Helper.createDigestValueElement(doc,
                hashValue));

        return referenceElement;
    }
}
