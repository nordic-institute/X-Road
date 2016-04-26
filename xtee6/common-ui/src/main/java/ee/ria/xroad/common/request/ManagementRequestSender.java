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
package ee.ria.xroad.common.request;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

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
import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.MimeUtils.TEXT_XML_UTF8;
import static ee.ria.xroad.common.util.MimeUtils.getBaseContentType;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

/**
 * Sends various management requests. Authentication certificate registration
 * requests are send directly through the central server, while others are sent
 * as normal X-Road messages.
 */
public final class ManagementRequestSender {

    private final ManagementRequestBuilder builder;

    /**
     * Creates the sender for the user ID, client and receiver used in
     * constructing the X-Road message.
     * @param userId the user id
     * @param sender the sender
     * @param receiver the receiver
     */
    public ManagementRequestSender(String userId, ClientId sender,
            ClientId receiver) {
        this.builder = new ManagementRequestBuilder(userId, receiver, sender);
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
     * @throws Exception if an error occurs
     */
    public void sendAuthCertRegRequest(SecurityServerId securityServer,
            String address, byte[] authCert) throws Exception {
        try (HttpSender sender =
                ManagementRequestClient.createCentralHttpSender()) {
            send(sender, getCentralServiceURI(),
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
     * @throws Exception if an error occurs
     */
    public void sendAuthCertDeletionRequest(SecurityServerId securityServer,
            byte[] authCert) throws Exception {
        sendToProxy(builder.buildAuthCertDeletionRequest(securityServer,
                authCert));
    }

    /**
     * Sends a client registration request as a normal X-Road message.
     * @param securityServer the security server id
     * @param clientId the client id that will be registered
     * @throws Exception if an error occurs
     */
    public void sendClientRegRequest(SecurityServerId securityServer,
            ClientId clientId) throws Exception {
        sendToProxy(builder.buildClientRegRequest(securityServer, clientId));
    }

    /**
     * Sends a client deletion request as a normal X-Road message.
     * @param securityServer the security server id
     * @param clientId the client id that will be registered
     * @throws Exception if an error occurs
     */
    public void sendClientDeletionRequest(SecurityServerId securityServer,
            ClientId clientId) throws Exception {
        sendToProxy(builder.buildClientDeletionRequest(securityServer,
                clientId));
    }

    // -- Helper methods ------------------------------------------------------

    private void sendToProxy(SoapMessageImpl request) throws Exception {
        try (HttpSender sender =
                ManagementRequestClient.createProxyHttpSender()) {
            send(sender, getSecurityServerURI(),
                    new SimpleManagementRequest(request));
        }
    }

    private static void send(HttpSender sender, URI address,
            ManagementRequest req) throws Exception {
        sender.doPost(address, req.getRequestContent(),
                CHUNKED_LENGTH, req.getRequestContentType());

        SoapMessageImpl requestMessage = req.getRequestMessage();
        SoapMessageImpl responseMessage =
                getResponse(sender, req.getResponseContentType());
        SoapUtils.checkConsistency(requestMessage, responseMessage);
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
