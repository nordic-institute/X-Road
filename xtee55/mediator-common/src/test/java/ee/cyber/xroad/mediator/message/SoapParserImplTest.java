package ee.cyber.xroad.mediator.message;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.http.MimeTypes;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.xroad.mediator.TestResources;
import ee.cyber.xroad.mediator.util.MediatorUtils;
import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParser;

import static ee.ria.xroad.common.ErrorCodes.*;
import static org.junit.Assert.*;

/**
 * Tests to verify correct SOAP parser behavior.
 */
public class SoapParserImplTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Test to ensure a X-Road 6.0 request is read correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readXroadRequestMessage() throws Exception {
        SoapMessage message = parseSoapMessage("xroad-simple.request");
        assertTrue(MediatorUtils.isV6XRoadSoapMessage(message));
        assertTrue(message.isRequest());
        SoapMessageImpl xroadMessage = (SoapMessageImpl) message;
        assertTrue(xroadMessage.getHeader() instanceof XroadSoapHeader);
    }

    /**
     * Test to ensure a X-Road 6.0 response is read correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readXroadResponseMessage() throws Exception {
        SoapMessage message = parseSoapMessage("xroad-simple.response");
        assertTrue(MediatorUtils.isV6XRoadSoapMessage(message));
        assertTrue(message.isResponse());
    }

    /**
     * Test to ensure a X-Road 5.0 request is read correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readV5XRoadRequestMessage() throws Exception {
        String[] files = {"v5xroad-simple.request", "v5xroad-simple2.request",
            "v5xroad-simple3.request", "v5xroad-simple4.request",
            "v5xroad-simple5.request"};
        for (String file : files) {
            SoapMessage message = parseSoapMessage(file);
            assertTrue(MediatorUtils.isV5XRoadSoapMessage(message));
            assertTrue(message.isRequest());

            V5XRoadSoapMessageImpl v5xroadSoap = (V5XRoadSoapMessageImpl) message;
            assertFalse(v5xroadSoap.isRpcEncoded());
            assertEquals("consumer", v5xroadSoap.getConsumer());
            assertEquals("producer", v5xroadSoap.getProducer());
            assertEquals("producer.testQuery.v1", v5xroadSoap.getService());
            assertEquals("EE37702211234", v5xroadSoap.getUserId());
            assertEquals("1234567890", v5xroadSoap.getQueryId());
        }
    }

    /**
     * Test to ensure a X-Road 5.0 response is read correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readV5XRoadResponseMessage() throws Exception {
        SoapMessage message = parseSoapMessage("v5xroad-simple.response");
        assertTrue(MediatorUtils.isV5XRoadSoapMessage(message));
        assertTrue(message.isResponse());

        V5XRoadSoapMessageImpl v5xroadSoap = (V5XRoadSoapMessageImpl) message;
        assertFalse(v5xroadSoap.isRpcEncoded());
        assertEquals("consumer", v5xroadSoap.getConsumer());
        assertEquals("producer", v5xroadSoap.getProducer());
        assertEquals("producer.testQuery.v1", v5xroadSoap.getService());
        assertEquals("EE37702211234", v5xroadSoap.getUserId());
        assertEquals("1234567890", v5xroadSoap.getQueryId());
    }

    /**
     * Test to ensure a X-Road 5.0 requests with EE and EU namespaces are read correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readXRoadRequestsEEAndEUNamespace() throws Exception {
        String[] files = {"v5xroad-ee.request", "v5xroad-eu.request"};
        for (String file : files) {
            SoapMessage message = parseSoapMessage(file);
            assertTrue(MediatorUtils.isV5XRoadSoapMessage(message));
            assertTrue(message.isRequest());

            V5XRoadSoapMessageImpl v5xroadSoap = (V5XRoadSoapMessageImpl) message;
            assertFalse(v5xroadSoap.isRpcEncoded());
            assertEquals("consumer", v5xroadSoap.getConsumer());
            assertEquals("producer", v5xroadSoap.getProducer());
            assertEquals("producer.testQuery.v1", v5xroadSoap.getService());
            assertEquals("EE37702211234", v5xroadSoap.getUserId());
            assertEquals("1234567890", v5xroadSoap.getQueryId());
        }
    }

    /**
     * Parses a real test X-Road 5.0 SOAP (RPC/encoded) message.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readTestV5XRoadMessage() throws Exception {
        SoapMessage message = parseSoapMessage("v5xroad-test.request");
        assertTrue(MediatorUtils.isV5XRoadSoapMessage(message));
        assertTrue(message.isRequest());

        V5XRoadSoapMessageImpl v5xroadSoap = (V5XRoadSoapMessageImpl) message;
        assertTrue(v5xroadSoap.isRpcEncoded());
        assertEquals("toll.0123456789", v5xroadSoap.getConsumer());
        assertEquals("andmekogu64", v5xroadSoap.getProducer());
        assertEquals("andmekogu64.testQuery.v1", v5xroadSoap.getService());
        assertEquals("27001010001", v5xroadSoap.getUserId());
        assertEquals("testquery4", v5xroadSoap.getQueryId());
    }

    /**
     * Test to ensure a X-Road 5.0 request without RPC encoding is read correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readV5XRoadWithoutRpcEncoding() throws Exception {
        SoapMessage message = parseSoapMessage("v5xroad-test-rpc.request");
        assertTrue(MediatorUtils.isV5XRoadSoapMessage(message));
        assertTrue(message.isRequest());
        assertTrue(message.isRpcEncoded());
    }

    /**
     * Test to ensure a listProducers meta request and response are read correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readListProducers() throws Exception {
        SoapMessage request = parseSoapMessage("listProducers.request");
        assertTrue(MediatorUtils.isV5XRoadSoapMessage(request));
        assertTrue(request.isRequest());

        SoapMessage response = parseSoapMessage("listProducers.response");
        assertTrue(MediatorUtils.isV5XRoadSoapMessage(response));
        assertTrue(response.isResponse());
    }

    /**
     * Test to ensure a X-Road 6.0 allowedMethods meta request is read correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readXroadAllowedMethodsRequest() throws Exception {
        SoapMessage response = parseSoapMessage("xroad-allowedMethods.request");
        assertTrue(MediatorUtils.isV6XRoadSoapMessage(response));
        assertTrue(response.isRequest());
    }

    /**
     * Test to ensure reading of an unknown request behaves as expected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readUnknownRequestMessage() throws Exception {
        thrown.expectError(X_INVALID_MESSAGE);
        parseSoapMessage("unknown.request");
    }

    /**
     * Test to ensure reading of a request without a header behaves as expected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readNoHeaderRequestMessage() throws Exception {
        thrown.expectError(X_MISSING_HEADER);
        parseSoapMessage("no-header.request");
    }

    /**
     * Test to ensure reading of a request with missing header fields behaves as expected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readXroadMissingFields() throws Exception {
        thrown.expectError(X_MISSING_HEADER_FIELD);
        SoapMessage message = parseSoapMessage("xroad-missing-fields.request");
        assertTrue(MediatorUtils.isV6XRoadSoapMessage(message));
        assertTrue(message.isRequest());
    }

    /**
     * Test to ensure reading a X-Road 5.0 subsystem request behaves as expected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readV5XRoadSubsystem() throws Exception {
        SoapMessage message = parseSoapMessage("v5xroad-subsystem.request");
        assertTrue(MediatorUtils.isV5XRoadSoapMessage(message));
        assertTrue(message.isRequest());

        V5XRoadSoapMessageImpl v5xroadMessage = (V5XRoadSoapMessageImpl) message;
        assertEquals("xrddlGetRandom", v5xroadMessage.getServiceName());
        assertEquals("v1", v5xroadMessage.getServiceVersion());
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
