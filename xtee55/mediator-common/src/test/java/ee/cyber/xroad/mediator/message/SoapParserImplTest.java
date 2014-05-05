package ee.cyber.xroad.mediator.message;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.http.MimeTypes;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.message.Soap;
import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapParser;
import ee.cyber.xroad.mediator.TestResources;
import ee.cyber.xroad.mediator.util.MediatorUtils;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static org.junit.Assert.*;

public class SoapParserImplTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Test
    public void readSdsbRequestMessage() throws Exception {
        SoapMessage message = parseSoapMessage("sdsb-simple.request");
        assertTrue(MediatorUtils.isSdsbSoapMessage(message));
        assertTrue(message.isRequest());
        SoapMessageImpl sdsbMessage = (SoapMessageImpl) message;
        assertTrue(sdsbMessage.getHeader() instanceof SdsbSoapHeader);
    }

    @Test
    public void readSdsbResponseMessage() throws Exception {
        SoapMessage message = parseSoapMessage("sdsb-simple.response");
        assertTrue(MediatorUtils.isSdsbSoapMessage(message));
        assertTrue(message.isResponse());
    }

    @Test
    public void readXroadRequestMessage() throws Exception {
        String[] files = {"xroad-simple.request", "xroad-simple2.request",
            "xroad-simple3.request", "xroad-simple4.request",
            "xroad-simple5.request"};
        for (String file : files) {
            SoapMessage message = parseSoapMessage(file);
            assertTrue(MediatorUtils.isXroadSoapMessage(message));
            assertTrue(message.isRequest());

            XRoadSoapMessageImpl xroadSoap = (XRoadSoapMessageImpl) message;
            assertFalse(xroadSoap.isRpcEncoded());
            assertEquals("consumer", xroadSoap.getConsumer());
            assertEquals("producer", xroadSoap.getProducer());
            assertEquals("producer.testQuery.v1", xroadSoap.getService());
            assertEquals("EE37702211234", xroadSoap.getUserId());
            assertEquals("1234567890", xroadSoap.getQueryId());
        }
    }

    @Test
    public void readXroadResponseMessage() throws Exception {
        SoapMessage message = parseSoapMessage("xroad-simple.response");
        assertTrue(MediatorUtils.isXroadSoapMessage(message));
        assertTrue(message.isResponse());

        XRoadSoapMessageImpl xroadSoap = (XRoadSoapMessageImpl) message;
        assertFalse(xroadSoap.isRpcEncoded());
        assertEquals("consumer", xroadSoap.getConsumer());
        assertEquals("producer", xroadSoap.getProducer());
        assertEquals("producer.testQuery.v1", xroadSoap.getService());
        assertEquals("EE37702211234", xroadSoap.getUserId());
        assertEquals("1234567890", xroadSoap.getQueryId());
    }

    @Test
    public void readXRoadRequestsEEAndEUNamespace() throws Exception {
        String[] files = {"xroad-ee.request", "xroad-eu.request"};
        for (String file : files) {
            SoapMessage message = parseSoapMessage(file);
            assertTrue(MediatorUtils.isXroadSoapMessage(message));
            assertTrue(message.isRequest());

            XRoadSoapMessageImpl xroadSoap = (XRoadSoapMessageImpl) message;
            assertFalse(xroadSoap.isRpcEncoded());
            assertEquals("consumer", xroadSoap.getConsumer());
            assertEquals("producer", xroadSoap.getProducer());
            assertEquals("producer.testQuery.v1", xroadSoap.getService());
            assertEquals("EE37702211234", xroadSoap.getUserId());
            assertEquals("1234567890", xroadSoap.getQueryId());
        }
    }

    /**
     * Parses a real test X-Road 5.0 SOAP (RPC/encoded) message.
     */
    @Test
    public void readTestXroadMessage() throws Exception {
        SoapMessage message = parseSoapMessage("xroad-test.request");
        assertTrue(MediatorUtils.isXroadSoapMessage(message));
        assertTrue(message.isRequest());

        XRoadSoapMessageImpl xroadSoap = (XRoadSoapMessageImpl) message;
        assertTrue(xroadSoap.isRpcEncoded());
        assertEquals("toll.0123456789", xroadSoap.getConsumer());
        assertEquals("andmekogu64", xroadSoap.getProducer());
        assertEquals("andmekogu64.testQuery.v1", xroadSoap.getService());
        assertEquals("27001010001", xroadSoap.getUserId());
        assertEquals("testquery4", xroadSoap.getQueryId());
    }

    @Test
    public void readXroadWithoutRpcEncoding() throws Exception {
        SoapMessage message = parseSoapMessage("xroad-test-rpc.request");
        assertTrue(MediatorUtils.isXroadSoapMessage(message));
        assertTrue(message.isRequest());
        assertTrue(message.isRpcEncoded());
    }

    @Test
    public void readListProducers() throws Exception {
        SoapMessage request = parseSoapMessage("listProducers.request");
        assertTrue(MediatorUtils.isXroadSoapMessage(request));
        assertTrue(request.isRequest());

        SoapMessage response = parseSoapMessage("listProducers.response");
        assertTrue(MediatorUtils.isXroadSoapMessage(response));
        assertTrue(response.isResponse());
    }

    @Test
    public void readMetaRequests() throws Exception {
        SoapMessage request = parseSoapMessage("testSystem.request");
        assertTrue(MediatorUtils.isXroadSoapMessage(request));
        assertTrue(request.isRequest());
    }

    @Test
    public void readListMethodsResponse() throws Exception {
        SoapMessage response = parseSoapMessage("listMethods.response");
        assertTrue(MediatorUtils.isXroadSoapMessage(response));
        assertTrue(response.isResponse());
    }

    @Test
    public void readUnknownRequestMessage() throws Exception {
        thrown.expectError(X_INVALID_MESSAGE);
        parseSoapMessage("unknown.request");
    }

    @Test
    public void readNoHeaderRequestMessage() throws Exception {
        thrown.expectError(X_MISSING_HEADER);
        parseSoapMessage("no-header.request");
    }

    @Test
    public void readSdsbMissingFields() throws Exception {
        thrown.expectError(X_MISSING_HEADER_FIELD);
        SoapMessage message = parseSoapMessage("sdsb-missing-fields.request");
        assertTrue(MediatorUtils.isSdsbSoapMessage(message));
        assertTrue(message.isRequest());
    }

    private static SoapMessage parseSoapMessage(String fileName)
            throws Exception {
        Soap soap = parseMessage(fileName);
        assertNotNull(soap);
        assertTrue(soap instanceof SoapMessage);
        return (SoapMessage) soap;
    }

    private static Soap parseMessage(String fileName) throws Exception {
        return parseMessage(TestResources.get(fileName));
    }

    private static Soap parseMessage(InputStream is) throws Exception {
        assertNotNull("InputStream must not be null", is);

        SoapParser parser = new SoapParserImpl();
        return parser.parse(MimeTypes.TEXT_XML, StandardCharsets.UTF_8.name(),
                is);
    }

}
