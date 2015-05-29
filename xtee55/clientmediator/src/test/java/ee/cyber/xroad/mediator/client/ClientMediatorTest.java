package ee.cyber.xroad.mediator.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.output.NullOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.xroad.mediator.EmptyGlobalConf;
import ee.cyber.xroad.mediator.EmptyServerConf;
import ee.cyber.xroad.mediator.IdentifierMappingProvider;
import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.MediatorServerConfProvider;
import ee.cyber.xroad.mediator.MockSender;
import ee.cyber.xroad.mediator.TestResources;
import ee.cyber.xroad.mediator.common.MediatorRequest;
import ee.cyber.xroad.mediator.common.MediatorResponse;
import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ClientCert;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.util.AsyncHttpSender;
import ee.ria.xroad.common.util.MimeUtils;

import static ee.cyber.xroad.mediator.util.MediatorUtils.isV5XRoadSoapMessage;
import static ee.cyber.xroad.mediator.util.MediatorUtils.isV6XRoadSoapMessage;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SOAP;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests to verify correct client mediator behavior.
 */
public class ClientMediatorTest {

    private boolean isXroadMember;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Set up configuration.
     * @throws Exception in case of any unexpected errors
     */
    @Before
    public void setUp() throws Exception {
        GlobalConf.reload(new TestGlobalConf());
        MediatorServerConf.reload(new TestServerConf());
        isXroadMember = false;
    }

