package ee.cyber.xroad.mediator.service;

import java.net.URI;

import org.apache.commons.lang.StringUtils;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.AsyncHttpSender;
import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.common.AbstractMediatorMessageProcessor;
import ee.cyber.xroad.mediator.common.HttpClientManager;
import ee.cyber.xroad.mediator.message.MessageVersion;
import ee.cyber.xroad.mediator.message.V5XRoadListMethods;
import ee.cyber.xroad.mediator.message.V5XRoadSoapMessageImpl;
import ee.cyber.xroad.mediator.message.V5XRoadTestSystem;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.cyber.xroad.mediator.message.MessageVersion.XROAD60;
import static ee.cyber.xroad.mediator.message.MessageVersion.XROAD50;

class ServiceMediatorMessageProcessor extends AbstractMediatorMessageProcessor {

    private static final Logger LOG =
            LoggerFactory.getLogger(ServiceMediatorMessageProcessor.class);

    private ServiceId service;

    ServiceMediatorMessageProcessor(String target,
            HttpClientManager httpClientManager) throws Exception {
        super(target, httpClientManager);
    }

    @Override
    protected SoapMessage getOutboundRequestMessage(
            SoapMessage inboundRequestMessage) throws Exception {
        LOG.trace("getOutboundRequestMessage()");

        // If this message is an X-Road 5.0 Meta Service, we do not convert and
        // use the request target to find the service address
        if (isSpecialMetaMessage(inboundRequestMessage)) {
            return inboundRequestMessage;
        }

        // Get the service identifier from the message. If the message is
        // X-Road 5.0 message, convert the service short name to
        // X-Road 6.0 identifier.
        service = getServiceId(inboundRequestMessage);

        // Verify that the service is enabled. If it is disabled,
        // throw exception with notice.
        verifyServiceEnabled();

        // If the service is X-Road 6.0 service and the message is X-Road 5.0
        // message, then convert the message to X-Road 6.0 message
        if (MediatorServerConf.isXroadService(service)) {
            LOG.debug("Target service '{}' is X-Road 6.0 service", service);

            if (inboundRequestVersion == XROAD50) {
                return getMessageConverter().xroadSoapMessage(
                        (V5XRoadSoapMessageImpl) inboundRequestMessage, false);
            } else if (inboundRequestVersion == XROAD60) {
                return getMessageConverter().removeXRoadHeaders(
                        (SoapMessageImpl) inboundRequestMessage);
            }
        } else {
            LOG.debug("Target service '{}' is X-Road 5.0 service", service);

            // If the service is X-Road 5.0 service and the message is
            // X-Road 6.0 message, then convert the message to X-Road 5.0
            // message.
            if (inboundRequestVersion == XROAD60) {
                // If the message is X-Road 6.0 CentralService, throw exception
                // since we do not support X-Road 5.0 services with CentralService.
                if (((SoapMessageImpl) inboundRequestMessage).getService()
                        instanceof CentralServiceId) {
                    throw new CodedException(X_INTERNAL_ERROR,
                            "Central services are not supported "
                                    + "for X-Road 5.0 adapter servers");
                }

                return getMessageConverter().v5XroadSoapMessage(
                        (SoapMessageImpl) inboundRequestMessage);
            }
        }

        // No conversion necessary
        return inboundRequestMessage;
    }

    private void verifyServiceEnabled() {
        String disabledNotice = MediatorServerConf.getDisabledNotice(service);
        if (disabledNotice != null) {
            throw new CodedException(X_SERVICE_DISABLED,
                    "Service %s is disabled: %s", service, disabledNotice);
        }
    }

    @Override
    protected SoapMessage getOutboundResponseMessage(
            SoapMessage inboundResponseMessage) throws Exception {
        LOG.trace("getOutboundResponseMessage()");

        // Do not convert meta messages
        if (inboundRequestVersion == MessageVersion.XROAD50_META) {
            return inboundResponseMessage;
        }

        verifyRequestResponseMessageVersions();

        if (inboundRequestVersion == XROAD60
                && outboundRequestVersion == XROAD50) {
            return getMessageConverter().xroadSoapMessage(
                    (V5XRoadSoapMessageImpl) inboundResponseMessage, true);
        } else if (inboundRequestVersion == XROAD50
                && outboundRequestVersion == XROAD60) {
            return getMessageConverter().v5XroadSoapMessage(
                    (SoapMessageImpl) inboundResponseMessage,
                    inboundRequestHeaderClass);
        }

        // No conversion necessary
        return inboundResponseMessage;
    }

    @Override
    protected URI getTargetAddress(SoapMessage message) throws Exception {
        String url;
        if (isSpecialMetaMessage(message)) {
            ClientId client = getClientIdFromRequest();
            LOG.trace("getTargetAddress({})", client);

            url = MediatorServerConf.getBackendURL(client);
            if (url == null) {
                throw new CodedException(X_UNKNOWN_SERVICE,
                        "Could not find address for client: %s", client);
            }
        } else {
            LOG.trace("getTargetAddress({})", service);

            url = MediatorServerConf.getBackendURL(service);
            if (url == null) {
                throw new CodedException(X_UNKNOWN_SERVICE,
                        "Could not find address for service: %s", service);
            }
        }

        return new URI(url);
    }

    @Override
    protected CloseableHttpAsyncClient getHttpClient() {
        if (service != null) {
            return httpClientManager.getHttpClient(service.getClientId());
        }

        return httpClientManager.getDefaultHttpClient();
    }

    @Override
    protected AsyncHttpSender createSender() {
        AsyncHttpSender sender = new AsyncHttpSender(getHttpClient());

        if (service != null) {
            sender.setAttribute(ServerTrustVerifier.class.getName(),
                    new DefaultServerTrustVerifier(service));
        }

        sender.addHeader("accept-encoding", "");
        return sender;
    }

    @Override
    protected int getSendTimeoutSeconds() {
        if (service != null) {
            return MediatorServerConf.getServiceTimeout(service);
        }

        return super.getSendTimeoutSeconds();
    }

    private ClientId getClientIdFromRequest() throws Exception {
        if (!StringUtils.isEmpty(target)) {
            return getClientId(target);
        }

        String xRoadInstance = getRequestParameter("xRoadInstance");
        String memberClass = getRequestParameter("memberClass");
        String memberCode = getRequestParameter("memberCode");
        String subsystemCode = getRequestParameter("subsystemCode");
        return ClientId.create(xRoadInstance, memberClass, memberCode,
                subsystemCode);
    }

    private ServiceId getServiceId(SoapMessage soap) throws Exception {
        if (soap instanceof V5XRoadSoapMessageImpl) {
            V5XRoadSoapMessageImpl xroadSoap = (V5XRoadSoapMessageImpl) soap;
            return ServiceId.create(getClientId(xroadSoap.getProducer()),
                    xroadSoap.getServiceName(),
                    xroadSoap.getServiceVersion());
        }

        return GlobalConf.getServiceId(((SoapMessageImpl) soap).getService());
    }

    private ClientId getClientId(String shortName) throws Exception {
        ClientId clientId = getIdentifierMapping().getClientId(shortName);
        if (clientId == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "No mapping found for short name '%s'", shortName);
        }

        return clientId;
    }

    private static boolean isSpecialMetaMessage(SoapMessage message) {
        return message instanceof V5XRoadListMethods
                || message instanceof V5XRoadTestSystem;
    }

    @Override
    protected boolean shouldSendWithHttp10(SoapMessage message) {
        return MessageVersion.fromMessage(message) != MessageVersion.XROAD60;
    }
}
