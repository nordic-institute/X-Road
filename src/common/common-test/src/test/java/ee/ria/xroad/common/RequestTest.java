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
package ee.ria.xroad.common;

import ee.ria.xroad.common.Request.RequestTag;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.XmlUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests to verify test requests are created as expected.
 */
@Slf4j
public class RequestTest {
    private static final String TEMPLATES_DIR = "src/main/resources";

    /**
     * Test to ensure bodymass index test request creation is correct.
     *
     * @throws IOException if request template file could not be read
     */
    @Test
    public void shouldCreateBodyMassIndexDoclitRequest() throws IOException {
        // Given
        ClientId client = ClientId.Conf.create(
                "EE",
                "riigiasutus",
                "consumer",
                "subClient");

        ServiceId service = ServiceId.Conf.create(
                "EE",
                "riigiasutus",
                "producer",
                "subService",
                "bodyMassIndex",
                "v1");

        String id = "1234567890";

        List<RequestTag> content = Arrays.asList(
                new RequestTag("height", Integer.toString(180)),
                new RequestTag("weight", Double.toString(80.1)));

        String template = FileUtils
                .readFileToString(new File(TEMPLATES_DIR + File.separator + "xroadDoclitRequest.st"));

        Request request =
                new Request(template, client, service, id, content);

        // When
        String xmlFromRequest = request.toXml();
        log.debug("XML from request: {}", xmlFromRequest);

        // Then
        String expectedRequest = FileUtils.readFileToString(new File("src/test/resources/xroadDoclit2.request"));

        assertXml(xmlFromRequest, expectedRequest);
    }

    /**
     * Test to ensure bodymass index test RPC request (with version) creation is correct.
     *
     * @throws IOException if request template file could not be read
     */
    @Test
    public void shouldCreateBodyMassIndexRpcRequestWithVersion()
            throws IOException {
        // Given
        ClientId client = ClientId.Conf.create(
                "EE",
                "riigiasutus",
                "consumer",
                "subClient");

        ServiceId service = ServiceId.Conf.create(
                "EE",
                "riigiasutus",
                "producer",
                "subService",
                "bodyMassIndex", "v1");

        String id = "1234567890";

        List<RequestTag> content = Arrays.asList(
                new RequestTag("height", Integer.toString(180)),
                new RequestTag("weight", Double.toString(80.1)));

        String template = FileUtils.readFileToString(new File(
                TEMPLATES_DIR + File.separator + "v5DoclitRequest.st"));

        Request request =
                new Request(template, client, service, id, content);

        // When
        String xmlFromRequest = request.toXml();
        log.debug("XML from request: {}", xmlFromRequest);

        // Then
        String expectedRequest = FileUtils.readFileToString(new File(
                "src/test/resources/v5DoclitWithVersion.request"));
        assertXml(xmlFromRequest, expectedRequest);
    }

    /**
     * Test to ensure bodymass index test RPC request (without version) creation is correct.
     *
     * @throws IOException if request template file could not be read
     */
    @Test
    public void shouldCreateBodyMassIndexRpcRequestWithoutVersion()
            throws IOException {
        // Given
        ClientId client = ClientId.Conf.create(
                "EE",
                "riigiasutus",
                "consumer",
                "subClient");

        ServiceId service = ServiceId.Conf.create(
                "EE",
                "riigiasutus",
                "producer",
                "subService",
                "bodyMassIndex");

        String id = "1234567890";

        List<RequestTag> content = Arrays.asList(
                new RequestTag("height", Integer.toString(180)),
                new RequestTag("weight", Double.toString(80.1)));

        String template = FileUtils.readFileToString(new File(
                TEMPLATES_DIR + File.separator + "v5DoclitRequest.st"));

        Request request =
                new Request(template, client, service, id, content);

        // When
        String xmlFromRequest = request.toXml();
        log.debug("XML from request: {}", xmlFromRequest);

        // Then
        String expectedRequest = FileUtils.readFileToString(new File(
                "src/test/resources/v5DoclitWithoutVersion.request"));
        assertXml(xmlFromRequest, expectedRequest);
    }

    private void assertXml(String xml, String expectedXml) {
        try {
            assertTrue(normalize(XmlUtils.parseDocument(xml))
                    .isEqualNode(normalize(XmlUtils.parseDocument(expectedXml))));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    private Document normalize(Document doc) throws TransformerException {
        doc.normalize();
        final TransformerFactory factory = TransformerFactory.newInstance();
        final Transformer transformer = factory.newTransformer(new DOMSource(XSLT));
        DOMResult result = new DOMResult();
        transformer.transform(new DOMSource(doc), result);
        return (Document) result.getNode();
    }

    private static final Document XSLT;

    static {
        try {
            XSLT = XmlUtils.parseDocument(
                    "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                            + " <xsl:strip-space elements=\"*\"/>"
                            + "  <xsl:template match=\"node() | @*\">\n"
                            + "    <xsl:copy>\n"
                            + "      <xsl:apply-templates select=\"node() | @*\"/>\n"
                            + "    </xsl:copy>\n"
                            + "  </xsl:template>\n"
                            + "</xsl:stylesheet>");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
