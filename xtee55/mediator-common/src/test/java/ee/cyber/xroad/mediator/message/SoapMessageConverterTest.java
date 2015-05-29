package ee.cyber.xroad.mediator.message;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Test;

import ee.cyber.xroad.mediator.IdentifierMappingProvider;
import ee.cyber.xroad.mediator.TestResources;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageImpl;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests to verify correct soap message converter behavior.
 */
public class SoapMessageConverterTest {

    /**
     * Test to ensure X-Road 5.0 messages are correctly converted to X-Road 6.0 messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v5XRroadMessageToV6XRoadMessage() throws Exception {
        ClientId expectedClientId = ClientId.create("EE", "X", "foo");
        ServiceId expectedServiceId =
                ServiceId.create("EE", "X", "bar", null , "testQuery", "v1");

        IdentifierMappingProvider mapping =
                mock(IdentifierMappingProvider.class);
        mockIdentifierMapping(mapping,
                new String[] {"consumer", "producer"},
                new ClientId[] {
                        expectedClientId, expectedServiceId.getClientId()});
        mockIdentifierMapping(mapping,
                new ClientId[] {
                        expectedClientId, expectedServiceId.getClientId()},
                new String[] {"consumer", "producer"});

        SoapMessageConverter converter = new SoapMessageConverter(mapping);

        V5XRoadSoapMessageImpl v5xroadMessage =
                readV5XRoadMessage("v5xroad-simple.request");
        assertEquals("producer", v5xroadMessage.getProducer());
        assertEquals("consumer", v5xroadMessage.getConsumer());
        assertEquals("producer.testQuery.v1", v5xroadMessage.getService());
        assertEquals("EE37702211234", v5xroadMessage.getUserId());
        assertEquals("1234567890", v5xroadMessage.getQueryId());
        assertTrue(v5xroadMessage.isAsync());
        assertTrue(v5xroadMessage.getHeader() instanceof V5XRoadDlSoapHeader.EE);

        for (SoapMessageImpl xroadMessage
                : withParsedMessage(converter.xroadSoapMessage(
                        v5xroadMessage, true))) {
            assertNotNull(xroadMessage);
            assertEquals(expectedClientId, xroadMessage.getClient());
            assertEquals(expectedServiceId, xroadMessage.getService());
            assertEquals("EE37702211234", xroadMessage.getUserId());
            assertEquals("1234567890", xroadMessage.getQueryId());
            assertTrue(xroadMessage.isAsync());
            assertTrue(xroadMessage.getHeader() instanceof XroadSoapHeader);

            V5XRoadSoapMessageImpl v5xroadMessage2 =
                    converter.v5XroadSoapMessage(xroadMessage);
            assertEquals("producer", v5xroadMessage2.getProducer());
            assertEquals("consumer", v5xroadMessage2.getConsumer());
            assertEquals("producer.testQuery.v1", v5xroadMessage2.getService());
            assertEquals("EE37702211234", v5xroadMessage2.getUserId());
            assertEquals("1234567890", v5xroadMessage2.getQueryId());
            assertTrue(v5xroadMessage2.isAsync());
            assertTrue(
                    v5xroadMessage2.getHeader() instanceof V5XRoadDlSoapHeader.EE);
        }
    }

    /**
     * Test to ensure X-Road 6.0 messages are correctly converted to X-Road 5.0 messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMessageToV5XRoadMessage() throws Exception {
        ClientId expectedClientId =
                ClientId.create("EE", "BUSINESS", "consumer");
        ServiceId expectedServiceId =
                ServiceId.create("EE", "BUSINESS", "producer", null,
                        "testQuery");

        IdentifierMappingProvider mapping =
                mock(IdentifierMappingProvider.class);
        mockIdentifierMapping(mapping,
                new ClientId[] {expectedClientId,
                        expectedServiceId.getClientId()},
                new String[] {"consumer", "producer"});

        SoapMessageConverter converter = new SoapMessageConverter(mapping);

        SoapMessageImpl xroadMessage = readXroadMessage("xroad-simple.request");
        assertEquals(expectedClientId, xroadMessage.getClient());
        assertEquals(expectedServiceId, xroadMessage.getService());
        assertEquals("EE37702211234", xroadMessage.getUserId());
        assertEquals("1234567890", xroadMessage.getQueryId());
        assertFalse(xroadMessage.isAsync());

        for (V5XRoadSoapMessageImpl v5xroadMessage
                : withParsedMessage(converter.v5XroadSoapMessage(xroadMessage))) {
            assertNotNull(v5xroadMessage);
            assertEquals("consumer", v5xroadMessage.getConsumer());
            assertEquals("producer", v5xroadMessage.getProducer());
            assertEquals("producer.testQuery", v5xroadMessage.getService());
            assertEquals("EE37702211234", v5xroadMessage.getUserId());
            assertEquals("1234567890", v5xroadMessage.getQueryId());
            assertFalse(v5xroadMessage.isAsync());
        }
    }

    /**
     * Test to ensure X-Road 6.0 messages with legacy headers are correctly
     * converted to X-Road 5.0 messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMessageWithLegacyHeaderToV5XRoadMessage() throws Exception {
        ClientId expectedClientId =
                ClientId.create("EE", "BUSINESS", "consumer");
        ServiceId expectedServiceId =
                ServiceId.create("EE", "BUSINESS", "producer", null,
                        "testQuery", "v1");

        IdentifierMappingProvider mapping =
                mock(IdentifierMappingProvider.class);
        mockIdentifierMapping(mapping,
                new ClientId[] {expectedClientId,
                        expectedServiceId.getClientId()},
                new String[] {"consumer", "producer"});
        mockIdentifierMapping(mapping,
                new String[] {"consumer", "producer"},
                new ClientId[] {
                        expectedClientId, expectedServiceId.getClientId()});

        SoapMessageConverter converter = new SoapMessageConverter(mapping);

        SoapMessageImpl xroadMessage =
                readXroadMessage("xroad-simple-legacy.request");
        assertEquals(expectedClientId, xroadMessage.getClient());
        assertEquals(expectedServiceId, xroadMessage.getService());
        assertEquals("EE37702211234", xroadMessage.getUserId());
        assertEquals("1234567890", xroadMessage.getQueryId());
        assertFalse(xroadMessage.isAsync());

        for (V5XRoadSoapMessageImpl v5xroadMessage
                : withParsedMessage(converter.v5XroadSoapMessage(xroadMessage))) {
            assertNotNull(v5xroadMessage);
            assertEquals("consumer", v5xroadMessage.getConsumer());
            assertEquals("producer", v5xroadMessage.getProducer());
            assertEquals("producer.testQuery.v1", v5xroadMessage.getService());
            assertEquals("EE37702211234", v5xroadMessage.getUserId());
            assertEquals("1234567890", v5xroadMessage.getQueryId());
            assertFalse(v5xroadMessage.isAsync());

            SoapMessageImpl xroadMessage2 =
                    converter.xroadSoapMessage(v5xroadMessage, false);
            assertNotNull(xroadMessage2);
            assertEquals(xroadMessage.getClient(), xroadMessage2.getClient());
            assertEquals(xroadMessage.getService(), xroadMessage2.getService());
            assertEquals(xroadMessage.getUserId(), xroadMessage2.getUserId());
            assertEquals(xroadMessage.getQueryId(), xroadMessage2.getQueryId());
            assertTrue(xroadMessage.isAsync() == xroadMessage2.isAsync());
        }
    }

    /**
     * Test to ensure X-Road 6.0 messages with service version are
     * correctly converted to X-Road 5.0 messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMessageWithVersionToV5XRoadMessage() throws Exception {
        ClientId expectedClientId =
                ClientId.create("EE", "BUSINESS", "consumer");
        ServiceId expectedServiceId =
                ServiceId.create("EE", "BUSINESS", "producer", null,
                        "testQuery", "v1");

        IdentifierMappingProvider mapping =
                mock(IdentifierMappingProvider.class);
        mockIdentifierMapping(mapping,
                new ClientId[] {expectedClientId,
                        expectedServiceId.getClientId() },
                new String[] {"consumer", "producer"});

        SoapMessageConverter converter = new SoapMessageConverter(mapping);

        SoapMessageImpl xroadMessage =
                readXroadMessage("xroad-simple-v1.request");
        assertEquals(expectedClientId, xroadMessage.getClient());
        assertEquals(expectedServiceId, xroadMessage.getService());
        assertEquals("EE37702211234", xroadMessage.getUserId());
        assertEquals("1234567890", xroadMessage.getQueryId());

        for (V5XRoadSoapMessageImpl v5xroadMessage
                : withParsedMessage(converter.v5XroadSoapMessage(xroadMessage))) {
            assertNotNull(v5xroadMessage);
            assertEquals("consumer", v5xroadMessage.getConsumer());
            assertEquals("producer", v5xroadMessage.getProducer());
            assertEquals("producer.testQuery.v1", v5xroadMessage.getService());
            assertEquals("EE37702211234", v5xroadMessage.getUserId());
            assertEquals("1234567890", v5xroadMessage.getQueryId());
        }
    }

    /**
     * Test to ensure X-Road 5.0 messages with namespace in header are
     * correctly converted to X-Road 6.0 messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v5xroadMessageWithNsInHeaderToXroadMessage() throws Exception {
        ClientId expectedClientId = ClientId.create("EE", "X", "foo");
        ServiceId expectedServiceId =
                ServiceId.create("EE", "X", "bar", null, "testQuery", "v1");

        IdentifierMappingProvider mapping =
                mock(IdentifierMappingProvider.class);
        mockIdentifierMapping(mapping,
                new String[] {"consumer", "producer"},
                new ClientId[] {
                        expectedClientId, expectedServiceId.getClientId()});

        SoapMessageConverter converter = new SoapMessageConverter(mapping);

        String[] files = {"v5xroad-simple2.request",
                "v5xroad-simple3.request", "v5xroad-simple4.request"};
        for (String file : files) {
            V5XRoadSoapMessageImpl v5xroadMessage = readV5XRoadMessage(file);
            assertEquals("producer", v5xroadMessage.getProducer());
            assertEquals("consumer", v5xroadMessage.getConsumer());
            assertEquals("producer.testQuery.v1", v5xroadMessage.getService());
            assertEquals("EE37702211234", v5xroadMessage.getUserId());
            assertEquals("1234567890", v5xroadMessage.getQueryId());

            for (SoapMessageImpl xroadMessage
                    : withParsedMessage(
                            converter.xroadSoapMessage(v5xroadMessage, false))) {
                assertNotNull(xroadMessage);
                assertEquals(expectedClientId, xroadMessage.getClient());
                assertEquals(expectedServiceId, xroadMessage.getService());
                assertEquals("EE37702211234", xroadMessage.getUserId());
                assertEquals("1234567890", xroadMessage.getQueryId());
            }
        }
    }

    /**
     * Test to ensure RPC encoded X-Road 6.0 messages are correctly converted to X-Road 5.0 messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadRpcEncodedMessageToV5XRoadMessage() throws Exception {
        ClientId expectedClientId =
                ClientId.create("EE", "BUSINESS", "consumer");
        ServiceId expectedServiceId =
                ServiceId.create("EE", "BUSINESS", "producer", null,
                        "testQuery");

        IdentifierMappingProvider mapping =
                mock(IdentifierMappingProvider.class);
        mockIdentifierMapping(mapping,
                new ClientId[] {expectedClientId,
                        expectedServiceId.getClientId() },
                new String[] {"consumer", "producer"});

        SoapMessageConverter converter = new SoapMessageConverter(mapping);

        SoapMessageImpl xroadMessage =
                readXroadMessage("xroad-rpc-simple.request");
        assertTrue(xroadMessage.isRpcEncoded());
        assertEquals(expectedClientId, xroadMessage.getClient());
        assertEquals(expectedServiceId, xroadMessage.getService());
        assertEquals("EE37702211234", xroadMessage.getUserId());
        assertEquals("1234567890", xroadMessage.getQueryId());

        for (V5XRoadSoapMessageImpl v5xroadMessage
                : withParsedMessage(converter.v5XroadSoapMessage(xroadMessage))) {
            assertNotNull(v5xroadMessage);
            assertTrue(v5xroadMessage.isRpcEncoded());
            assertEquals("consumer", v5xroadMessage.getConsumer());
            assertEquals("producer", v5xroadMessage.getProducer());
            assertEquals("producer.testQuery", v5xroadMessage.getService());
            assertEquals("EE37702211234", v5xroadMessage.getUserId());
            assertEquals("1234567890", v5xroadMessage.getQueryId());
        }
    }

    /**
     * Test to ensure RPC encoded X-Road 5.0 messages are correctly converted to X-Road 6.0 messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v5xroadRpcEncodedMessageToXroadMessage() throws Exception {
        ClientId expectedClientId = ClientId.create("EE", "X", "foo");
        ServiceId expectedServiceId =
                ServiceId.create("EE", "X", "bar", null, "testQuery", "v1");

        IdentifierMappingProvider mapping =
                mock(IdentifierMappingProvider.class);
        mockIdentifierMapping(mapping,
                new String[] {"toll.0123456789", "andmekogu64"},
                new ClientId[] {expectedClientId,
                        expectedServiceId.getClientId() });

        SoapMessageConverter converter = new SoapMessageConverter(mapping);

        V5XRoadSoapMessageImpl v5xroadMessage =
                readV5XRoadMessage("v5xroad-test.request");
        assertTrue(v5xroadMessage.isRpcEncoded());
        assertEquals("andmekogu64", v5xroadMessage.getProducer());
        assertEquals("toll.0123456789", v5xroadMessage.getConsumer());
        assertEquals("andmekogu64.testQuery.v1", v5xroadMessage.getService());
        assertEquals("27001010001", v5xroadMessage.getUserId());
        assertEquals("testquery4", v5xroadMessage.getQueryId());

        for (SoapMessageImpl xroadMessage
                : withParsedMessage(converter.xroadSoapMessage(
                        v5xroadMessage, false))) {
            assertNotNull(xroadMessage);
            assertTrue(xroadMessage.isRpcEncoded());
            assertEquals(expectedClientId, xroadMessage.getClient());
            assertEquals(expectedServiceId, xroadMessage.getService());
            assertEquals("27001010001", xroadMessage.getUserId());
            assertEquals("testquery4", xroadMessage.getQueryId());
        }
    }

    /**
     * Test to ensure asynchronous X-Road 5.0 messages are
     * correctly converted to asynchronous X-Road 6.0 messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v5xroadAsyncMessageToXroadAsyncMessage() throws Exception {
        ClientId expectedClientId = ClientId.create("EE", "X", "foo");
        ServiceId expectedServiceId =
                ServiceId.create("EE", "X", "bar", null, "testQuery", "v1");

        IdentifierMappingProvider mapping =
                mock(IdentifierMappingProvider.class);
        mockIdentifierMapping(mapping,
                new String[] {"consumer", "producer"},
                new ClientId[] {
                        expectedClientId, expectedServiceId.getClientId()});

        SoapMessageConverter converter = new SoapMessageConverter(mapping);

        V5XRoadSoapMessageImpl v5xroadMessage =
                readV5XRoadMessage("v5xroad-simple-async.request");
        assertEquals("producer", v5xroadMessage.getProducer());
        assertEquals("consumer", v5xroadMessage.getConsumer());
        assertEquals("producer.testQuery.v1", v5xroadMessage.getService());
        assertEquals("EE37702211234", v5xroadMessage.getUserId());
        assertEquals("1234567890", v5xroadMessage.getQueryId());
        assertTrue(v5xroadMessage.isAsync());

        for (SoapMessageImpl xroadMessage
                : withParsedMessage(converter.xroadSoapMessage(
                        v5xroadMessage, false))) {
            assertNotNull(xroadMessage);
            assertEquals(expectedClientId, xroadMessage.getClient());
            assertEquals(expectedServiceId, xroadMessage.getService());
            assertEquals("EE37702211234", xroadMessage.getUserId());
            assertEquals("1234567890", xroadMessage.getQueryId());
            assertTrue(xroadMessage.isAsync());
        }
    }

    /**
     * Test to ensure asynchronous X-Road 6.0 messages are
     * correctly converted to asynchronous X-Road 5.0 messages.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadAsyncMessageToV5XRoadAsyncMessage() throws Exception {
        ClientId expectedClientId =
                ClientId.create("EE", "BUSINESS", "consumer");
        ServiceId expectedServiceId =
                ServiceId.create("EE", "BUSINESS", "producer", null,
                        "testQuery");

        IdentifierMappingProvider mapping =
                mock(IdentifierMappingProvider.class);
        mockIdentifierMapping(mapping,
                new ClientId[] {expectedClientId,
                        expectedServiceId.getClientId()},
                new String[] {"consumer", "producer"});

        SoapMessageConverter converter = new SoapMessageConverter(mapping);

        SoapMessageImpl xroadMessage =
                readXroadMessage("xroad-simple-async.request");
        assertEquals(expectedClientId, xroadMessage.getClient());
        assertEquals(expectedServiceId, xroadMessage.getService());
        assertEquals("EE37702211234", xroadMessage.getUserId());
        assertEquals("1234567890", xroadMessage.getQueryId());
        assertTrue(xroadMessage.isAsync());

        for (V5XRoadSoapMessageImpl v5xroadMessage
                : withParsedMessage(converter.v5XroadSoapMessage(xroadMessage))) {
            assertNotNull(v5xroadMessage);
            assertEquals("consumer", v5xroadMessage.getConsumer());
            assertEquals("producer", v5xroadMessage.getProducer());
            assertEquals("producer.testQuery", v5xroadMessage.getService());
            assertEquals("EE37702211234", v5xroadMessage.getUserId());
            assertEquals("1234567890", v5xroadMessage.getQueryId());
            assertTrue(v5xroadMessage.isAsync());
        }
    }

    // ------------------------------ Helpers ---------------------------------

    private void mockIdentifierMapping(IdentifierMappingProvider mockedMapping,
            ClientId[] input, String[] output) throws Exception {
        assertEquals(input.length, output.length);
        for (int i = 0; i < input.length; i++) {
            when(mockedMapping.getShortName(input[i])).thenReturn(output[i]);
        }
    }

    private void mockIdentifierMapping(IdentifierMappingProvider mockedMapping,
            String[] input, ClientId[] output) throws Exception {
        assertEquals(input.length, output.length);
        for (int i = 0; i < input.length; i++) {
            when(mockedMapping.getClientId(input[i])).thenReturn(output[i]);
        }
    }

    private static SoapMessageImpl readXroadMessage(String fileName)
            throws Exception {
        return readXroadMessage(TestResources.get(fileName));
    }

    private static V5XRoadSoapMessageImpl readV5XRoadMessage(String fileName)
            throws Exception {
        return readV5XRoadMessage(TestResources.get(fileName));
    }

    private static SoapMessageImpl readXroadMessage(InputStream is)
            throws Exception {
        Soap soap = new SoapParserImpl().parse(is);
        assertNotNull(soap);
        assertTrue(soap instanceof SoapMessageImpl);
        return (SoapMessageImpl) soap;
    }

    private static V5XRoadSoapMessageImpl readV5XRoadMessage(InputStream is)
            throws Exception {
        Soap soap = new SoapParserImpl().parse(is);
        assertNotNull(soap);
        assertTrue(soap instanceof V5XRoadSoapMessageImpl);
        return (V5XRoadSoapMessageImpl) soap;
    }

    // Returns the original message and parsed message
    // from its XML representation, so that tests can ensure that converted
    // message object and XML represent the same information.
    private static SoapMessageImpl[] withParsedMessage(
            SoapMessageImpl xroadMessage) throws Exception {
        return new SoapMessageImpl[] {
                xroadMessage, readXroadMessage(is(xroadMessage))};
    }

    private static V5XRoadSoapMessageImpl[] withParsedMessage(
            V5XRoadSoapMessageImpl v5xroadMessage) throws Exception {
        return new V5XRoadSoapMessageImpl[] {
                v5xroadMessage, readV5XRoadMessage(is(v5xroadMessage))};
    }

    private static InputStream is(SoapMessage message) throws Exception {
        return new ByteArrayInputStream(
                message.getXml().getBytes(message.getCharset()));
    }
}
