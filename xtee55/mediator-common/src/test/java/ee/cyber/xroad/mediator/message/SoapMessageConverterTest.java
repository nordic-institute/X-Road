package ee.cyber.xroad.mediator.message;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Test;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.message.Soap;
import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.xroad.mediator.IdentifierMappingProvider;
import ee.cyber.xroad.mediator.TestResources;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SoapMessageConverterTest {

    @Test
    public void xroadMessageToSdsbMessage() throws Exception {
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

        XRoadSoapMessageImpl xroadMessage =
                readXroadMessage("xroad-simple.request");
        assertEquals("producer", xroadMessage.getProducer());
        assertEquals("consumer", xroadMessage.getConsumer());
        assertEquals("producer.testQuery.v1", xroadMessage.getService());
        assertEquals("EE37702211234", xroadMessage.getUserId());
        assertEquals("1234567890", xroadMessage.getQueryId());
        assertTrue(xroadMessage.isAsync());
        assertTrue(xroadMessage.getHeader() instanceof XRoadDlSoapHeader.EE);

        for (SoapMessageImpl sdsbMessage
                : withParsedMessage(converter.sdsbSoapMessage(
                        xroadMessage, true))) {
            assertNotNull(sdsbMessage);
            assertEquals(expectedClientId, sdsbMessage.getClient());
            assertEquals(expectedServiceId, sdsbMessage.getService());
            assertEquals("EE37702211234", sdsbMessage.getUserId());
            assertEquals("1234567890", sdsbMessage.getQueryId());
            assertTrue(sdsbMessage.isAsync());
            assertTrue(sdsbMessage.getHeader() instanceof SdsbSoapHeader);

            XRoadSoapMessageImpl xroadMessage2 =
                    converter.xroadSoapMessage(sdsbMessage);
            assertEquals("producer", xroadMessage2.getProducer());
            assertEquals("consumer", xroadMessage2.getConsumer());
            assertEquals("producer.testQuery.v1", xroadMessage2.getService());
            assertEquals("EE37702211234", xroadMessage2.getUserId());
            assertEquals("1234567890", xroadMessage2.getQueryId());
            assertTrue(xroadMessage2.isAsync());
            assertTrue(
                    xroadMessage2.getHeader() instanceof XRoadDlSoapHeader.EE);
        }
    }

    @Test
    public void sdsbMessageToXroadMessage() throws Exception {
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

        SoapMessageImpl sdsbMessage = readSdsbMessage("sdsb-simple.request");
        assertEquals(expectedClientId, sdsbMessage.getClient());
        assertEquals(expectedServiceId, sdsbMessage.getService());
        assertEquals("EE37702211234", sdsbMessage.getUserId());
        assertEquals("1234567890", sdsbMessage.getQueryId());
        assertFalse(sdsbMessage.isAsync());

        for (XRoadSoapMessageImpl xroadMessage
                : withParsedMessage(converter.xroadSoapMessage(sdsbMessage))) {
            assertNotNull(xroadMessage);
            assertEquals("consumer", xroadMessage.getConsumer());
            assertEquals("producer", xroadMessage.getProducer());
            assertEquals("producer.testQuery", xroadMessage.getService());
            assertEquals("EE37702211234", xroadMessage.getUserId());
            assertEquals("1234567890", xroadMessage.getQueryId());
            assertFalse(xroadMessage.isAsync());
        }
    }

    @Test
    public void sdsbMessageWithLegacyHeaderToXroadMessage() throws Exception {
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

        SoapMessageImpl sdsbMessage =
                readSdsbMessage("sdsb-simple-legacy.request");
        assertEquals(expectedClientId, sdsbMessage.getClient());
        assertEquals(expectedServiceId, sdsbMessage.getService());
        assertEquals("EE37702211234", sdsbMessage.getUserId());
        assertEquals("1234567890", sdsbMessage.getQueryId());
        assertFalse(sdsbMessage.isAsync());

        for (XRoadSoapMessageImpl xroadMessage
                : withParsedMessage(converter.xroadSoapMessage(sdsbMessage))) {
            assertNotNull(xroadMessage);
            assertEquals("consumer", xroadMessage.getConsumer());
            assertEquals("producer", xroadMessage.getProducer());
            assertEquals("producer.testQuery.v1", xroadMessage.getService());
            assertEquals("EE37702211234", xroadMessage.getUserId());
            assertEquals("1234567890", xroadMessage.getQueryId());
            assertFalse(xroadMessage.isAsync());

            SoapMessageImpl sdsbMessage2 =
                    converter.sdsbSoapMessage(xroadMessage, false);
            assertNotNull(sdsbMessage2);
            assertEquals(sdsbMessage.getClient(), sdsbMessage2.getClient());
            assertEquals(sdsbMessage.getService(), sdsbMessage2.getService());
            assertEquals(sdsbMessage.getUserId(), sdsbMessage2.getUserId());
            assertEquals(sdsbMessage.getQueryId(), sdsbMessage2.getQueryId());
            assertTrue(sdsbMessage.isAsync() == sdsbMessage2.isAsync());
        }
    }

    @Test
    public void sdsbMessageWithVersionToXroadMessage() throws Exception {
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

        SoapMessageImpl sdsbMessage =
                readSdsbMessage("sdsb-simple-v1.request");
        assertEquals(expectedClientId, sdsbMessage.getClient());
        assertEquals(expectedServiceId, sdsbMessage.getService());
        assertEquals("EE37702211234", sdsbMessage.getUserId());
        assertEquals("1234567890", sdsbMessage.getQueryId());

        for (XRoadSoapMessageImpl xroadMessage
                : withParsedMessage(converter.xroadSoapMessage(sdsbMessage))) {
            assertNotNull(xroadMessage);
            assertEquals("consumer", xroadMessage.getConsumer());
            assertEquals("producer", xroadMessage.getProducer());
            assertEquals("producer.testQuery.v1", xroadMessage.getService());
            assertEquals("EE37702211234", xroadMessage.getUserId());
            assertEquals("1234567890", xroadMessage.getQueryId());
        }
    }

    @Test
    public void xroadMessageWithNsInHeaderToSdsbMessage() throws Exception {
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

        String[] files = {"xroad-simple2.request",
                "xroad-simple3.request", "xroad-simple4.request"};
        for (String file : files) {
            XRoadSoapMessageImpl xroadMessage = readXroadMessage(file);
            assertEquals("producer", xroadMessage.getProducer());
            assertEquals("consumer", xroadMessage.getConsumer());
            assertEquals("producer.testQuery.v1", xroadMessage.getService());
            assertEquals("EE37702211234", xroadMessage.getUserId());
            assertEquals("1234567890", xroadMessage.getQueryId());

            for (SoapMessageImpl sdsbMessage
                    : withParsedMessage(
                            converter.sdsbSoapMessage(xroadMessage, false))) {
                assertNotNull(sdsbMessage);
                assertEquals(expectedClientId, sdsbMessage.getClient());
                assertEquals(expectedServiceId, sdsbMessage.getService());
                assertEquals("EE37702211234", sdsbMessage.getUserId());
                assertEquals("1234567890", sdsbMessage.getQueryId());
            }
        }
    }

    @Test
    public void sdsbRpcEncodedMessageToXroadMessage() throws Exception {
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

        SoapMessageImpl sdsbMessage =
                readSdsbMessage("sdsb-rpc-simple.request");
        assertTrue(sdsbMessage.isRpcEncoded());
        assertEquals(expectedClientId, sdsbMessage.getClient());
        assertEquals(expectedServiceId, sdsbMessage.getService());
        assertEquals("EE37702211234", sdsbMessage.getUserId());
        assertEquals("1234567890", sdsbMessage.getQueryId());

        for (XRoadSoapMessageImpl xroadMessage
                : withParsedMessage(converter.xroadSoapMessage(sdsbMessage))) {
            assertNotNull(xroadMessage);
            assertTrue(xroadMessage.isRpcEncoded());
            assertEquals("consumer", xroadMessage.getConsumer());
            assertEquals("producer", xroadMessage.getProducer());
            assertEquals("producer.testQuery", xroadMessage.getService());
            assertEquals("EE37702211234", xroadMessage.getUserId());
            assertEquals("1234567890", xroadMessage.getQueryId());
        }
    }

    @Test
    public void xroadRpcEncodedMessageToSdsbMessage() throws Exception {
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

        XRoadSoapMessageImpl xroadMessage =
                readXroadMessage("xroad-test.request");
        assertTrue(xroadMessage.isRpcEncoded());
        assertEquals("andmekogu64", xroadMessage.getProducer());
        assertEquals("toll.0123456789", xroadMessage.getConsumer());
        assertEquals("andmekogu64.testQuery.v1", xroadMessage.getService());
        assertEquals("27001010001", xroadMessage.getUserId());
        assertEquals("testquery4", xroadMessage.getQueryId());

        for (SoapMessageImpl sdsbMessage
                : withParsedMessage(converter.sdsbSoapMessage(
                        xroadMessage, false))) {
            assertNotNull(sdsbMessage);
            assertTrue(sdsbMessage.isRpcEncoded());
            assertEquals(expectedClientId, sdsbMessage.getClient());
            assertEquals(expectedServiceId, sdsbMessage.getService());
            assertEquals("27001010001", sdsbMessage.getUserId());
            assertEquals("testquery4", sdsbMessage.getQueryId());
        }
    }

    @Test
    public void xroadAsyncMessageToSdsbAsyncMessage() throws Exception {
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

        XRoadSoapMessageImpl xroadMessage =
                readXroadMessage("xroad-simple-async.request");
        assertEquals("producer", xroadMessage.getProducer());
        assertEquals("consumer", xroadMessage.getConsumer());
        assertEquals("producer.testQuery.v1", xroadMessage.getService());
        assertEquals("EE37702211234", xroadMessage.getUserId());
        assertEquals("1234567890", xroadMessage.getQueryId());
        assertTrue(xroadMessage.isAsync());

        for (SoapMessageImpl sdsbMessage
                : withParsedMessage(converter.sdsbSoapMessage(
                        xroadMessage, false))) {
            assertNotNull(sdsbMessage);
            assertEquals(expectedClientId, sdsbMessage.getClient());
            assertEquals(expectedServiceId, sdsbMessage.getService());
            assertEquals("EE37702211234", sdsbMessage.getUserId());
            assertEquals("1234567890", sdsbMessage.getQueryId());
            assertTrue(sdsbMessage.isAsync());
        }
    }

    @Test
    public void sdsbAsyncMessageToXroadAsyncMessage() throws Exception {
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

        SoapMessageImpl sdsbMessage =
                readSdsbMessage("sdsb-simple-async.request");
        assertEquals(expectedClientId, sdsbMessage.getClient());
        assertEquals(expectedServiceId, sdsbMessage.getService());
        assertEquals("EE37702211234", sdsbMessage.getUserId());
        assertEquals("1234567890", sdsbMessage.getQueryId());
        assertTrue(sdsbMessage.isAsync());

        for (XRoadSoapMessageImpl xroadMessage
                : withParsedMessage(converter.xroadSoapMessage(sdsbMessage))) {
            assertNotNull(xroadMessage);
            assertEquals("consumer", xroadMessage.getConsumer());
            assertEquals("producer", xroadMessage.getProducer());
            assertEquals("producer.testQuery", xroadMessage.getService());
            assertEquals("EE37702211234", xroadMessage.getUserId());
            assertEquals("1234567890", xroadMessage.getQueryId());
            assertTrue(xroadMessage.isAsync());
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

    private static SoapMessageImpl readSdsbMessage(String fileName)
            throws Exception {
        return readSdsbMessage(TestResources.get(fileName));
    }

    private static XRoadSoapMessageImpl readXroadMessage(String fileName)
            throws Exception {
        return readXroadMessage(TestResources.get(fileName));
    }

    private static SoapMessageImpl readSdsbMessage(InputStream is)
            throws Exception {
        Soap soap = new SoapParserImpl().parse(is);
        assertNotNull(soap);
        assertTrue(soap instanceof SoapMessageImpl);
        return (SoapMessageImpl) soap;
    }

    private static XRoadSoapMessageImpl readXroadMessage(InputStream is)
            throws Exception {
        Soap soap = new SoapParserImpl().parse(is);
        assertNotNull(soap);
        assertTrue(soap instanceof XRoadSoapMessageImpl);
        return (XRoadSoapMessageImpl) soap;
    }

    // Returns the original message and parsed message
    // from its XML representation, so that tests can ensure that converted
    // message object and XML represent the same information.
    private static SoapMessageImpl[] withParsedMessage(
            SoapMessageImpl sdsbMessage) throws Exception {
        return new SoapMessageImpl[] {
                sdsbMessage, readSdsbMessage(is(sdsbMessage))};
    }

    private static XRoadSoapMessageImpl[] withParsedMessage(
            XRoadSoapMessageImpl xroadMessage) throws Exception {
        return new XRoadSoapMessageImpl[] {
                xroadMessage, readXroadMessage(is(xroadMessage))};
    }

    private static InputStream is(SoapMessage message) throws Exception {
        return new ByteArrayInputStream(
                message.getXml().getBytes(message.getCharset()));
    }
}
