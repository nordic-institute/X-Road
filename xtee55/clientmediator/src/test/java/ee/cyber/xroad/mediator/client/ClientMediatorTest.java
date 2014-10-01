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

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.conf.serverconf.ClientCert;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.util.AsyncHttpSender;
import ee.cyber.sdsb.common.util.MimeUtils;
import ee.cyber.xroad.mediator.EmptyGlobalConf;
import ee.cyber.xroad.mediator.EmptyServerConf;
import ee.cyber.xroad.mediator.IdentifierMappingProvider;
import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.MediatorServerConfProvider;
import ee.cyber.xroad.mediator.MockSender;
import ee.cyber.xroad.mediator.TestResources;
import ee.cyber.xroad.mediator.common.MediatorRequest;
import ee.cyber.xroad.mediator.common.MediatorResponse;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.X_INVALID_SOAP;
import static ee.cyber.xroad.mediator.util.MediatorUtils.isSdsbSoapMessage;
import static ee.cyber.xroad.mediator.util.MediatorUtils.isXroadSoapMessage;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClientMediatorTest {

    private boolean isSdsbMember;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Before
    public void setUp() throws Exception {
        MediatorServerConf.reload(new TestServerConf());
        isSdsbMember = false;
    }

    /**
     * Sends an SDSB SOAP message to client mediator, no conversion,
     * proxy responds with SDSB SOAP message, no conversion.
     */
    @Test
    public void sdsbMessageInOut() throws Exception {
        isSdsbMember = true;

        (new MockClientMediatorMessageProcessor(
                TEXT_XML, "sdsb-simple.request",
                TEXT_XML, "sdsb-simple.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isSdsbSoapMessage(in));
                assertTrue(isSdsbSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isSdsbSoapMessage(in));
                assertTrue(isSdsbSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an SDSB SOAP message to client mediator, no conversion,
     * proxy responds with SDSB SOAP message, no conversion.
     */
    @Test
    public void sdsbMessageWithXRoadHeadersInOut() throws Exception {
        isSdsbMember = true;

        (new MockClientMediatorMessageProcessor(
                TEXT_XML, "sdsb-simple.request",
                TEXT_XML, "sdsb-simple-xrdheaders.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isSdsbSoapMessage(in));
                assertTrue(isSdsbSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isSdsbSoapMessage(in));
                assertTrue(isSdsbSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends a X-Road 5.0 SOAP message to client mediator, no conversion,
     * proxy responds with X-Road 5.0 SOAP message, no conversion.
     */
    @Test
    public void xroadMessageInOut() throws Exception {
        isSdsbMember = false;

        (new MockClientMediatorMessageProcessor(
                TEXT_XML, "xroad-simple.request",
                TEXT_XML, "xroad-simple.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isXroadSoapMessage(in));
                assertTrue(isXroadSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isXroadSoapMessage(in));
                assertTrue(isXroadSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an SDSB SOAP message to client mediator,
     * message converted to X-Road 5.0.
     */
    @Test
    public void sdsbMessageInXroadMessageOut() throws Exception {
        isSdsbMember = false;

        (new MockClientMediatorMessageProcessor(
                TEXT_XML, "sdsb-simple.request",
                TEXT_XML, "xroad-simple.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isSdsbSoapMessage(in));
                assertTrue(isXroadSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isXroadSoapMessage(in));
                assertTrue(isSdsbSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an X-Road 5.0 SOAP message to client mediator,
     * message converted to SDSB.
     */
    @Test
    public void xroadMessageInSdsbMessageOut() throws Exception {
        isSdsbMember = true;

        (new MockClientMediatorMessageProcessor(
                TEXT_XML, "xroad-simple.request",
                TEXT_XML, "sdsb-simple.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isXroadSoapMessage(in));
                assertTrue(isSdsbSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isSdsbSoapMessage(in));
                assertTrue(isXroadSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an SDSB multipart message to client mediator,
     * simple SDSB response.
     */
    @Test
    public void sdsbMultipartMessageInSimpleOut() throws Exception {
        isSdsbMember = true;

        String contentType = MimeUtils.mpRelatedContentType("mainBoundary");
        (new MockClientMediatorMessageProcessor(
                contentType, "sdsb-multipart.request",
                TEXT_XML, "sdsb-simple.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isSdsbSoapMessage(in));
                assertTrue(isSdsbSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isSdsbSoapMessage(in));
                assertTrue(isSdsbSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an SDSB multipart message to client mediator,
     * multipart SDSB response.
     */
    @Test
    public void sdsbMultipartMessageInOut() throws Exception {
        isSdsbMember = true;

        String contentType = MimeUtils.mpRelatedContentType("mainBoundary");
        (new MockClientMediatorMessageProcessor(
                contentType, "sdsb-multipart.request",
                contentType, "sdsb-multipart.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isSdsbSoapMessage(in));
                assertTrue(isSdsbSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isSdsbSoapMessage(in));
                assertTrue(isSdsbSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an SDSB multipart message with invalid SOAP.
     */
    @Test
    public void sdsbMultipartInvalidSoapIn() throws Exception {
        thrown.expectError(X_INVALID_SOAP);

        String contentType = MimeUtils.mpRelatedContentType("mainBoundary");
        new MockClientMediatorMessageProcessor(
                contentType, "sdsb-multipart-invalidmessage.request",
                contentType, null).process();
    }

    /**
     * Sends an SDSB message to client mediator,
     * proxy responds with X-Road 5.0 message.
     */
    @Test
    public void sdsbMessageInUnexpectedXroadResponse() throws Exception {
        thrown.expectError(X_INTERNAL_ERROR);

        isSdsbMember = true;

        (new MockClientMediatorMessageProcessor(
                TEXT_XML, "sdsb-simple.request",
                TEXT_XML, "xroad-simple.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(isSdsbSoapMessage(in));
                assertTrue(isSdsbSoapMessage(out));
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(isSdsbSoapMessage(in));
                assertTrue(isSdsbSoapMessage(out));
            }
        }).process();
    }

    /**
     * Sends an SDSB SOAP message to client mediator, no conversion,
     * proxy responds with SOAP fault.
     */
    @Test
    public void sdsbMessageInFaultResponse() throws Exception {
        isSdsbMember = true;
        try {
            new MockClientMediatorMessageProcessor(
                    TEXT_XML, "sdsb-simple.request",
                    TEXT_XML, "sdsb-fault.response").process();
        } catch (CodedException.Fault fault) {
            assertEquals("CODE", fault.getFaultCode());
            assertEquals("STRING", fault.getFaultString());
        }
    }

    // -------------------------- Helpers -------------------------------------

    private class TestGlobalConf extends EmptyGlobalConf {
        @Override
        public Collection<String> getProviderAddress(ClientId client) {
            return isSdsbMember ? Arrays.asList("foobar") : null;
        }

        @Override
        public boolean isSecurityServerClient(ClientId client,
                SecurityServerId securityServer) {
            return true;
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
            super("", null, new TestGlobalConf(), new ClientCert(null, null));

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
