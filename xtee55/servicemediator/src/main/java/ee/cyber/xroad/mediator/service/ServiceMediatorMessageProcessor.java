package ee.cyber.xroad.mediator.service;

import java.net.URI;

import org.apache.commons.lang.StringUtils;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.util.AsyncHttpSender;
import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.common.AbstractMediatorMessageProcessor;
import ee.cyber.xroad.mediator.common.HttpClientManager;
import ee.cyber.xroad.mediator.message.MessageVersion;
import ee.cyber.xroad.mediator.message.XRoadListMethods;
import ee.cyber.xroad.mediator.message.XRoadSoapMessageImpl;
import ee.cyber.xroad.mediator.message.XRoadTestSystem;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.xroad.mediator.message.MessageVersion.SDSB;
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
        // SDSB identifier.
        service = getServiceId(inboundRequestMessage);

        // Verify that the service is enabled. If it is disabled,
        // throw exception with notice.
        verifyServiceEnabled();

        // If the service is SDSB service and the message is X-Road 5.0
        // message, then convert the message to SDSB message
        if (MediatorServerConf.isSdsbService(service)) {
            LOG.debug("Target service '{}' is SDSB service", service);

            if (inboundRequestVersion == XROAD50) {
                return getMessageConverter().sdsbSoapMessage(
                        (XRoadSoapMessageImpl) inboundRequestMessage, false);
            } else if (inboundRequestVersion == SDSB) {
                return getMessageConverter().removeXRoadHeaders(
                        (SoapMessageImpl) inboundRequestMessage);
            }
        } else {
            LOG.debug("Target service '{}' is X-Road 5.0 service", service);

            // If the service is X-Road 5.0 service and the message is SDSB
            // message, then convert the message to X-Road 5.0 message.
            if (inboundRequestVersion == SDSB) {
                // If the message is SDSB CentralService, throw exception
                // since we do not support X-Road 5.0 services with CentralService.
                if (((SoapMessageImpl) inboundRequestMessage).getService()
                        instanceof CentralServiceId) {
                    throw new CodedException(X_INTERNAL_ERROR,
                            "Central services are not supported "
                                    + "for X-Road 5.0 adapter servers");
                }

                return getMessageConverter().xroadSoapMessage(
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

        if (inboundRequestVersion == SDSB
                && outboundRequestVersion == XROAD50) {
            return getMessageConverter().sdsbSoapMessage(
                    (XRoadSoapMessageImpl) inboundResponseMessage, true);
        } else if (inboundRequestVersion == XROAD50
                && outboundRequestVersion == SDSB) {
            return getMessageConverter().xroadSoapMessage(
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

        String sdsbInstance = getRequestParameter("sdsbInstance");
        String memberClass = getRequestParameter("memberClass");
        String memberCode = getRequestParameter("memberCode");
        String subsystemCode = getRequestParameter("subsystemCode");
        return ClientId.create(sdsbInstance, memberClass, memberCode,
                subsystemCode);
    }

    private ServiceId getServiceId(SoapMessage soap) throws Exception {
        if (soap instanceof XRoadSoapMessageImpl) {
            XRoadSoapMessageImpl xroadSoap = (XRoadSoapMessageImpl) soap;
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
        return message instanceof XRoadListMethods
                || message instanceof XRoadTestSystem;
    }

    @Override
    protected boolean shouldSendWithHttp10(SoapMessage message) {
        MessageVersion version =
                MessageVersion.fromMessage(message);
        return version != MessageVersion.SDSB;
    }
}
