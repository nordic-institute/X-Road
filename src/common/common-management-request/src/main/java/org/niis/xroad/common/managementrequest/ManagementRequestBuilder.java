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
import ee.ria.xroad.common.request.ClientRegRequestType;
import ee.ria.xroad.common.request.ClientRenameRequestType;
import ee.ria.xroad.common.request.ClientRequestType;
import ee.ria.xroad.common.request.MaintenanceModeDisableRequestType;
import ee.ria.xroad.common.request.MaintenanceModeEnableRequestType;
import ee.ria.xroad.common.request.ObjectFactory;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;

import javax.xml.namespace.QName;

import java.util.UUID;

import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.ADDRESS_CHANGE_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.AUTH_CERT_DELETION_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.CLIENT_DELETION_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.CLIENT_DISABLE_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.CLIENT_ENABLE_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.CLIENT_REGISTRATION_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.CLIENT_RENAME_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.MAINTENANCE_MODE_DISABLE_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.MAINTENANCE_MODE_ENABLE_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.OWNER_CHANGE_REQUEST;

@Slf4j
final class ManagementRequestBuilder {
    private static final ObjectFactory FACTORY = new ObjectFactory();
    private static final JAXBContext JAXB_CTX = initJaxbContext();

    private final ClientId.Conf sender;
    private final ClientId.Conf receiver;

    ManagementRequestBuilder(ClientId sender, ClientId receiver) {
        this.sender = ClientId.Conf.ensure(sender);
        this.receiver = ClientId.Conf.ensure(receiver);
    }

    // -- Public API methods --------------------------------------------------

    SoapMessageImpl buildAuthCertRegRequest(SecurityServerId.Conf securityServer, String address, byte[] authCert)
            throws Exception {
        log.debug("buildAuthCertRegRequest(server: {}, address: {})", securityServer, address);

        var request = FACTORY.createAuthCertRegRequestType();
        request.setServer(securityServer);
        request.setAddress(address);
        request.setAuthCert(authCert);

        return buildMessage(element(AUTH_CERT_REGISTRATION_REQUEST, AuthCertRegRequestType.class, request));
    }

    SoapMessageImpl buildAuthCertDeletionRequest(SecurityServerId.Conf securityServer,
                                                 byte[] authCert) throws Exception {
        log.debug("buildAuthCertDeletionRequest(server: {})", securityServer);

        var request = FACTORY.createAuthCertDeletionRequestType();
        request.setServer(securityServer);
        request.setAuthCert(authCert);

        return buildMessage(element(AUTH_CERT_DELETION_REQUEST, AuthCertDeletionRequestType.class, request));
    }

    SoapMessageImpl buildClientRegRequest(SecurityServerId.Conf securityServer, ClientId.Conf client, String clientName) throws Exception {
        log.debug("buildClientRegRequest(server: {}, client: {})", securityServer, client);

        var request = FACTORY.createClientRegRequestType();
        request.setServer(securityServer);
        request.setClient(client);
        request.setSubsystemName(clientName);

        return buildMessage(element(CLIENT_REGISTRATION_REQUEST, ClientRegRequestType.class, request));
    }

    SoapMessageImpl buildClientDeletionRequest(SecurityServerId.Conf securityServer,
                                               ClientId.Conf client) throws Exception {
        log.debug("buildClientDeletionRequest(server: {}, client: {})", securityServer, client);

        var request = FACTORY.createClientRequestType();
        request.setServer(securityServer);
        request.setClient(client);

        return buildMessage(element(CLIENT_DELETION_REQUEST, ClientRequestType.class, request));
    }

    SoapMessageImpl buildOwnerChangeRequest(SecurityServerId.Conf securityServer,
                                            ClientId.Conf client) throws Exception {
        log.debug("buildOwnerChangeRequest(server: {}, client: {})", securityServer, client);

        var request = FACTORY.createClientRequestType();
        request.setServer(securityServer);
        request.setClient(client);

        return buildMessage(element(OWNER_CHANGE_REQUEST, ClientRequestType.class, request));
    }

    SoapMessageImpl buildClientDisableRequest(SecurityServerId.Conf securityServer,
                                              ClientId.Conf client) throws Exception {
        log.debug("buildClientDisableRequest(server: {}, client: {})", securityServer, client);

        var request = FACTORY.createClientRequestType();
        request.setServer(securityServer);
        request.setClient(client);

        return buildMessage(element(CLIENT_DISABLE_REQUEST, ClientRequestType.class, request));
    }

    SoapMessageImpl buildClientEnableRequest(SecurityServerId.Conf securityServer,
                                             ClientId.Conf client) throws Exception {
        log.debug("buildClientEnableRequest(server: {}, client: {})", securityServer, client);

        var request = FACTORY.createClientRequestType();
        request.setServer(securityServer);
        request.setClient(client);

        return buildMessage(element(CLIENT_ENABLE_REQUEST, ClientRequestType.class, request));
    }

    SoapMessageImpl buildClientRenameRequest(SecurityServerId.Conf securityServer,
                                             ClientId.Conf client, String subsystemName) throws Exception {
        log.debug("buildClientRenameRequest(server: {}, client: {}, clientName: {})", securityServer, client, subsystemName);

        var request = FACTORY.createClientRenameRequestType();
        request.setServer(securityServer);
        request.setClient(client);
        request.setSubsystemName(subsystemName);

        return buildMessage(element(CLIENT_RENAME_REQUEST, ClientRenameRequestType.class, request));
    }


    SoapMessageImpl buildAddressChangeRequest(SecurityServerId.Conf securityServer, String address) throws Exception {
        log.debug("buildAddressChangeRequest(server: {}, address: {})", securityServer, address);

        var request = FACTORY.createAddressChangeRequestType();
        request.setServer(securityServer);
        request.setAddress(address);
        return buildMessage(element(ADDRESS_CHANGE_REQUEST, AddressChangeRequestType.class, request));
    }

    SoapMessageImpl buildMaintenanceModeEnableRequest(SecurityServerId.Conf securityServer, String message) throws Exception {
        log.debug("buildMaintenanceModeEnableRequest(server: {}, message: {})", securityServer, message);

        var request = FACTORY.createMaintenanceModeEnableRequestType();
        request.setServer(securityServer);
        request.setMessage(message);
        return buildMessage(element(MAINTENANCE_MODE_ENABLE_REQUEST, MaintenanceModeEnableRequestType.class, request));
    }

    SoapMessageImpl buildMaintenanceModeDisableRequest(SecurityServerId.Conf securityServer) throws Exception {
        log.debug("buildMaintenanceModeDisableRequest(server: {})", securityServer);

        var request = FACTORY.createMaintenanceModeDisableRequestType();
        request.setServer(securityServer);
        return buildMessage(element(MAINTENANCE_MODE_DISABLE_REQUEST, MaintenanceModeDisableRequestType.class, request));
    }

    // -- Private helper methods ----------------------------------------------

    SoapMessageImpl buildMessage(final JAXBElement<?> bodyJaxbElement) throws Exception {
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
