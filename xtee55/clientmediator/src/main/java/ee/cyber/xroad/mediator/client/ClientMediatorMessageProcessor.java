package ee.cyber.xroad.mediator.client;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.common.conf.serverconf.ClientCert;
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

import static ee.cyber.sdsb.common.metadata.MetadataRequests.ALLOWED_METHODS;
import static ee.cyber.sdsb.common.metadata.MetadataRequests.LIST_METHODS;
import static ee.cyber.xroad.mediator.message.MessageVersion.SDSB;
import static ee.cyber.xroad.mediator.message.MessageVersion.XROAD50;
import static ee.cyber.xroad.mediator.util.MediatorUtils.isSdsbSoapMessage;


class ClientMediatorMessageProcessor extends AbstractMediatorMessageProcessor {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClientMediatorMessageProcessor.class);

    private static final Map<String, ActivationInfo> activationInfo =
            new ConcurrentHashMap<>();

    private final ClientCert clientCert;

    ClientMediatorMessageProcessor(String target,
            HttpClientManager httpClientManager, ClientCert clientCert)
                    throws Exception {
        super(target, httpClientManager);

        if (clientCert == null) {
            throw new IllegalArgumentException("clientCert must not be null");
        }

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

        IsAuthentication.verifyClientAuthentication(sender, clientCert);
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
            LOG.trace("X-Road 5.0 meta service messages cannot be sent "
                    + "to SDSB");
            return false;
        }

        if (MediatorUtils.isSdsbSoapMessage(message)) {
            SoapMessageImpl sdsbSoap = (SoapMessageImpl) message;
            if (sdsbSoap.getService() instanceof CentralServiceId) {
                LOG.trace("SDSB central service message is sent to SDSB");
                return true;
            }

            // SDSB meta requests go to SDSB
            String serviceCode = sdsbSoap.getService().getServiceCode();
            if (LIST_METHODS.equals(serviceCode)
                    || ALLOWED_METHODS.equals(serviceCode)) {
                LOG.trace("SDSB meta request ({}) is sent to SDSB",
                        serviceCode);
                return true;
            }
        }

        ClientId sender = getSender(message);
        if (sender == null) {
            LOG.error("Could not get sender identifier from message");
            return false;
        }

        SecurityServerId thisServer = MediatorServerConf.getIdentifier();
        if (!GlobalConf.isSecurityServerClient(sender, thisServer)) {
            LOG.trace("'{}' is not client of '{}'", sender, thisServer);
            return false;
        }

        ClientId receiver = getReceiver(message);
        if (receiver == null) {
            LOG.error("Could not get receiver identifier from message");
            return false;
        }

        Collection<String> addresses = GlobalConf.getProviderAddress(receiver);
        if (addresses == null || addresses.isEmpty()) {
            LOG.trace("'{}' is not registered in GlobalConf", receiver);

            // If the message is for a federated environment, always send it
            // to SDSB.
            return !receiver.getSdsbInstance().equals(
                    GlobalConf.getInstanceIdentifier());
        }

        // We assume that all security servers of a service provider are
        // activated simultaneously thus we check with the first known address.
        return isServerProxyActivated(addresses.iterator().next());
    }

    boolean isServerProxyActivated(String address) {
        return checkIfServerProxyIsActivated(address);
    }

    private static boolean checkIfServerProxyIsActivated(String address) {
        ActivationInfo i = activationInfo.get(address);
        if (i != null && !i.isExpired()) {
            LOG.trace("Server proxy activated (from cache): {}",
                    i.isActivated());
            return i.isActivated();
        }

        boolean activated = false;
        try {
            URI uri = new URI("http", null, address,
                    SystemProperties.getOcspResponderPort(),
                    "/", null, null);

            LOG.trace("Checking if {} is activated (URL = {})", address, uri);

            HttpURLConnection conn =
                    (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("HEAD");
            int responseCode = conn.getResponseCode();
            LOG.trace("Got HTTP response code {} from {}", responseCode, uri);

            activated = responseCode == HttpServletResponse.SC_OK;
            try {
                conn.getInputStream().close();
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            LOG.warn("Error when checking if " + address + " is activated", e);
        }

        LOG.trace("{} is {}activated", address, !activated ? "not " : "");

        activationInfo.put(address, new ActivationInfo(activated));
        return activated;
    }
}
