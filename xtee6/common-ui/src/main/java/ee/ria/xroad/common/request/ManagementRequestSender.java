package ee.ria.xroad.common.request;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.HttpSender;

import static ee.ria.xroad.common.ErrorCodes.X_HTTP_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.message.SoapUtils.getRequestIdInCentralDatabase;
import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.MimeUtils.TEXT_XML_UTF8;
import static ee.ria.xroad.common.util.MimeUtils.getBaseContentType;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

/**
 * Sends various management requests. Authentication certificate registration
 * requests are send directly through the central server, while others are sent
 * as normal X-Road messages.
 */
@Slf4j
public final class ManagementRequestSender {

    private final ManagementRequestBuilder builder;

    /**
     * Creates the sender for the user ID, client and receiver used in
     * constructing the X-Road message.
     * @param sender the sender
     * @param receiver the receiver
     */
    public ManagementRequestSender(ClientId sender, ClientId receiver) {
        this.builder = new ManagementRequestBuilder(sender, receiver);
    }

    protected URI getCentralServiceURI() throws Exception {
        return new URI(GlobalConf.getManagementRequestServiceAddress());
    }

    protected URI getSecurityServerURI() throws Exception {
        return new URI("https://localhost:"
                + SystemProperties.getClientProxyHttpsPort());
    }

    // -- Management request send methods -------------------------------------

    /**
     * Sends the authentication certificate registration request directly
     * to the central server. The request is sent as a signed mime multipart
     * message.
     * @param securityServer the security server id whose certificate is to be
     * registered
     * @param address the IP address of the security server
     * @param authCert the authentication certificate bytes
     * @return request ID in the central server database
     * @throws Exception if an error occurs
     */
    public Integer sendAuthCertRegRequest(SecurityServerId securityServer,
            String address, byte[] authCert) throws Exception {
        try (HttpSender sender =
                ManagementRequestClient.createCentralHttpSender()) {
            return send(sender, getCentralServiceURI(),
                    new AuthCertRegRequest(authCert, securityServer.getOwner(),
                                builder.buildAuthCertRegRequest(
                                    securityServer, address, authCert)));
        }
    }

    /**
     * Sends the authentication certificate deletion request as a normal
     * X-Road message.
     * @param securityServer the security server id whose certificate is to be
     * deleted
     * @param authCert the authentication certificate bytes
     * @return request ID in the central server database
     * @throws Exception if an error occurs
     */
    public Integer sendAuthCertDeletionRequest(SecurityServerId securityServer,
            byte[] authCert) throws Exception {
        return sendToProxy(builder.buildAuthCertDeletionRequest(securityServer,
                authCert));
    }

    /**
     * Sends a client registration request as a normal X-Road message.
     * @param securityServer the security server id
     * @param clientId the client id that will be registered
     * @return request ID in the central server database
     * @throws Exception if an error occurs
     */
    public Integer sendClientRegRequest(SecurityServerId securityServer,
            ClientId clientId) throws Exception {
        return sendToProxy(builder.buildClientRegRequest(securityServer, clientId));
    }

    /**
     * Sends a client deletion request as a normal X-Road message.
     * @param securityServer the security server id
     * @param clientId the client id that will be registered
     * @return request ID in the central server database
     * @throws Exception if an error occurs
     */
    public Integer sendClientDeletionRequest(SecurityServerId securityServer,
            ClientId clientId) throws Exception {
        return sendToProxy(builder.buildClientDeletionRequest(securityServer,
                clientId));
    }

    // -- Helper methods ------------------------------------------------------

    private Integer sendToProxy(SoapMessageImpl request) throws Exception {
        try (HttpSender sender =
                ManagementRequestClient.createProxyHttpSender()) {
            return send(sender, getSecurityServerURI(),
                    new SimpleManagementRequest(request));
        }
    }

    private static Integer send(HttpSender sender, URI address,
            ManagementRequest req) throws Exception {
        sender.doPost(address, req.getRequestContent(),
                CHUNKED_LENGTH, req.getRequestContentType());

        SoapMessageImpl requestMessage = req.getRequestMessage();

        log.trace("Request SOAP:\n{}", requestMessage.getXml());

        SoapMessageImpl responseMessage =
                getResponse(sender, req.getResponseContentType());

        log.trace("Response SOAP:\n{}", responseMessage.getXml());

        SoapUtils.checkConsistency(requestMessage, responseMessage);

        Integer requestId = getRequestIdInCentralDatabase(responseMessage);

        log.trace("Request ID in the central server database: {}", requestId);
        return requestId;
    }

    private static SoapMessageImpl getResponse(HttpSender sender,
            String expectedContentType) throws Exception {
        String baseContentType =
                getBaseContentType(sender.getResponseContentType());
        if (baseContentType == null
                || !baseContentType.equalsIgnoreCase(expectedContentType)) {
            throw new CodedException(X_HTTP_ERROR,
                    "Unexpected or no content type (%s) in response",
                    baseContentType);
        }

        Soap response = new SoapParserImpl().parse(baseContentType,
                sender.getResponseContent());
        if (response instanceof SoapFault) {
            // Server responded with fault
            throw ((SoapFault) response).toCodedException();
        }

        if (!(response instanceof SoapMessageImpl)) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Got unexpected response message " + response);
        }

        SoapMessageImpl responseMessage = (SoapMessageImpl) response;
        if (!responseMessage.isResponse()) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Expected response message");
        }

        return responseMessage;
    }

    private static class SimpleManagementRequest implements ManagementRequest {

        private final SoapMessageImpl requestMessage;

        SimpleManagementRequest(SoapMessageImpl requestMessage) {
            this.requestMessage = requestMessage;
        }

        @Override
        public SoapMessageImpl getRequestMessage() {
            return requestMessage;
        }

        @Override
        public InputStream getRequestContent() throws Exception {
            return new ByteArrayInputStream(getRequestMessage().getBytes());
        }

        @Override
        public String getRequestContentType() {
            return TEXT_XML_UTF8;
        }

        @Override
        public String getResponseContentType() {
            return TEXT_XML;
        }
    }
}
