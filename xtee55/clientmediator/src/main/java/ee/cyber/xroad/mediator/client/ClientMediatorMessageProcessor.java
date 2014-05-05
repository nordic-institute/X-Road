package ee.cyber.xroad.mediator.client;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.GlobalConfImpl;
import ee.cyber.sdsb.common.conf.GlobalConfProvider;
import ee.cyber.sdsb.common.conf.serverconf.IsAuthentication;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.MediatorSystemProperties;
import ee.cyber.xroad.mediator.common.AbstractMediatorMessageProcessor;
import ee.cyber.xroad.mediator.common.HttpClientManager;
import ee.cyber.xroad.mediator.message.XRoadMetaServiceImpl;
import ee.cyber.xroad.mediator.message.XRoadSoapMessageImpl;
import ee.cyber.xroad.mediator.util.MediatorUtils;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.X_SSL_AUTH_FAILED;
import static ee.cyber.xroad.mediator.message.MessageVersion.SDSB;
import static ee.cyber.xroad.mediator.message.MessageVersion.XROAD50;
import static ee.cyber.xroad.mediator.util.MediatorUtils.isSdsbSoapMessage;


class ClientMediatorMessageProcessor extends AbstractMediatorMessageProcessor {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClientMediatorMessageProcessor.class);

    private final GlobalConfProvider globalConf;
    private final ClientCert clientCert;

    ClientMediatorMessageProcessor(String target,
            HttpClientManager httpClientManager, ClientCert clientCert)
                    throws Exception {
        this(target, httpClientManager, loadGlobalConf(), clientCert);
    }

    ClientMediatorMessageProcessor(String target,
            HttpClientManager httpClientManager,
            GlobalConfProvider globalConf, ClientCert clientCert)
                    throws Exception {
        super(target, httpClientManager);

        if (clientCert == null) {
            throw new IllegalArgumentException("clientCert must not be null");
        }

        this.globalConf = globalConf;
        this.clientCert = clientCert;
    }

    @Override
    protected SoapMessage getOutboundRequestMessage(
            SoapMessage inboundRequestMessage) throws Exception {
        LOG.trace("getOutboundRequestMessage()");

        // Get the client from the message and determine, if this message
        // can be relayed through SDSB.

        // If this message can be relayed through SDSB, send it to SDSB proxy
        // (convert the SOAP message to SDSB format, if it is X-Road 5.0 SOAP),
        // otherwise send it to X-Road 5.0 proxy (convert the message to
        // X-Road 5.0 SOAP, if it is SDSB SOAP).

        boolean canSendToSdsb = canSendToSdsb(inboundRequestMessage);
        LOG.trace("canSendToSdsb = {}", canSendToSdsb);

        if (inboundRequestVersion == SDSB && !canSendToSdsb) {
            LOG.trace("Cannot send message to SDSB, sending to X-Road 5.0");

            return getMessageConverter().xroadSoapMessage(
                    (SoapMessageImpl) inboundRequestMessage);
        } else if (inboundRequestVersion == XROAD50 && canSendToSdsb) {
            LOG.trace("Can send message to SDSB");

            return getMessageConverter().sdsbSoapMessage(
                    (XRoadSoapMessageImpl) inboundRequestMessage, true);
        }

        // No conversion necessary
        return inboundRequestMessage;
    }

    @Override
    protected SoapMessage getOutboundResponseMessage(
            SoapMessage inboundResponseMessage) throws Exception {
        LOG.trace("getOutboundResponseMessage()");

        // Do not convert meta messages
        if (inboundResponseMessage instanceof XRoadMetaServiceImpl) {
            return inboundResponseMessage;
        }

        verifyRequestResponseMessageVersions();

        if (inboundRequestVersion == SDSB) {
            if (outboundRequestVersion == XROAD50) {
                LOG.trace("Converting X-Road 5.0 SOAP to SDSB SOAP");

                return getMessageConverter().sdsbSoapMessage(
                        (XRoadSoapMessageImpl) inboundResponseMessage, false);
            } else if (outboundRequestVersion == SDSB) {
                // Remove XRoad headers
                return getMessageConverter().removeXRoadHeaders(
                        (SoapMessageImpl) inboundResponseMessage);
            }
        } else if (inboundRequestVersion == XROAD50
                && outboundRequestVersion == SDSB) {
            LOG.trace("Converting SDSB SOAP to X-Road 5.0 SOAP");

            return getMessageConverter().xroadSoapMessage(
                    (SoapMessageImpl) inboundResponseMessage,
                    inboundRequestHeaderClass);
        }

        // No conversion necessary
        return inboundResponseMessage;
    }

    @Override
    protected URI getTargetAddress(SoapMessage message) throws Exception {
        verifyClientAuthentication(message);

        String sdsbProxy = MediatorSystemProperties.getSdsbProxyAddress();
        String xroadProxy = MediatorSystemProperties.getXroadProxyAddress();
        return new URI(isSdsbSoapMessage(message) ? sdsbProxy : xroadProxy);
    }

    @Override
    protected CloseableHttpAsyncClient getHttpClient() {
        return httpClientManager.getDefaultHttpClient();
    }

    protected void verifyClientAuthentication(SoapMessage message)
            throws Exception {
        ClientId sender = getSender(message);
        if (sender == null) {
            return;
        }

        IsAuthentication isAuthentication =
                MediatorServerConf.getIsAuthentication(sender);
        if (isAuthentication == null) {
            // Means the client was not found in the server conf.
            // The getIsAuthentication method implemented in ServerConfCommonImpl
            // checks if the client exists; if it does, returns the
            // isAuthentication value or NOSSL if no value is specified.
            throw new CodedException(X_INTERNAL_ERROR,
                    "Client '%s' not found", sender);
        }

        LOG.debug("IS authentication for client '{}' is: {}", sender,
                isAuthentication);

        if (isAuthentication == IsAuthentication.SSLNOAUTH) {
            if (clientCert.getVerificationResult() == null) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) specifies SSLNOAUTH but client made "
                                + " plaintext connection", sender);
            }
        } else if (isAuthentication == IsAuthentication.SSLAUTH) {
            if (clientCert.getCert() == null) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) specifies SSLAUTH but did not supply"
                                + " SSL certificate", sender);
            }

            List<X509Certificate> isCerts =
                    MediatorServerConf.getIsCerts(sender);
            if (isCerts.isEmpty()) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) has no IS certificates", sender);
            }

            if (!isCerts.contains(clientCert.getCert())) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) SSL certificate does not match any"
                                + " IS certificates", sender);
            }
        }
    }

    private ClientId getSender(SoapMessage message) throws Exception {
        if (MediatorUtils.isXroadSoapMessage(message)) {
            XRoadSoapMessageImpl xroadSoap = (XRoadSoapMessageImpl) message;
            return getIdentifierMapping().getClientId(xroadSoap.getConsumer());
        }

        return ((SoapMessageImpl) message).getClient();
    }

    private ClientId getReceiver(SoapMessage message) throws Exception {
        if (MediatorUtils.isXroadSoapMessage(message)) {
            XRoadSoapMessageImpl xroadSoap = (XRoadSoapMessageImpl) message;
            return getIdentifierMapping().getClientId(xroadSoap.getProducer());
        }

        return ((SoapMessageImpl) message).getService().getClientId();
    }

    private boolean canSendToSdsb(SoapMessage message) throws Exception {
        if (message instanceof XRoadMetaServiceImpl) {
            return false;
        }

        if (MediatorUtils.isSdsbSoapMessage(message) &&
                ((SoapMessageImpl)
                        message).getService() instanceof CentralServiceId) {
            return true;
        }

        ClientId sender = getSender(message);
        if (sender == null) {
            LOG.error("Could not get sender identifier from message");
            return false;
        }

        SecurityServerId thisServer = MediatorServerConf.getIdentifier();
        if (!globalConf.isSecurityServerClient(sender, thisServer)) {
            LOG.trace("'{}' is not client of '{}'", sender, thisServer);
            return false;
        }

        ClientId receiver = getReceiver(message);
        if (receiver == null) {
            LOG.error("Could not get receiver identifier from message");
            return false;
        }

        Collection<String> addresses = globalConf.getProviderAddress(receiver);
        if (addresses == null || addresses.isEmpty()) {
            LOG.trace("'{}' is not registered in GlobalConf", receiver);
            return false;
        }

        return true;
    }

    private static GlobalConfProvider loadGlobalConf() throws Exception {
        return new GlobalConfImpl(SystemProperties.getGlobalConfFile());
    }
}
