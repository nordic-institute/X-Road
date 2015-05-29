package ee.cyber.xroad.mediator.client;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.MediatorSystemProperties;
import ee.cyber.xroad.mediator.common.AbstractMediatorMessageProcessor;
import ee.cyber.xroad.mediator.common.HttpClientManager;
import ee.cyber.xroad.mediator.message.V5XRoadMetaServiceImpl;
import ee.cyber.xroad.mediator.message.V5XRoadSoapMessageImpl;
import ee.cyber.xroad.mediator.util.MediatorUtils;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ClientCert;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageImpl;

import static ee.cyber.xroad.mediator.message.MessageVersion.XROAD50;
import static ee.cyber.xroad.mediator.message.MessageVersion.XROAD60;
import static ee.cyber.xroad.mediator.util.MediatorUtils.isV6XRoadSoapMessage;
import static ee.ria.xroad.common.metadata.MetadataRequests.ALLOWED_METHODS;
import static ee.ria.xroad.common.metadata.MetadataRequests.LIST_METHODS;

@Slf4j
class ClientMediatorMessageProcessor extends AbstractMediatorMessageProcessor {

    private static final Map<String, ActivationInfo> ACTIVATION_INFO =
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
        log.trace("getOutboundRequestMessage()");

        // Get the client from the message and determine, if this message
        // can be relayed through X-Road 6.0.

        // If this message can be relayed through X-Road 6.0, send it to
        // X-Road 6.0 proxy (convert the SOAP message to X-Road 6.0 format,
        // if it is X-Road 5.0 SOAP), otherwise send it to X-Road 5.0 proxy
        // (convert the message to  X-Road 5.0 SOAP, if it is X-Road 6.0 SOAP).

        boolean canSendToXroad = canSendToXroad(inboundRequestMessage);
        log.trace("canSendToXroad = {}", canSendToXroad);

        if (inboundRequestVersion == XROAD60 && !canSendToXroad) {
            log.trace("Cannot send message to X-Road 6.0, "
                    + "sending to X-Road 5.0");

            return getMessageConverter().v5XroadSoapMessage(
                    (SoapMessageImpl) inboundRequestMessage);
        } else if (inboundRequestVersion == XROAD50 && canSendToXroad) {
            log.trace("Can send message to X-Road 6.0");

            return getMessageConverter().xroadSoapMessage(
                    (V5XRoadSoapMessageImpl) inboundRequestMessage, true);
        }

