/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package org.niis.xroad.common.managementrequest;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.HttpSender;

import jakarta.xml.soap.SOAPException;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.managementrequest.model.AddressChangeRequest;
import org.niis.xroad.common.managementrequest.model.AuthCertRegRequest;
import org.niis.xroad.common.managementrequest.model.ClientDisableRequest;
import org.niis.xroad.common.managementrequest.model.ClientEnableRequest;
import org.niis.xroad.common.managementrequest.model.ClientRegRequest;
import org.niis.xroad.common.managementrequest.model.ManagementRequest;
import org.niis.xroad.common.managementrequest.model.OwnerChangeRequest;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

import static ee.ria.xroad.common.ErrorCodes.X_HTTP_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.MimeTypes.TEXT_XML;
import static ee.ria.xroad.common.util.MimeTypes.TEXT_XML_UTF8;
import static ee.ria.xroad.common.util.MimeUtils.getBaseContentType;

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
    public Integer sendAuthCertRegRequest(SecurityServerId.Conf securityServer, String address, byte[] authCert)
            throws Exception {
        try (HttpSender sender = ManagementRequestClient.createCentralHttpSender()) {
            return send(sender, getCentralServiceURI(), new AuthCertRegRequest(authCert, securityServer.getOwner(),
                    builder.buildAuthCertRegRequest(securityServer, address, authCert)));
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
    public Integer sendAuthCertDeletionRequest(SecurityServerId.Conf securityServer,
                                               byte[] authCert) throws Exception {
        return sendToProxy(builder.buildAuthCertDeletionRequest(securityServer,
                authCert));
    }

    /**
     * Sends the SecurityServer address change request as a normal X-Road message.
     * @param securityServer the security server id
     * @param address the new address
     * @return request ID in the central server database
     * @throws Exception if an error occurs
     */
    public Integer sendAddressChangeRequest(SecurityServerId.Conf securityServer, String address) throws Exception {
        try (HttpSender sender = ManagementRequestClient.createProxyHttpSender()) {
            return send(sender, getSecurityServerURI(),
                    new AddressChangeRequest(securityServer.getOwner(), builder.buildAddressChangeRequest(securityServer, address)));
        }
    }

    /**
     * Sends a client registration request as a normal X-Road message.
     * @param securityServer the security server id
     * @param clientId the client id that will be registered
     * @return request ID in the central server database
     * @throws Exception if an error occurs
     */
    public Integer sendClientRegRequest(SecurityServerId.Conf securityServer, ClientId.Conf clientId) throws Exception {
        try (HttpSender sender = ManagementRequestClient.createProxyHttpSender()) {
            return send(sender, getSecurityServerURI(),
                    new ClientRegRequest(clientId, builder.buildClientRegRequest(securityServer, clientId)));
        }
    }

    /**
     * Sends a client deletion request as a normal X-Road message.
     * @param securityServer the security server id
     * @param clientId the client id that will be registered
     * @return request ID in the central server database
     * @throws Exception if an error occurs
     */
    public Integer sendClientDeletionRequest(SecurityServerId.Conf securityServer,
                                             ClientId.Conf clientId) throws Exception {
        return sendToProxy(builder.buildClientDeletionRequest(securityServer,
                clientId));
    }

    /**
     * Sends an owner change request as a normal X-Road message.
     * @param securityServer the security server id
     * @param clientId the client id of the new security server owner
     * @return request ID in the central server database
     * @throws Exception if an error occurs
     */
    public Integer sendOwnerChangeRequest(SecurityServerId.Conf securityServer,
                                          ClientId.Conf clientId) throws Exception {
        try (HttpSender sender = ManagementRequestClient.createProxyHttpSender()) {
            return send(sender, getSecurityServerURI(),
                    new OwnerChangeRequest(clientId, builder.buildOwnerChangeRequest(securityServer, clientId)));
        }
    }

    public Integer sendClientDisableRequest(SecurityServerId.Conf securityServer,
                                          ClientId.Conf clientId) throws Exception {
        try (HttpSender sender = ManagementRequestClient.createProxyHttpSender()) {
            return send(sender, getSecurityServerURI(),
                    new ClientDisableRequest(clientId, builder.buildClientDisableRequest(securityServer, clientId)));
        }
    }

    public Integer sendClientEnableRequest(SecurityServerId.Conf securityServer,
                                            ClientId.Conf clientId) throws Exception {
        try (HttpSender sender = ManagementRequestClient.createProxyHttpSender()) {
            return send(sender, getSecurityServerURI(),
                    new ClientEnableRequest(clientId, builder.buildClientEnableRequest(securityServer, clientId)));
        }
    }

    // -- Helper methods ------------------------------------------------------

    private Integer sendToProxy(SoapMessageImpl request) throws Exception {
        try (HttpSender sender =
                     ManagementRequestClient.createProxyHttpSender()) {
            return send(sender, getSecurityServerURI(),
                    new SimpleManagementRequest(request));
        }
    }

    private static Integer send(HttpSender sender, URI address, ManagementRequest req) throws Exception {
        sender.doPost(address, req.getRequestContent(), CHUNKED_LENGTH, req.getRequestContentType());

        SoapMessageImpl requestMessage = req.getRequestMessage();

        if (log.isTraceEnabled()) {
            log.trace("Request SOAP:\n{}", requestMessage.getXml());
        }

        SoapMessageImpl responseMessage = getResponse(sender, req.getResponseContentType());

        if (log.isTraceEnabled()) {
            log.trace("Response SOAP:\n{}", responseMessage.getXml());
        }

        SoapUtils.checkConsistency(requestMessage, responseMessage);

        Integer requestId = getRequestId(responseMessage);

        log.trace("Request ID in the central server database: {}", requestId);

        return requestId;
    }

    static Integer getRequestId(
            SoapMessageImpl responseMessage) throws SOAPException {
        NodeList nodes = responseMessage
                .getSoap()
                .getSOAPBody()
                .getElementsByTagNameNS(SoapHeader.NS_XROAD, "requestId");

        if (nodes.getLength() == 0) {
            return null;
        }

        Node node = nodes.item(0);

        try {
            return Integer.parseInt(node.getTextContent());
        } catch (NumberFormatException e) {
            return null;
        }
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
