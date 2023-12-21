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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.messagelog.SoapLogMessage;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.XmlUtils;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for SOAP message body manipulation
 */
@Slf4j
public class MessageBodyManipulatorTest {

    public static final String QUERY_DIR = "../../../proxy/src/test/queries/";

    @Getter
    @Setter
    private class TestableMessageBodyManipulator extends MessageBodyManipulator {
        TestableMessageBodyManipulator(boolean globalBodyLogging,
                                           Collection<ClientId> localOverrides,
                                           Collection<ClientId> remoteOverrides) {
            setConfigurator(new Configurator() {
                @Override
                public Collection<ClientId> getLocalProducerOverrides() {
                    return localOverrides;
                }
                @Override
                public Collection<ClientId> getRemoteProducerOverrides() {
                    return remoteOverrides;
                }
                @Override
                public boolean isMessageBodyLoggingEnabled() {
                    return globalBodyLogging;
                }
            });
        }
        TestableMessageBodyManipulator(boolean globalBodyLogging) {
            this (globalBodyLogging, new ArrayList<ClientId>(), new ArrayList<ClientId>());
        }
    }

    /**
     * Create request SOAP message from file
     * @param fileName input file
     * @return soap message
     * @throws Exception when error occurs
     */
    public static SoapMessageImpl createRequest(String fileName)
            throws Exception {
        Soap message = createSoapMessage(fileName);
        if (!(message instanceof SoapMessageImpl)) {
            throw new RuntimeException(
                    "Got " + message.getClass() + " instead of SoapMessage");
        }

        if (((SoapMessageImpl) message).isResponse()) {
            throw new RuntimeException("Got response instead of request");
        }

        return (SoapMessageImpl) message;
    }

    /**
     * Create response SOAP message from file
     * @param fileName input file
     * @return soap message
     * @throws Exception when error occurs
     */
    public static SoapMessageImpl createResponse(String fileName)
            throws Exception {
        Soap message = createSoapMessage(fileName);
        if (!(message instanceof SoapMessageImpl)) {
            throw new RuntimeException(
                    "Got " + message.getClass() + " instead of SoapResponse");
        }

        if (((SoapMessageImpl) message).isRequest()) {
            throw new RuntimeException("Got request instead of response");
        }

        return (SoapMessageImpl) message;
    }

    /**
     * Create SOAP message from file
     * @param fileName input file
     * @return soap message
     * @throws Exception when error occurs
     */
    public static Soap createSoapMessage(String fileName)
            throws Exception {
        return new SoapParserImpl().parse(MimeTypes.TEXT_XML_UTF8, newQueryInputStream(fileName));
    }

    /**
     * Create new query input stream
     * @param fileName input file
     * @return FileInputStream
     * @throws Exception when error occurs
     */
    public static FileInputStream newQueryInputStream(String fileName)
            throws Exception {
        return new FileInputStream(QUERY_DIR + fileName);
    }

    /**
     * Test SOAP message body removal
     * @throws Exception when error occurs
     */
    @Test
    public void removingBody() throws Exception {
        SoapMessageImpl query = createRequest("simple.query");
        final boolean clientSide = true;
        final boolean serverSide = false;
        final String requestName = "testQuery";
        final String responseName = requestName + "Response";
        final boolean keepBody = true;
        final boolean removeBody = false;

        assertNodeEmptinessAfterManipulation(query, clientSide, requestName, keepBody);
        assertNodeEmptinessAfterManipulation(query, clientSide, requestName, removeBody);

        SoapMessageImpl answer = createResponse("simple.answer");

        assertNodeEmptinessAfterManipulation(answer, clientSide, responseName, keepBody);
        assertNodeEmptinessAfterManipulation(answer, clientSide, responseName, removeBody);

        // and test server side as well
        assertNodeEmptinessAfterManipulation(query, serverSide, requestName, keepBody);
        assertNodeEmptinessAfterManipulation(query, serverSide, requestName, removeBody);
        assertNodeEmptinessAfterManipulation(answer, serverSide, responseName, keepBody);
        assertNodeEmptinessAfterManipulation(answer, serverSide, responseName, removeBody);
    }

    /**
     * Check that when we remove body, the correct body element is empty (no attributes or children) - and
     * that when we do not remove body, it is not empty
     */
    private void assertNodeEmptinessAfterManipulation(SoapMessageImpl query,
                                                     boolean clientSide,
                                                     String elementName,
                                                     boolean keepBody) throws Exception {
        String loggableMessage = new TestableMessageBodyManipulator(keepBody)
                .getLoggableMessageText(new SoapLogMessage(query, null, clientSide));
        log.debug("loggable message with body"
                + (keepBody ? " intact: " : " removed: ")
                + loggableMessage);
        Node body = getSingleNodeFromXml(loggableMessage, elementName);
        assertTrue(isEmptyNode(body) != keepBody);
    }

    private boolean isEmptyNode(Node n) {
        return n.getChildNodes().getLength() == 0 && n.getAttributes().getLength() == 0;
    }

    private Node getSingleNodeFromXml(String xml,
                                      String tagName) throws IOException, SAXException, ParserConfigurationException {
        Document d = parseXml(xml);

        NodeList nl = d.getElementsByTagNameNS("*", tagName);
        log.debug("nodes: " + nl + ", size=" + nl.getLength());
        assertTrue(nl.getLength() == 1);
        return nl.item(0);
    }

    private Document parseXml(String xml) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory dbf = XmlUtils.createDocumentBuilderFactory();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Test client id search
     * @throws Exception when error occurs
     */
    @Test
    public void clientIdSearching() throws Exception {
        MessageBodyManipulator manipulator = new MessageBodyManipulator();
        ClientId.Conf ss1 = ClientId.Conf.create("instance", "memberclass", "membercode", "ss1");
        ClientId.Conf cmember = ClientId.Conf.create("instance", "memberclass", "membercode", null);
        ClientId.Conf ss2 = ClientId.Conf.create("instance", "memberclass", "membercode", "ss2");
        ClientId.Conf cmember2 = ClientId.Conf.create("instance", "memberclass", "membercode2", null);
        List<ClientId.Conf> coll1 = Arrays.asList(ss1, cmember);

        assertTrue(manipulator.isClientInCollection(ss1, coll1));
        assertTrue(manipulator.isClientInCollection(
                ClientId.Conf.create("instance", "memberclass", "membercode", "ss1"),
                coll1));
        assertFalse(manipulator.isClientInCollection(ss2, coll1));
        assertTrue(manipulator.isClientInCollection(cmember, coll1));
        assertFalse(manipulator.isClientInCollection(cmember2, coll1));
        assertFalse(manipulator.isClientInCollection(
                ClientId.Conf.create("-", "memberclass", "membercode", "ss1"),
                coll1));

        // subsystem does not match to subsystem-less member
        assertFalse(manipulator.isClientInCollection(ss1, Arrays.asList(cmember)));

    }
}