        // No conversion necessary
        return inboundRequestMessage;
    }

    @Override
    protected SoapMessage getOutboundResponseMessage(
            SoapMessage inboundResponseMessage) throws Exception {
        log.trace("getOutboundResponseMessage()");

        // Do not convert meta messages
        if (inboundResponseMessage instanceof V5XRoadMetaServiceImpl) {
            return inboundResponseMessage;
        }

        verifyRequestResponseMessageVersions();

        if (inboundRequestVersion == XROAD60) {
            if (outboundRequestVersion == XROAD50) {
                log.trace("Converting X-Road 5.0 SOAP to X-Road 6.0 SOAP");

                return getMessageConverter().xroadSoapMessage(
                        (V5XRoadSoapMessageImpl) inboundResponseMessage, false);
            } else if (outboundRequestVersion == XROAD60) {
                // Remove XRoad headers
                return getMessageConverter().removeXRoadHeaders(
                        (SoapMessageImpl) inboundResponseMessage);
            }
        } else if (inboundRequestVersion == XROAD50
                && outboundRequestVersion == XROAD60) {
            log.trace("Converting X-Road 6.0 SOAP to X-Road 5.0 SOAP");

            return getMessageConverter().v5XroadSoapMessage(
                    (SoapMessageImpl) inboundResponseMessage,
                    inboundRequestHeaderClass);
        }

        // No conversion necessary
        return inboundResponseMessage;
    }

    @Override
    protected URI getTargetAddress(SoapMessage message) throws Exception {
        verifyClientAuthentication(message);

        String xroadProxy = MediatorSystemProperties.getXroadProxyAddress();
        String v5XroadProxy = MediatorSystemProperties.getV5XroadProxyAddress();
        return new URI(isV6XRoadSoapMessage(message) ? xroadProxy : v5XroadProxy);
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
        if (MediatorUtils.isV5XRoadSoapMessage(message)) {
            V5XRoadSoapMessageImpl xroadSoap = (V5XRoadSoapMessageImpl) message;
            return getIdentifierMapping().getClientId(xroadSoap.getConsumer());
        }

        return ((SoapMessageImpl) message).getClient();
    }

    private ClientId getReceiver(SoapMessage message) throws Exception {
        if (MediatorUtils.isV5XRoadSoapMessage(message)) {
            V5XRoadSoapMessageImpl xroadSoap = (V5XRoadSoapMessageImpl) message;
            return getIdentifierMapping().getClientId(xroadSoap.getProducer());
        }

        return ((SoapMessageImpl) message).getService().getClientId();
    }

    private boolean canSendToXroad(SoapMessage message) throws Exception {
        if (message instanceof V5XRoadMetaServiceImpl) {
            log.trace("X-Road 5.0 meta service messages cannot be sent "
                    + "to X-Road 6.0");
            return false;
        }

        if (MediatorUtils.isV6XRoadSoapMessage(message)) {
            SoapMessageImpl xroadSoap = (SoapMessageImpl) message;
            if (xroadSoap.getService() instanceof CentralServiceId) {
                log.trace("X-Road 6.0 central service message is sent "
                        + "to X-Road 6.0");
                return true;
            }

            // X-Road 6.0 meta requests go to X-Road 6.0
            String serviceCode = xroadSoap.getService().getServiceCode();
            if (LIST_METHODS.equals(serviceCode)
                    || ALLOWED_METHODS.equals(serviceCode)) {
                log.trace("X-Road 6.0 meta request ({}) is sent to X-Road 6.0",
                        serviceCode);
                return true;
            }
        }

        ClientId sender = getSender(message);
        if (sender == null) {
            log.error("Could not get sender identifier from message");
            return false;
        }

        SecurityServerId thisServer = MediatorServerConf.getIdentifier();
        if (!GlobalConf.isSecurityServerClient(sender, thisServer)) {
            log.trace("'{}' is not client of '{}'", sender, thisServer);
            return false;
        }

        ClientId receiver = getReceiver(message);
        if (receiver == null) {
            log.error("Could not get receiver identifier from message");
            return false;
        }

        Collection<String> addresses = GlobalConf.getProviderAddress(receiver);
        if (addresses == null || addresses.isEmpty()) {
            log.trace("'{}' is not registered in GlobalConf", receiver);

            // If the message is for a federated environment, always send it
            // to X-Road 6.0.
            return !receiver.getXRoadInstance().equals(
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
        ActivationInfo i = ACTIVATION_INFO.get(address);
        if (i != null && !i.isExpired()) {
            log.trace("Server proxy activated (from cache): {}",
                    i.isActivated());
            return i.isActivated();
        }

        boolean activated = false;
        try {
            URI uri = new URI("http", null, address,
                    SystemProperties.getOcspResponderPort(),
                    "/", null, null);

            log.trace("Checking if {} is activated (URL = {})", address, uri);

            HttpURLConnection conn =
                    (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("HEAD");
            int responseCode = conn.getResponseCode();
            log.trace("Got HTTP response code {} from {}", responseCode, uri);

            activated = responseCode == HttpServletResponse.SC_OK;
            try {
                conn.getInputStream().close();
            } catch (Exception ignored) {
                log.warn("Error when closing connection input stream");
            }
        } catch (Exception e) {
            log.warn("Error when checking if " + address + " is activated", e);
        }

        log.trace("{} is {}activated", address, !activated ? "not " : "");

        ACTIVATION_INFO.put(address, new ActivationInfo(activated));
        return activated;
    }
}
