package ee.cyber.sdsb.common.request;

import java.io.InputStream;
import java.net.URI;

import org.apache.commons.io.IOUtils;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.message.Soap;
import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapParserImpl;
import ee.cyber.sdsb.common.message.SoapUtils;
import ee.cyber.sdsb.common.util.HttpSender;

import static ee.cyber.sdsb.common.ErrorCodes.X_HTTP_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.cyber.sdsb.common.util.MimeUtils.TEXT_XML_UTF8;
import static ee.cyber.sdsb.common.util.MimeUtils.getBaseContentType;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

public class ManagementRequestSender {

    private final ManagementRequestBuilder builder;

    public ManagementRequestSender(String userId, ClientId sender,
            ClientId receiver) {
        this.builder = new ManagementRequestBuilder(userId, receiver, sender);
    }

    protected URI getCentralServiceURI() throws Exception {
        return new URI(GlobalConf.getManagementRequestServiceAddress());
    }

    protected URI getSecurityServerURI() throws Exception {
        return new URI("http://localhost:" + PortNumbers.CLIENT_HTTP_PORT);
    }

    // -- Management request send methods -------------------------------------

    public void sendAuthCertRegRequest(SecurityServerId securityServer,
            String address, byte[] authCert) throws Exception {
        send(getCentralServiceURI(),
                new AuthCertRegRequest(authCert, securityServer.getOwner(),
                            builder.buildAuthCertRegRequest(
                                securityServer, address, authCert)));
    }

    public void sendAuthCertDeletionRequest(SecurityServerId securityServer,
            byte[] authCert) throws Exception {
        send(builder.buildAuthCertDeletionRequest(securityServer, authCert));
    }

    public void sendClientRegRequest(SecurityServerId securityServer,
            ClientId clientId) throws Exception {
        send(builder.buildClientRegRequest(securityServer, clientId));
    }

    public void sendClientDeletionRequest(SecurityServerId securityServer,
            ClientId clientId) throws Exception {
        send(builder.buildClientDeletionRequest(securityServer, clientId));
    }

    // -- Helper methods ------------------------------------------------------

    private static void send(URI address, ManagementRequest req)
            throws Exception {
        try (HttpSender sender =
                ManagementRequestClient.getInstance().createHttpSender()) {
            sender.doPost(address, req.getRequestContent(),
                    CHUNKED_LENGTH, req.getRequestContentType());

            SoapMessageImpl requestMessage = req.getRequestMessage();
            SoapMessageImpl responseMessage =
                    getResponse(sender, req.getResponseContentType());
            SoapUtils.checkConsistency(requestMessage, responseMessage);
        }
    }

    /** Convenience method for the default case. */
    private void send(SoapMessageImpl request) throws Exception {
        send(getSecurityServerURI(), new SimpleManagementRequest(request));
    }

    private static SoapMessageImpl getResponse(HttpSender sender,
            String expectedContentType) throws Exception {
        String baseContentType =
                getBaseContentType(sender.getResponseContentType());
        if (baseContentType == null ||
                !baseContentType.equalsIgnoreCase(expectedContentType)) {
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
            return IOUtils.toInputStream(getRequestMessage().getXml());
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
