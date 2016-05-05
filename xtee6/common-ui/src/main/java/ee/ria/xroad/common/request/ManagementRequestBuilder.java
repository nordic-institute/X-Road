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

import java.util.UUID;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPBody;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapBuilder;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessageImpl;

import static ee.ria.xroad.common.request.ManagementRequests.*;

final class ManagementRequestBuilder {

    private static final Logger LOG =
            LoggerFactory.getLogger(ManagementRequestBuilder.class);

    private static final ObjectFactory FACTORY = new ObjectFactory();
    private static final JAXBContext JAXB_CTX = initJaxbContext();

    private final String userId;
    private final ClientId sender;
    private final ClientId receiver;

    ManagementRequestBuilder(String userId, ClientId sender,
            ClientId receiver) {
        this.userId = userId;
        this.sender = sender;
        this.receiver = receiver;
    }

    // -- Public API methods --------------------------------------------------

    SoapMessageImpl buildAuthCertRegRequest(
            SecurityServerId securityServer, String address, byte[] authCert)
                    throws Exception {
        LOG.debug("buildAuthCertRegRequest(server: {}, address: {})",
                new Object[] {securityServer, address});

        AuthCertRegRequestType request =
                FACTORY.createAuthCertRegRequestType();
        request.setServer(securityServer);
        request.setAddress(address);
        request.setAuthCert(authCert);

        return buildMessage(element(AUTH_CERT_REG,
                AuthCertRegRequestType.class, request));
    }

    SoapMessageImpl buildAuthCertDeletionRequest(
            SecurityServerId securityServer, byte[] authCert)
                    throws Exception {
        LOG.debug("buildAuthCertDeletionRequest(server: {})", securityServer);

        AuthCertDeletionRequestType request =
                FACTORY.createAuthCertDeletionRequestType();
        request.setServer(securityServer);
        request.setAuthCert(authCert);

        return buildMessage(element(AUTH_CERT_DELETION,
                AuthCertDeletionRequestType.class, request));
    }

    SoapMessageImpl buildClientRegRequest(
            SecurityServerId securityServer, ClientId client)
                    throws Exception {
        LOG.debug("buildClientRegRequest(server: {}, client: {})",
                securityServer, client);

        ClientRequestType request = FACTORY.createClientRequestType();
        request.setServer(securityServer);
        request.setClient(client);

        return buildMessage(element(CLIENT_REG,
                ClientRequestType.class, request));
    }

    SoapMessageImpl buildClientDeletionRequest(
            SecurityServerId securityServer, ClientId client)
                    throws Exception {
        LOG.debug("buildClientDeletionRequest(server: {}, client: {})",
                securityServer, client);

        ClientRequestType request = FACTORY.createClientRequestType();
        request.setServer(securityServer);
        request.setClient(client);

        return buildMessage(element(CLIENT_DELETION,
                ClientRequestType.class, request));
    }

    // -- Private helper methods ----------------------------------------------

    SoapMessageImpl buildMessage(final JAXBElement<?> bodyJaxbElement)
            throws Exception {
        String serviceCode = bodyJaxbElement.getName().getLocalPart();
        ServiceId service = ServiceId.create(receiver, serviceCode);

        SoapHeader header = new SoapHeader();
        header.setClient(sender);
        header.setService(service);
        header.setUserId(userId);
        header.setQueryId(generateQueryId());

        SoapBuilder builder = new SoapBuilder();
        builder.setHeader(header);
        builder.setRpcEncoded(false); // D/L wrapped

        builder.setCreateBodyCallback(new SoapBuilder.SoapBodyCallback() {
                /**
                 * Using a callback for setting SOAP body enables us to
                 * marshal the content straight into the body element.
                 */
                @Override
                public void create(SOAPBody soapBodyNode) throws Exception {
                    getMarshaller().marshal(bodyJaxbElement, soapBodyNode);
                }
            });

        return builder.build();
    }

    private static String generateQueryId() {
        return UUID.randomUUID().toString();
    }

    private static Marshaller getMarshaller() throws Exception {
        return JAXB_CTX.createMarshaller();
    }

    private static <T> JAXBElement<T> element(String name, Class<T> clazz,
            T value) {
        return new JAXBElement<T>(new QName(SoapHeader.NS_XROAD, name),
                clazz, null, value);
    }

    private static JAXBContext initJaxbContext() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
