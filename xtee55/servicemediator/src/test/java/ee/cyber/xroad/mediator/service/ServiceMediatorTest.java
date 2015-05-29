package ee.cyber.xroad.mediator.service;

import java.io.InputStream;
import java.io.OutputStream;
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
import ee.cyber.xroad.mediator.message.V5XRoadListMethods;
import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.util.AsyncHttpSender;
import ee.ria.xroad.common.util.MimeUtils;

import static ee.cyber.xroad.mediator.util.MediatorUtils.isV5XRoadSoapMessage;
import static ee.cyber.xroad.mediator.util.MediatorUtils.isV6XRoadSoapMessage;
import static ee.ria.xroad.common.ErrorCodes.*;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests to verify correct service mediator behavior.
 */
public class ServiceMediatorTest {

    private boolean isXroadService;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Set up configuration.
     */
    @Before
    public void setUp() {
        isXroadService = false;
        GlobalConf.reload(new EmptyGlobalConf());
        MediatorServerConf.reload(new TestServerConf());
    }

    /**
     * Sends an X-Road 6.0 SOAP message to service mediator,
     * response is an X-Road 6.0 message.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void xroadMessageInOut() throws Exception {
        isXroadService = true;

        (new MockServiceMediatorMessageProcessor(
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
     * Sends an X-Road 6.0 SOAP message with legacy headers to service mediator,
     * response is an X-Road 6.0 message.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void xroadMessageWithV5XRoadHeadersInOut() throws Exception {
        isXroadService = true;

        (new MockServiceMediatorMessageProcessor(
                TEXT_XML, "xroad-simple-xrdheaders.request",
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
     * Sends an X-Road 5.0 SOAP message to service mediator,
     * response is an X-Road 5.0 message.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v5XroadMessageInOut() throws Exception {
        isXroadService = false;

        (new MockServiceMediatorMessageProcessor(
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
     * Sends an X-Road 6.0 SOAP message to service mediator,
     * message converted to X-Road 5.0.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMessageInV5XRoadMessageOut() throws Exception {
        isXroadService = false;

        (new MockServiceMediatorMessageProcessor(
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
     * Sends an X-Road 5.0 SOAP message to service mediator,
     * message converted to X-Road 6.0.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v5XroadMessageInXroadMessageOut() throws Exception {
        isXroadService = true;

        (new MockServiceMediatorMessageProcessor(
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
     * Sends an X-Road 6.0 multipart message to service mediator,
     * simple X-Road 6.0 response.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void xroadMultipartMessageInSimpleOut() throws Exception {
        isXroadService = true;

        String contentType = MimeUtils.mpRelatedContentType("mainBoundary");
        (new MockServiceMediatorMessageProcessor(
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
     * Sends an X-Road 6.0 multipart message to service mediator,
     * multipart X-Road 6.0 response.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void xroadMultipartMessageInOut() throws Exception {
        isXroadService = true;

        String contentType = MimeUtils.mpRelatedContentType("mainBoundary");
        (new MockServiceMediatorMessageProcessor(
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
    public void xroadMultipartInvalidSoapIn() throws Exception {
        thrown.expectError(X_INVALID_SOAP);

        String contentType = MimeUtils.mpRelatedContentType("mainBoundary");
        new MockServiceMediatorMessageProcessor(
                contentType, "xroad-multipart-invalidmessage.request",
                contentType, null).process();
    }

    /**
     * Sends an X-Road 6.0 message to service mediator,
     * proxy responds with X-Road 5.0 message.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void v6XRoadMessageInUnexpectedV5XRoadResponse() throws Exception {
        thrown.expectError(X_INTERNAL_ERROR);

        isXroadService = true;

        (new MockServiceMediatorMessageProcessor(
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
     * Sends an X-Road 6.0 SOAP message to service mediator, no conversion,
     * service responds with SOAP fault.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void xroadMessageInFaultResponse() throws Exception {
        isXroadService = true;
        try {
            new MockServiceMediatorMessageProcessor(
                    TEXT_XML, "xroad-simple.request",
                    TEXT_XML, "xroad-fault.response").process();
        } catch (CodedException.Fault fault) {
            assertEquals("CODE", fault.getFaultCode());
            assertEquals("STRING", fault.getFaultString());
        }
    }

    /**
     * Sends a listMethods meta request.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void listMethods() throws Exception {
        isXroadService = true;

        (new MockServiceMediatorMessageProcessor("/consumer",
                TEXT_XML, "listMethods.request",
                TEXT_XML, "listMethods.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(in instanceof V5XRoadListMethods);
                assertTrue(out instanceof V5XRoadListMethods);
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(in instanceof V5XRoadListMethods);
                assertTrue(out instanceof V5XRoadListMethods);
            }
        }).process();
    }

    /**
     * Sends a message to disabled service, response is a SOAP fault.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void serviceDisabled() throws Exception {
        thrown.expectError(X_SERVICE_DISABLED);

        MediatorServerConf.reload(new TestServerConf() {
            @Override
            public String getDisabledNotice(ServiceId service) {
                return "disabled";
            }
        });

        new MockServiceMediatorMessageProcessor("",
                TEXT_XML, "xroad-simple.request",
                TEXT_XML, "xroad-fault.response").process();
    }

    // -------------------------- Helpers -------------------------------------

    private class TestServerConf extends EmptyServerConf
            implements MediatorServerConfProvider {
        @Override
        public boolean isXroadService(ServiceId serviceId) {
            return isXroadService;
        }

        @Override
        public String getBackendURL(ServiceId serviceId) {
            return "http://localhost:80"; // dummy
        }

        @Override
        public String getBackendURL(ClientId clientId) {
            return "http://localhost:80"; // dummy
        }

        @Override
        public IsAuthentication getIsAuthentication(ClientId client) {
            return IsAuthentication.NOSSL;
        }
    }

    private class MockServiceMediatorMessageProcessor
            extends ServiceMediatorMessageProcessor {

        private final String requestContentType;
        private final InputStream requestContent;

        private final MockSender sender;

        MockServiceMediatorMessageProcessor(String requestContentType,
                String requestFileName, String responseContentType,
                String responseFileName) throws Exception {
            this("", requestContentType, requestFileName, responseContentType,
                    responseFileName);
        }

        MockServiceMediatorMessageProcessor(String target,
                String requestContentType, String requestFileName,
                String responseContentType, String responseFileName)
                        throws Exception {
            super(target, null);

            this.requestContentType = requestContentType;
            this.requestContent = requestFileName != null
                    ? TestResources.get(requestFileName) : null;
            this.sender = MockSender.create(responseContentType,
                    responseFileName != null
                        ? TestResources.get(responseFileName) : null);
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

        void verifyRequest(SoapMessage in, SoapMessage out) {
        }

        void verifyResponse(SoapMessage in, SoapMessage out) {
        }
    }
}
