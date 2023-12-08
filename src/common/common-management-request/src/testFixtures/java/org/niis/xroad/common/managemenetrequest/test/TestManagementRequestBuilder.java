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

package org.niis.xroad.common.managemenetrequest.test;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.ProtocolVersion;
import ee.ria.xroad.common.message.SoapBuilder;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.request.AddressChangeRequestType;
import ee.ria.xroad.common.request.AuthCertDeletionRequestType;
import ee.ria.xroad.common.request.AuthCertRegRequestType;
import ee.ria.xroad.common.request.ClientRequestType;
import ee.ria.xroad.common.request.ObjectFactory;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;

import javax.xml.namespace.QName;

import java.util.UUID;

@Slf4j
public class TestManagementRequestBuilder {
    private static final ObjectFactory FACTORY = new ObjectFactory();
    private static final JAXBContext JAXB_CTX = initJaxbContext();

    private final ClientId sender;
    private final ClientId receiver;

    public TestManagementRequestBuilder(ClientId sender, ClientId receiver) {
        this.sender = sender;
        this.receiver = receiver;
    }

    public SoapMessageImpl buildAuthCertRegRequest(SecurityServerId.Conf securityServer, String address, byte[] authCert) {
        log.debug("buildAuthCertRegRequest(server: {}, address: {})", securityServer, address);

        AuthCertRegRequestType request = FACTORY.createAuthCertRegRequestType();
        request.setServer(securityServer);
        request.setAddress(address);
        request.setAuthCert(authCert);

        return buildMessage(element(ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST, AuthCertRegRequestType.class, request));
    }

    public SoapMessageImpl buildAuthCertDeletionRequest(SecurityServerId.Conf securityServer, byte[] authCert) {
        AuthCertDeletionRequestType request = FACTORY.createAuthCertDeletionRequestType();
        request.setServer(securityServer);
        request.setAuthCert(authCert);

        return buildMessage(element(ManagementRequestType.AUTH_CERT_DELETION_REQUEST, AuthCertDeletionRequestType.class, request));
    }

    public SoapMessageImpl buildAddressChangeRequest(SecurityServerId.Conf securityServer, String address) {
        AddressChangeRequestType request = FACTORY.createAddressChangeRequestType();
        request.setServer(securityServer);
        request.setAddress(address);

        return buildMessage(element(ManagementRequestType.ADDRESS_CHANGE_REQUEST, AddressChangeRequestType.class, request));
    }

    public SoapMessageImpl buildClientRegRequest(SecurityServerId.Conf securityServer, ClientId.Conf clientId) {
        return buildGenericClientRequestType(ManagementRequestType.CLIENT_REGISTRATION_REQUEST, securityServer, clientId);
    }

    public SoapMessageImpl buildOwnerChangeRegRequest(SecurityServerId.Conf securityServer, ClientId.Conf clientId) {
        return buildGenericClientRequestType(ManagementRequestType.OWNER_CHANGE_REQUEST, securityServer, clientId);
    }

    public SoapMessageImpl buildClientDeletionRequest(SecurityServerId.Conf securityServer, ClientId.Conf clientId) {
        return buildGenericClientRequestType(ManagementRequestType.CLIENT_DELETION_REQUEST, securityServer, clientId);
    }

    public SoapMessageImpl buildClientDisableRequest(SecurityServerId.Conf securityServer, ClientId.Conf clientId) {
        return buildGenericClientRequestType(ManagementRequestType.CLIENT_DISABLE_REQUEST, securityServer, clientId);
    }

    public SoapMessageImpl buildClientEnableRequest(SecurityServerId.Conf securityServer, ClientId.Conf clientId) {
        return buildGenericClientRequestType(ManagementRequestType.CLIENT_ENABLE_REQUEST, securityServer, clientId);
    }

    private SoapMessageImpl buildGenericClientRequestType(ManagementRequestType type, SecurityServerId.Conf securityServer,
                                                          ClientId.Conf clientId) {
        log.debug("buildClientRegRequest(server: {}, clientId: {})", securityServer, clientId);

        ClientRequestType request = FACTORY.createClientRequestType();
        request.setServer(securityServer);
        request.setClient(clientId);

        return buildMessage(element(type, ClientRequestType.class, request));
    }

    @SneakyThrows
    SoapMessageImpl buildMessage(final JAXBElement<?> bodyJaxbElement) {
        String serviceCode = bodyJaxbElement.getName().getLocalPart();
        ServiceId.Conf service = ServiceId.Conf.create(receiver, serviceCode);

        SoapHeader header = new SoapHeader();
        header.setClient(sender);
        header.setService(service);
        header.setQueryId(generateQueryId());
        header.setProtocolVersion(new ProtocolVersion());

        SoapBuilder builder = new SoapBuilder();
        builder.setHeader(header);
        builder.setRpcEncoded(false); // D/L wrapped

        // Using a callback for setting SOAP body enables us to
        // marshal the content straight into the body element.
        builder.setCreateBodyCallback(soapBodyNode -> getMarshaller().marshal(bodyJaxbElement, soapBodyNode));

        return builder.build();
    }

    private static String generateQueryId() {
        return UUID.randomUUID().toString();
    }

    private static Marshaller getMarshaller() throws Exception {
        return JAXB_CTX.createMarshaller();
    }

    private static <T> JAXBElement<T> element(ManagementRequestType requestType, Class<T> clazz, T value) {
        return new JAXBElement<>(new QName(SoapHeader.NS_XROAD, requestType.getServiceCode()), clazz, null, value);
    }

    private static JAXBContext initJaxbContext() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
