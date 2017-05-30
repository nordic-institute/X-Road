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
package ee.ria.xroad.common.message;

import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.ExpectedCodedException;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.util.Arrays;
import org.junit.Rule;
import org.junit.Test;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.message.SoapMessageTestUtil.*;
import static ee.ria.xroad.common.message.SoapUtils.getChildElements;
import static org.junit.Assert.*;

/**
 * This class tests the basic functionality (parsing the soap message etc.)
 * of the SoapMessage class.
 */
public class SoapMessageTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Test that reading a normal request message is successful and that
     * header and body are correctly parsed.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void simpleRequest() throws Exception {
        SoapMessageImpl message = createRequest("simple.query");

        ClientId expectedClient =
                ClientId.create("EE", "BUSINESS", "consumer");
        ServiceId expectedService =
                ServiceId.create("EE", "BUSINESS", "producer", null,
                        "testQuery");

        assertTrue(message.isRequest());
        assertEquals(expectedClient, message.getClient());
        assertEquals(expectedService, message.getService());
        assertEquals("EE37702211234", message.getUserId());
        assertEquals("1234567890", message.getQueryId());
    }

    /**
     * Test that reading a normal RPC encoded request message is successful
     * and that header and body are correctly parsed.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void simpleRpcRequest() throws Exception {
        SoapMessageImpl message = createRequest("simple-rpc.query");

        ClientId expectedClient =
                ClientId.create("EE", "BUSINESS", "consumer");
        ServiceId expectedService =
                ServiceId.create("EE", "BUSINESS", "producer", null,
                        "testQuery");

        assertTrue(message.isRpcEncoded());
        assertTrue(message.isRequest());
        assertEquals(expectedClient, message.getClient());
        assertEquals(expectedService, message.getService());
        assertEquals("EE37702211234", message.getUserId());
        assertEquals("1234567890", message.getQueryId());
    }

    /**
     * Test that reading a normal response message is successful and that
     * header and body are correctly parsed.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void simpleResponse() throws Exception {
        SoapMessageImpl message = createResponse("simple.answer");

        ClientId expectedClient =
                ClientId.create("EE", "BUSINESS", "consumer");
        ServiceId expectedService =
                ServiceId.create("EE", "BUSINESS", "producer", null,
                        "testQuery");

        assertTrue(message.isResponse());
        assertEquals(expectedClient, message.getClient());
        assertEquals(expectedService, message.getService());
        assertEquals("EE37702211234", message.getUserId());
        assertEquals("1234567890", message.getQueryId());
    }

    /**
     * Test that reading a normal async request message is successful and that
     * header and body are correctly parsed.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void asyncMessage() throws Exception {
        SoapMessageImpl message = createRequest("async.query");

        ClientId expectedClient =
                ClientId.create("EE", "BUSINESS", "consumer");
        ServiceId expectedService =
                ServiceId.create("EE", "BUSINESS", "producer", null,
                        "getState");

        assertTrue(message.isRequest());
        assertTrue(message.isAsync());
        assertEquals(expectedClient, message.getClient());
        assertEquals(expectedService, message.getService());
        assertEquals("EE:PIN:abc4567", message.getUserId());
        assertEquals("411d6755661409fed365ad8135f8210be07613da",
                message.getQueryId());
    }

    /**
     * Tests that verification fails against schema, if the message is not a
     * valid SOAP message.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void notSoapMessage() throws Exception {
        thrown.expectError(X_MALFORMED_SOAP);
        createSoapMessage("malformed-soap.query");
    }

    /**
     * Tests that missing header is detected on not fault messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void missingHeader() throws Exception {
        thrown.expectError(X_MISSING_HEADER);
        createSoapMessage("no-header.query");
    }

    /**
     * Tests that missing body is detected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void missingBody() throws Exception {
        thrown.expectError(X_MISSING_BODY);
        createSoapMessage("missing-body.query");
    }

    /**
     * Tests that missing required header fields are detected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void missingRequiredHeaderField() throws Exception {
        thrown.expectError(X_MISSING_HEADER_FIELD);
        createSoapMessage("faulty-header.query");
    }

    /**
     * Tests that userId header field is optional.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void optionalUserIdField() throws Exception {
        createSoapMessage("missing-userId.query");
    }

    /**
     * Tests that duplicate header fields are detected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void duplicateHeaderField() throws Exception {
        thrown.expectError(X_DUPLICATE_HEADER_FIELD);
        createSoapMessage("faulty-header2.query");
    }

    /**
     * Tests that body with more than one child elements is detected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void malformedBody() throws Exception {
        thrown.expectError(X_INVALID_BODY);
        createSoapMessage("malformed-body1.query");
    }

    /**
     * Tests that service name mismatch in header and body is detected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void inconsistentHeaders() throws Exception {
        thrown.expectError(X_INCONSISTENT_HEADERS);
        createSoapMessage("inconsistent-headers.query");
    }

    /**
     * Tests that message with invalid content type is detected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void invalidContentType() throws Exception {
        thrown.expectError(X_INVALID_CONTENT_TYPE);
        new SoapParserImpl().parse("text/html", StandardCharsets.UTF_8.name(),
                new FileInputStream(QUERY_DIR + "simple.query"));
    }

    /**
     * Tests that SoapMessage class understands fault messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void faultMessage() throws Exception {
        String soapFaultXml = SoapFault.createFaultXml("foo.bar", "baz", "xxx", "yyy");
        Soap message = new SoapParserImpl().parse(
                new ByteArrayInputStream(soapFaultXml.getBytes()));

        assertTrue(message instanceof SoapFault);

        SoapFault fault = (SoapFault) message;
        assertEquals("foo.bar", fault.getCode());
        assertEquals("baz", fault.getString());
        assertEquals("xxx", fault.getActor());
        assertEquals("yyy", fault.getDetail());
    }

    /**
     * Tests that SoapMessage class understands fault messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void faultMessageWithCustomPrefix() throws Exception {
        String soapFaultXml = SoapFault.createFaultXml(new QName(SoapUtils.NS_SOAPENV, "foo.bar", "SE"),
                "baz", "xxx", "yyy");
        Soap message = new SoapParserImpl().parse(
                new ByteArrayInputStream(soapFaultXml.getBytes()));

        assertTrue(message instanceof SoapFault);

        SoapFault fault = (SoapFault) message;
        final QName code = fault.getCodeAsQName();
        assertEquals("foo.bar", code.getLocalPart());
        assertEquals(SoapUtils.NS_SOAPENV, code.getNamespaceURI());
        assertEquals("baz", fault.getString());
        assertEquals("xxx", fault.getActor());
        assertEquals("yyy", fault.getDetail());
    }

    /**
     * Tests that SoapMessage class understands fault messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void faultMessageWithCustomNamespace() throws Exception {
        final QName customCode = new QName("http://example.org", "foo.bar", "foo");
        String soapFaultXml = SoapFault.createFaultXml(customCode,
                "baz", "xxx", "yyy");
        Soap message = new SoapParserImpl().parse(
                new ByteArrayInputStream(soapFaultXml.getBytes()));

        assertTrue(message instanceof SoapFault);

        SoapFault fault = (SoapFault) message;
        assertEquals(customCode.getLocalPart(), fault.getCodeAsQName().getLocalPart());
        assertEquals(customCode.getNamespaceURI(), fault.getCodeAsQName().getNamespaceURI());
        assertEquals("baz", fault.getString());
        assertEquals("xxx", fault.getActor());
        assertEquals("yyy", fault.getDetail());
    }

    /**
     * Checks that inconsistencies between two messages are detected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void consistentMessages() throws Exception {
        SoapMessageImpl m1 = createRequest("getstate.query");
        SoapMessageImpl m2 = createResponse("getstate.answer");
        SoapUtils.checkConsistency(m1, m2);
    }

    /**
     * Checks that inconsistencies between two messages are detected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void inconsistentMessages() throws Exception {
        SoapMessageImpl m1 = createRequest("simple.query");
        SoapUtils.checkConsistency(m1, m1);

        SoapMessageImpl m2 = createResponse("getstate.answer");
        thrown.expectError(X_INCONSISTENT_HEADERS);
        SoapUtils.checkConsistency(m1, m2);
    }

    /**
     * Checks that a request message can be converted to a response message.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void requestToResponse() throws Exception {
        SoapMessageImpl request = createRequest("simple.query");

        SoapMessageImpl response = SoapUtils.toResponse(request);
        assertTrue(response.isResponse());
    }

    /**
     * Test that request with are quest suffix is correctly converted to a request.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void removeRequestSuffixFromResponse() throws Exception {
        SoapMessageImpl request = createRequest("request-suffix.query");

        SoapMessageImpl response = SoapUtils.toResponse(request);

        QName responseChild = getResponseChild(response);
        assertEquals("testQueryResponse", responseChild.getLocalPart());
    }

    private QName getResponseChild(SoapMessageImpl response) throws SOAPException {
        List<SOAPElement> bodyChildren =
                getChildElements(response.getSoap().getSOAPBody());
        return bodyChildren.get(0).getElementQName();
    }

    /**
     * Tests that we can parse our own created Soap messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void shouldParseBuiltMessage() throws Exception {
        ClientId client = ClientId.create("EE", "BUSINESS", "producer");
        ServiceId service = ServiceId.create("EE", "BUSINESS", "consumer",
                null, "test");
        CentralServiceId centralService =
                CentralServiceId.create("EE", "central");
        String userId = "foobar";
        String queryId = "barbaz";

        SoapMessageImpl built = build(client, service, userId, queryId);
        assertNotNull(built);
        assertEquals(userId, built.getUserId());
        assertEquals(queryId, built.getQueryId());
        assertEquals(client, built.getClient());
        assertEquals(service, built.getService());

        Soap parsedSoap = new SoapParserImpl().parse(
                        new ByteArrayInputStream(built.getBytes()));
        assertTrue(parsedSoap instanceof SoapMessageImpl);

        SoapMessageImpl parsed = (SoapMessageImpl) parsedSoap;
        assertNotNull(parsed);

        SoapUtils.checkConsistency(built, parsed);
        assertEquals(built.isRequest(), parsed.isRequest());

        // Central Service ----------------------------------------------------

        built = build(client, centralService, userId, queryId);
        assertNotNull(built);
        assertEquals(userId, built.getUserId());
        assertEquals(queryId, built.getQueryId());
        assertEquals(client, built.getClient());
        assertEquals(centralService, built.getService());

        parsedSoap = new SoapParserImpl().parse(
                        IOUtils.toInputStream(built.getXml()));
        assertTrue(parsedSoap instanceof SoapMessageImpl);

        parsed = (SoapMessageImpl) parsedSoap;
        assertNotNull(parsed);

        SoapUtils.checkConsistency(built, parsed);
        assertEquals(built.isRequest(), parsed.isRequest());
    }

    /**
     * Tests that we can parse our own created Soap messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void shouldBuildRpcMessage() throws Exception {
        ClientId client = ClientId.create("EE", "BUSINESS", "producer");
        ServiceId service = ServiceId.create("EE", "BUSINESS", "consumer",
                null, "test");
        String userId = "foobar";
        String queryId = "barbaz";

        SoapMessageImpl built = build(true, client, service, userId, queryId);
        assertNotNull(built);
        assertTrue(built.isRpcEncoded());
        assertEquals(userId, built.getUserId());
        assertEquals(queryId, built.getQueryId());
        assertEquals(client, built.getClient());
        assertEquals(service, built.getService());
    }

    /**
     * Tests that missing header field is checked.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void shouldNotBuildWithoutMissingHeaderFields() throws Exception {
        thrown.expectError(X_MISSING_HEADER_FIELD);

        ClientId client = null;
        ServiceId service = ServiceId.create("EE", "BUSINESS", "consumer",
                null, "test");
        String userId = "foobar";
        String queryId = "barbaz";

        build(client, service, userId, queryId);
    }

    /**
     * Test that input message is not re-encoded when getting XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void shouldNotReencodeInputMessage() throws Exception {
        byte[] in = fileToBytes("simple.query");
        byte[] out = messageToBytes(createSoapMessage(in));
        assertTrue(Arrays.areEqual(in, out));
    }

    /**
     * Test that central service query is parsed correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void centralServiceMessage() throws Exception {
        SoapMessageImpl message = createRequest("simple-centralservice.query");

        ClientId expectedClient =
                ClientId.create("EE", "BUSINESS", "consumer");

        CentralServiceId expectedService =
                CentralServiceId.create("EE", "centralservice");

        assertTrue(message.isRequest());
        assertEquals(expectedClient, message.getClient());
        assertEquals(expectedService, message.getCentralService());
        assertEquals("EE37702211234", message.getUserId());
        assertEquals("1234567890", message.getQueryId());
    }

    /**
     * Test that central service query is parsed correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void wrongProtocolVersion() throws Exception {
        thrown.expectError(X_INVALID_PROTOCOL_VERSION);
        createRequest("wrong-version.query");
    }

}
