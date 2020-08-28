/**
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
package ee.ria.xroad.common.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.xml.security.c14n.Canonicalizer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Contains various XML-related utility methods.
 */
@Slf4j
public final class XmlUtils {

    public static final String FEATURE_EXTERNAL_GENERAL_ENTITIES =
            "http://xml.org/sax/features/external-general-entities";
    public static final String FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    public static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities";

    private static final String ELEMENT_NOT_FOUND_WARNING = "Element not found with getElementXPathNS {}";
    private static final int DEFAULT_INDENT = 4;

    private XmlUtils() {
    }

    /**
     * Creates a new document object from the given String
     * @param xml the xml string
     * @return the created document
     * @throws Exception if an error occurs
     */
    public static Document parseDocument(String xml) throws Exception {
        return XmlUtils.parseDocument(IOUtils.toInputStream(xml, StandardCharsets.UTF_8));
    }

    /**
     * Creates a new document object from the given input stream containing the document XML.
     * @param documentXml the input stream containing the XML
     * @return the created document object
     * @throws Exception if an error occurs
     */
    public static Document parseDocument(InputStream documentXml) throws Exception {
        return parseDocument(documentXml, true);
    }

    /**
     * Creates a new document object from the given input stream containing the document XML.
     * @param documentXml the input stream containing the XML
     * @param namespaceAware flag indicating namespace awareness
     * @return the created document object
     * @throws Exception if an error occurs
     */
    public static Document parseDocument(InputStream documentXml, boolean namespaceAware) throws Exception {
        DocumentBuilderFactory dbf = createDocumentBuilderFactory();

        dbf.setNamespaceAware(namespaceAware);
        dbf.setIgnoringComments(true);

        dbf.setValidating(false);

        return dbf.newDocumentBuilder().parse(documentXml);
    }

    /**
     * Returns String representation of the the XML node (document or element).
     * @param node the node
     * @return string representation of the input
     * @throws Exception if an error occurs
     */
    public static String toXml(Node node) throws Exception {
        Source source = new DOMSource(node);
        StringWriter writer = new StringWriter();
        Result result = new StreamResult(writer);

        Transformer t = createTransformerFactory().newTransformer();
        t.transform(source, result);

        return writer.toString();
    }

    /**
     * Returns the first element matching the given tag name.
     * @param doc the document from which to search the element
     * @param tagName the name of the tag to match
     * @return optional containing the element or empty if the element cannot be found
     */
    public static Optional<Element> getFirstElementByTagName(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);

        return nodes.getLength() > 0 ? Optional.ofNullable((Element) nodes.item(0)) : Optional.empty();
    }

    /**
     * Returns an element matching the given xpath expression and using the specified namespace context.
     * @param parent the parent element from which to search
     * @param xpathExpr the xpath expression
     * @param nsCtx the namespace context (can be null)
     * @return the element or null if the element cannot be found or the xpath expression is invalid
     */
    public static Element getElementXPathNS(Element parent, String xpathExpr, NamespaceContext nsCtx) {
        try {
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();

            if (nsCtx != null) {
                xpath.setNamespaceContext(nsCtx);
            }

            return (Element) xpath.evaluate(xpathExpr, parent, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            log.warn(ELEMENT_NOT_FOUND_WARNING, e);

            return null;
        }
    }

    /**
     * Returns a list of elements matching the given xpath expression and using the specified namespace context.
     * @param parent the parent element from which to search
     * @param xpathExpr the xpath expression
     * @param nsCtx the namespace context (can be null)
     * @return the elements or null if the element cannot be found or the xpath expression is invalid
     */
    public static NodeList getElementsXPathNS(Element parent, String xpathExpr, NamespaceContext nsCtx) {
        try {
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();

            if (nsCtx != null) {
                xpath.setNamespaceContext(nsCtx);
            }

            return (NodeList) xpath.evaluate(xpathExpr, parent, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            log.warn(ELEMENT_NOT_FOUND_WARNING, e);

            return null;
        }
    }

    /**
     * Returns the element that has an ID attribute matching the input.
     * The search is performed using XPath evaluation.
     * @param doc the document
     * @param id the id
     * @return the element or null, if the element cannot be found
     */
    public static Element getElementById(Document doc, String id) {
        if (id.startsWith("#")) {
            id = id.substring(1);
        }

        try {
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();

            return (Element) xpath.evaluate("//*[@Id = '" + id + "']", doc, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            log.warn(ELEMENT_NOT_FOUND_WARNING, e);

            return null;
        }
    }

    /**
     * Calculates and return the result of canonicalization of the specified node, using the specified
     * canonicalization method.
     * @param algorithmUri the URI of the canonicalization algorithm that is known to the class Canonicalizer.
     * @param node the node to canonicalize
     * @return the c14n result.
     * @throws Exception if any errors occur
     */
    public static byte[] canonicalize(String algorithmUri, Node node) throws Exception {
        return Canonicalizer.getInstance(algorithmUri).canonicalizeSubtree(node);
    }

    /**
     * Pretty prints the document to string using default charset
     * @param xml the xml document as string
     * @return pretty printed document as String
     * @throws Exception if any errors occur
     */
    public static String prettyPrintXml(String xml) throws Exception {
        return prettyPrintXml(parseDocument(xml));
    }

    /**
     * Pretty prints the document to string using default charset.
     * @param document  the document
     * @return printed document in String form
     * @throws Exception if any errors occur
     */
    public static String prettyPrintXml(Document document) throws Exception {
        return prettyPrintXml(document, "UTF-8", DEFAULT_INDENT);
    }

    /**
     * Pretty prints the document to string using specified charset.
     * @param document the document
     * @param charset the charset
     * @return printed document in String form
     * @throws Exception if any errors occur
     */
    public static String prettyPrintXml(Document document, String charset, int indent) throws Exception {
        StringWriter stringWriter = new StringWriter();
        StreamResult output = new StreamResult(stringWriter);

        Transformer transformer = createTransformerFactory().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, charset);
        if (indent > 0) {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",
                    String.format("%d", indent));
        }
        transformer.transform(new DOMSource(document), output);

        return output.getWriter().toString().trim();
    }

    /**
     * Creates DocumentBuilderFactory and sets the features of the factory
     * @return
     */
    public static DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException e) {
            log.warn("XMLConstants.FEATURE_SECURE_PROCESSING not supported");
        }
        try {
            dbf.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
        } catch (ParserConfigurationException e) {
            log.warn("disallow-doctype-decl not supported");
        }
        try {
            dbf.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        } catch (ParserConfigurationException e) {
            log.warn("external-general-entities not supported");
        }
        try {
            dbf.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
        } catch (ParserConfigurationException e) {
            log.warn("external-parameter-entities not supported");
        }
        return dbf;
    }

    /**
     * Creates XMLReader and sets the features of the reader
     * @return
     * @throws SAXException
     */
    public static XMLReader createXmlReader() throws SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
        reader.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        return reader;
    }

    private static TransformerFactory createTransformerFactory() throws TransformerConfigurationException {
        final TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory;
    }
}