    /**
     * Sends an X-Road 6.0 SOAP message to client mediator, no conversion,
     * proxy responds with X-Road 6.0 SOAP message, no conversion.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMessageInOut() throws Exception {
        isXroadMember = true;

        (new MockClientMediatorMessageProcessor(
                TEXT_XML, "xroad-simple.request",
                TEXT_XML, "xroad-simple.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isV6XRoadSoapMessage(in));
                assertTrue(isV6XRoadSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isV6XRoadSoapMessage(in));
                assertTrue(isV6XRoadSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an X-Road 6.0 SOAP message to client mediator, no conversion,
     * proxy responds with X-Road 6.0 SOAP message, no conversion.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMessageWithXRoadHeadersInOut() throws Exception {
        isXroadMember = true;

        (new MockClientMediatorMessageProcessor(
                TEXT_XML, "xroad-simple.request",
                TEXT_XML, "xroad-simple-xrdheaders.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isV6XRoadSoapMessage(in));
                assertTrue(isV6XRoadSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isV6XRoadSoapMessage(in));
                assertTrue(isV6XRoadSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends a X-Road 5.0 SOAP message to client mediator, no conversion,
     * proxy responds with X-Road 5.0 SOAP message, no conversion.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v5XroadMessageInOut() throws Exception {
        isXroadMember = false;

        (new MockClientMediatorMessageProcessor(
                TEXT_XML, "v5xroad-simple.request",
                TEXT_XML, "v5xroad-simple.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isV5XRoadSoapMessage(in));
                assertTrue(isV5XRoadSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isV5XRoadSoapMessage(in));
                assertTrue(isV5XRoadSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an X-Road 6.0 SOAP message to client mediator,
     * message converted to X-Road 5.0.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMessageInV5XRoadMessageOut() throws Exception {
        isXroadMember = false;

        (new MockClientMediatorMessageProcessor(
                TEXT_XML, "xroad-simple.request",
                TEXT_XML, "v5xroad-simple.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isV6XRoadSoapMessage(in));
                assertTrue(isV5XRoadSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isV5XRoadSoapMessage(in));
                assertTrue(isV6XRoadSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an X-Road 5.0 SOAP message to client mediator,
     * message converted to X-Road 6.0.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v5XroadMessageInXroadMessageOut() throws Exception {
        isXroadMember = true;

        (new MockClientMediatorMessageProcessor(
                TEXT_XML, "v5xroad-simple.request",
                TEXT_XML, "xroad-simple.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isV5XRoadSoapMessage(in));
                assertTrue(isV6XRoadSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isV6XRoadSoapMessage(in));
                assertTrue(isV5XRoadSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an X-Road 6.0 multipart message to client mediator,
     * simple X-Road 6.0 response.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMultipartMessageInSimpleOut() throws Exception {
        isXroadMember = true;

        String contentType = MimeUtils.mpRelatedContentType("mainBoundary");
        (new MockClientMediatorMessageProcessor(
                contentType, "xroad-multipart.request",
                TEXT_XML, "xroad-simple.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isV6XRoadSoapMessage(in));
                assertTrue(isV6XRoadSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isV6XRoadSoapMessage(in));
                assertTrue(isV6XRoadSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an X-Road 6.0 multipart message to client mediator,
     * multipart X-Road 6.0 response.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMultipartMessageInOut() throws Exception {
        isXroadMember = true;

        String contentType = MimeUtils.mpRelatedContentType("mainBoundary");
        (new MockClientMediatorMessageProcessor(
                contentType, "xroad-multipart.request",
                contentType, "xroad-multipart.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isV6XRoadSoapMessage(in));
                assertTrue(isV6XRoadSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isV6XRoadSoapMessage(in));
                assertTrue(isV6XRoadSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an X-Road 6.0 multipart message with invalid SOAP.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMultipartInvalidSoapIn() throws Exception {
        thrown.expectError(X_INVALID_SOAP);

        String contentType = MimeUtils.mpRelatedContentType("mainBoundary");
        new MockClientMediatorMessageProcessor(
                contentType, "xroad-multipart-invalidmessage.request",
                contentType, null).process();
    }

    /**
     * Sends an X-Road 6.0 message to client mediator,
     * proxy responds with X-Road 5.0 message.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMessageInUnexpectedV5XRoadResponse() throws Exception {
        thrown.expectError(X_INTERNAL_ERROR);

        isXroadMember = true;

        (new MockClientMediatorMessageProcessor(
                TEXT_XML, "xroad-simple.request",
                TEXT_XML, "v5xroad-simple.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isV6XRoadSoapMessage(in));
                assertTrue(isV6XRoadSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isV6XRoadSoapMessage(in));
                assertTrue(isV6XRoadSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an X-Road 6.0 SOAP message to client mediator, no conversion,
     * proxy responds with SOAP fault.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMessageInFaultResponse() throws Exception {
        isXroadMember = true;
        try {
            new MockClientMediatorMessageProcessor(
                    TEXT_XML, "xroad-simple.request",
                    TEXT_XML, "xroad-fault.response").process();
        } catch (CodedException.Fault fault) {
            assertEquals("CODE", fault.getFaultCode());
            assertEquals("STRING", fault.getFaultString());
        }
    }

    // -------------------------- Helpers -------------------------------------

    private class TestGlobalConf extends EmptyGlobalConf {
        @Override
        public Collection<String> getProviderAddress(ClientId client) {
            return isXroadMember ? Arrays.asList("foobar") : null;
        }

        @Override
        public boolean isSecurityServerClient(ClientId client,
                SecurityServerId securityServer) {
            return true;
        }

        @Override
        public String getInstanceIdentifier() {
            return "EE";
        }
    }

    private class TestServerConf extends EmptyServerConf
            implements MediatorServerConfProvider {
        @Override
        public SecurityServerId getIdentifier() {
            return SecurityServerId.create("EE", "BUSINESS", "code", "server");
        }
    }

    private class MockClientMediatorMessageProcessor
            extends ClientMediatorMessageProcessor {

        private final String requestContentType;
        private final InputStream requestContent;

        private final MockSender sender;

        MockClientMediatorMessageProcessor(String requestContentType,
                String requestFileName, String responseContentType,
                String responseFileName) throws Exception {
            this(requestContentType, TestResources.get(requestFileName),
                    responseContentType, TestResources.get(responseFileName));
        }

        MockClientMediatorMessageProcessor(String requestContentType,
                InputStream request, String responseContentType,
                InputStream response) throws Exception {
            super("", null, new ClientCert(null, null));

            this.requestContentType = requestContentType;
            this.requestContent = request;
            this.sender = MockSender.create(responseContentType, response);
        }

        public void process() throws Exception {
            process(new MediatorRequest() {
                @Override
                public String getContentType() {
                    return requestContentType;
                }
                @Override
                public InputStream getInputStream() throws Exception {
                    return requestContent;
                }
                @Override
                public String getParameters() {
                    return null;
                }
            }, new MediatorResponse() {
                @Override
                public void setContentType(String contentType,
                        Map<String, String> additionalHeaders) {
                }
                @Override
                public OutputStream getOutputStream() throws Exception {
                    return new NullOutputStream();
                }
            });
        }

        @Override
        protected SoapMessage getOutboundRequestMessage(SoapMessage in)
                throws Exception {
            SoapMessage out = super.getOutboundRequestMessage(in);
            verifyRequest(in, out);
            return out;
        }

        @Override
        protected SoapMessage getOutboundResponseMessage(SoapMessage in)
                throws Exception {
            SoapMessage out = super.getOutboundResponseMessage(in);
            verifyResponse(in, out);
            return out;
        }

        @Override
        protected IdentifierMappingProvider getIdentifierMapping() {
            return new IdentifierMappingProvider() {
                @Override
                public ClientId getClientId(String shortName) {
                    return ClientId.create("EE", "BUSINESS", shortName);
                }
                @Override
                public String getShortName(ClientId clientId) {
                    return clientId.getMemberCode();
                }
                @Override
                public boolean hasChanged() {
                    return false;
                }
                @Override
                public void load(String fileName) throws Exception {
                }
                @Override
                public void save() throws Exception {
                }
                @Override
                public void save(OutputStream out) throws Exception {
                }
                @Override
                public Set<ClientId> getClientIds() {
                    return null;
                }
                @Override
                public Set<String> getShortNames() {
                    return null;
                }
            };
        }

        @Override
        protected AsyncHttpSender createSender() {
            return sender;
        }

        @Override
        protected void verifyClientAuthentication(SoapMessage message)
                throws Exception {
        }

        @Override
        boolean isServerProxyActivated(String address) {
            return true;
        }

        void verifyRequest(SoapMessage in, SoapMessage out) {
        }

        void verifyResponse(SoapMessage in, SoapMessage out) {
        }
    }

}
