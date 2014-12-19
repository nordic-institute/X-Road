package ee.cyber.xroad.mediator.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.output.NullOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.common.conf.serverconf.IsAuthentication;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
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
import ee.cyber.xroad.mediator.message.XRoadListMethods;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.xroad.mediator.util.MediatorUtils.isSdsbSoapMessage;
import static ee.cyber.xroad.mediator.util.MediatorUtils.isXroadSoapMessage;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServiceMediatorTest {

    private boolean isSdsbService;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Before
    public void setUp() {
        isSdsbService = false;
        GlobalConf.reload(new EmptyGlobalConf());
        MediatorServerConf.reload(new TestServerConf());
    }

    @Test
    public void sdsbMessageInOut() throws Exception {
        isSdsbService = true;

        (new MockServiceMediatorMessageProcessor(
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

    @Test
    public void sdsbMessageWithXRoadHeadersInOut() throws Exception {
        isSdsbService = true;

        (new MockServiceMediatorMessageProcessor(
                TEXT_XML, "sdsb-simple-xrdheaders.request",
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

    @Test
    public void xroadMessageInOut() throws Exception {
        isSdsbService = false;

        (new MockServiceMediatorMessageProcessor(
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
     * Sends an SDSB SOAP message to service mediator,
     * message converted to X-Road 5.0.
     */
    @Test
    public void sdsbMessageInXroadMessageOut() throws Exception {
        isSdsbService = false;

        (new MockServiceMediatorMessageProcessor(
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
     * Sends an X-Road 5.0 SOAP message to service mediator,
     * message converted to SDSB.
     */
    @Test
    public void xroadMessageInSdsbMessageOut() throws Exception {
        isSdsbService = true;

        (new MockServiceMediatorMessageProcessor(
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
     * Sends an SDSB multipart message to service mediator,
     * simple SDSB response.
     */
    @Test
    public void sdsbMultipartMessageInSimpleOut() throws Exception {
        isSdsbService = true;

        String contentType = MimeUtils.mpRelatedContentType("mainBoundary");
        (new MockServiceMediatorMessageProcessor(
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
     * Sends an SDSB multipart message to service mediator,
     * multipart SDSB response.
     */
    @Test
    public void sdsbMultipartMessageInOut() throws Exception {
        isSdsbService = true;

        String contentType = MimeUtils.mpRelatedContentType("mainBoundary");
        (new MockServiceMediatorMessageProcessor(
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
        new MockServiceMediatorMessageProcessor(
                contentType, "sdsb-multipart-invalidmessage.request",
                contentType, null).process();
    }

    /**
     * Sends an SDSB message to service mediator,
     * proxy responds with X-Road 5.0 message.
     */
    @Test
    public void sdsbMessageInUnexpectedXroadResponse() throws Exception {
        thrown.expectError(X_INTERNAL_ERROR);

        isSdsbService = true;

        (new MockServiceMediatorMessageProcessor(
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
     * Sends an SDSB SOAP message to service mediator, no conversion,
     * service responds with SOAP fault.
     */
    @Test
    public void sdsbMessageInFaultResponse() throws Exception {
        isSdsbService = true;
        try {
            new MockServiceMediatorMessageProcessor(
                    TEXT_XML, "sdsb-simple.request",
                    TEXT_XML, "sdsb-fault.response").process();
        } catch (CodedException.Fault fault) {
            assertEquals("CODE", fault.getFaultCode());
            assertEquals("STRING", fault.getFaultString());
        }
    }

    @Test
    public void listMethods() throws Exception {
        isSdsbService = true;

        (new MockServiceMediatorMessageProcessor("/consumer",
                TEXT_XML, "listMethods.request",
                TEXT_XML, "listMethods.response") {
            @Override
            void verifyRequest(SoapMessage in, SoapMessage out) {
                assertTrue(in instanceof XRoadListMethods);
                assertTrue(out instanceof XRoadListMethods);
            }
            @Override
            void verifyResponse(SoapMessage in, SoapMessage out) {
                assertTrue(in instanceof XRoadListMethods);
                assertTrue(out instanceof XRoadListMethods);
            }
        }).process();
    }

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
                TEXT_XML, "sdsb-simple.request",
                TEXT_XML, "sdsb-fault.response").process();
    }

    // -------------------------- Helpers -------------------------------------

    private class TestServerConf extends EmptyServerConf
            implements MediatorServerConfProvider {
        @Override
        public boolean isSdsbService(ServiceId serviceId) {
            return isSdsbService;
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
