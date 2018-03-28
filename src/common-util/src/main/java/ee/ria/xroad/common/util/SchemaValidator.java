/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.common.CodedException;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;

/**
 * Base class for schema-based validators.
 */
@Slf4j
public abstract class SchemaValidator {

    protected static Schema createSchema(String fileName) {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        try {
            URL schemaLocation = ResourceUtils.getClasspathResource(fileName);

            return factory.newSchema(schemaLocation);
        } catch (SAXException e) {
            log.error("Creating schema from file '{}' failed", fileName, e);

            throw new RuntimeException("Unable to create schema validator", e);
        }
    }

    protected static void validate(Schema schema, Source source, String errorCode) throws Exception {
        if (schema == null) {
            throw new IllegalStateException("Schema is not initialized");
        }

        try {
            validateWithSaxParser(source, schema);
        } catch (SAXException e) {
            throw new CodedException(errorCode, e);
        }
    }

    private static void validateWithSaxParser(Source source, Schema schema)
            throws SAXException, ParserConfigurationException, IOException {
        SAXParserFactory spf = createSafeSaxParserFactory();
        spf.setSchema(schema);

        SAXParser parser = spf.newSAXParser();
        InputSource inputSource = toInputSource(source);

        parser.parse(inputSource, new DraconianErrorHandler());
    }

    @SneakyThrows
    private static SAXParserFactory createSafeSaxParserFactory() {
        SAXParserFactory factory = SAXParserFactory.newInstance();

        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(true);
        factory.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        return factory;
    }

    @SneakyThrows
    private static InputSource toInputSource(Source source) {
        if (source instanceof SAXSource || source instanceof StreamSource) {
            return SAXSource.sourceToInputSource(source);
        } else if (source instanceof DOMSource) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Node node = ((DOMSource) source).getNode();

            if (node instanceof Document) {
                node = ((Document) node).getDocumentElement();
            }

            Element domElement = (Element) node;
            elementToStream(domElement, baos);
            InputSource inputSource = new InputSource(source.getSystemId());
            inputSource.setByteStream(new ByteArrayInputStream(baos.toByteArray()));

            return inputSource;
        } else {
            throw new IllegalArgumentException("SchemaValidator does not support " + source.getClass());
        }
    }

    @SneakyThrows
    private static void elementToStream(Element element, OutputStream out) {
        DOMSource source = new DOMSource(element);
        StreamResult result = new StreamResult(out);
        TransformerFactory transFactory = TransformerFactory.newInstance();
        Transformer transformer = transFactory.newTransformer();

        transformer.transform(source, result);
    }

    private static class DraconianErrorHandler extends DefaultHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            log.warn("SchemaValidator warning: {}", exception.getMessage());
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
