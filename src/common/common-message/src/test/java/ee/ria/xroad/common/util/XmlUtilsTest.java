/*
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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderAdapter;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import static org.junit.Assert.assertNotEquals;

/**
 * Unit tests for {@link XmlUtils}
 */
public class XmlUtilsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    String testString = "vulnerability";

    File file;

    @Before
    public void setUp() throws IOException {
        // Create temp file used as local resource
        file = folder.newFile("XXE-injection-test.txt");
        PrintWriter pw = new PrintWriter(file);
        pw.write(testString);
        pw.close();
    }

    @Test
    public void createDocumentBuilderFactory() throws
            IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory dbf = XmlUtils.createDocumentBuilderFactory();
        try {
            // Secure parsing throws SAXParseException to prevent injection (depends on DocumentBuilderFactory impl)
            Document document = dbf.newDocumentBuilder().parse(getXXEFileInjectionDocument());

            // If no exception then verify at least that the injection didn't work or else vulnerability was detected
            NodeList nodeList = document.getElementsByTagName("test");
            assertNotEquals(testString, nodeList.item(0).getTextContent());
        } catch (SAXParseException e) {
            // Parsing was secure
        }
    }

    @Test(expected = SAXParseException.class)
    public void createXmlParser() throws SAXException, IOException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(new XMLReaderAdapter());

        // Testing source document with insecure reader
        try {
            reader.parse(new InputSource(getXXEFileInjectionDocument()));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }

        // Testing secure reader
        reader = XmlUtils.createXmlReader();
        reader.setContentHandler(new XMLReaderAdapter());
        // Parsing should throw SAXParseException if reader is secure
        reader.parse(new InputSource(getXXEFileInjectionDocument()));
    }

    private InputStream getXXEFileInjectionDocument() {
        return new ByteArrayInputStream(String.format("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
                        + "<!DOCTYPE test ["
                        + "<!ELEMENT test ANY >"
                        + "<!ENTITY xxe SYSTEM \"file:///%s\" >]><test>&xxe;</test>",
                file.getAbsolutePath()).getBytes());
    }
}
