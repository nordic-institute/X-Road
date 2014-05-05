package ee.cyber.sdsb.proxy.serverproxy;

import java.io.InputStream;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.cert.CertHelper;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.message.SoapMessageDecoder;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.monitoring.MessageInfo;
import ee.cyber.sdsb.common.monitoring.MessageInfo.Origin;
import ee.cyber.sdsb.common.monitoring.MonitorAgent;
import ee.cyber.sdsb.common.util.HttpSender;
import ee.cyber.sdsb.proxy.conf.KeyConf;
import ee.cyber.sdsb.proxy.conf.ServerConf;
import ee.cyber.sdsb.proxy.protocol.ProxyMessage;
import ee.cyber.sdsb.proxy.protocol.ProxyMessageDecoder;
import ee.cyber.sdsb.proxy.protocol.ProxyMessageEncoder;
import ee.cyber.sdsb.proxy.securelog.SecureLog;
import ee.cyber.sdsb.proxy.util.MessageProcessorBase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

class ServerMessageProcessor extends MessageProcessorBase {

    private static final Logger LOG =
            LoggerFactory.getLogger(ServerMessageProcessor.class);

    private final X509Certificate sslClientCert;

    private ProxyMessage requestMessage;
    private ServiceId requestServiceId;
    private SoapMessageImpl responseSoap;

    private ProxyMessageDecoder decoder;
    private ProxyMessageEncoder encoder;

    ServerMessageProcessor(HttpServletRequest servletRequest,
            HttpServletResponse servletResponse, HttpClient httpClient,
            X509Certificate sslClientCert) {
        super(servletRequest, servletResponse, httpClient);

        this.sslClientCert = sslClientCert;
    }

    @Override
    protected void process() throws Exception {
        preprocess();

        LOG.info("process({})", servletRequest.getContentType());
        try {
            cacheConfigurationForCurrentThread();

            readMessage();

            verifyAccess();
            verifySignature();

            logSignature();

            processRequest();

            sign();

            close();

            onSuccess(requestMessage.getSoap());
        } catch (Exception ex) {
            handleException(ex);
        }
    }

    private void preprocess() throws Exception {
        encoder = new ProxyMessageEncoder(servletResponse.getOutputStream());
        servletResponse.setContentType(encoder.getContentType());
    }

    private void readMessage() throws Exception {
        LOG.trace("readMessage()");

        requestMessage = new ProxyMessage() {
            @Override
            public void soap(SoapMessageImpl soapMessage) throws Exception {
                super.soap(soapMessage);

                requestServiceId =
                        GlobalConf.getServiceId(soapMessage.getService());

                if (SystemProperties.isSslEnabled()) {
                    verifySslClientCert();
                }
            }
        };

        decoder = new ProxyMessageDecoder(requestMessage,
                servletRequest.getContentType(), false);
        try {
            decoder.parse(servletRequest.getInputStream());
        } catch (CodedException ex) {
            throw ex.withPrefix(X_SERVICE_FAILED_X);
        }

        // Check if the input contained all the required bits.
        checkRequest();
    }

    private void checkRequest() throws Exception {
        if (requestMessage.getSoap() == null) {
            throw new CodedException(
                    X_MISSING_SOAP, "Request does not have SOAP message");
        }

        if (requestMessage.getSignature() == null) {
            throw new CodedException(
                    X_MISSING_SIGNATURE, "Request does not have signature");
        }
    }

    private void verifySslClientCert() throws Exception {
        LOG.trace("verifySslClientCert()");

        if (requestMessage.getOcspResponse() == null) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Cannot verify SSL certificate, corresponding " +
                    "OCSP response is missing");
        }

        try {
            CertHelper.verifyAuthCert(sslClientCert, null, /* no other certs */
                    Arrays.asList(requestMessage.getOcspResponse()),
                    requestMessage.getSoap().getClient(),
                    ServerConf.getIdentifier());
        } catch (Exception e) {
            throw new CodedException(X_SSL_AUTH_FAILED, e);
        }
    }

    private void verifyAccess() throws Exception {
        LOG.trace("verifyAccess()");

        if (!ServerConf.serviceExists(requestServiceId)) {
            throw new CodedException(X_UNKNOWN_SERVICE,
                    "Unknown service: %s", requestServiceId);
        }

        verifySecurityCategory(requestServiceId);

        if (!ServerConf.isQueryAllowed(requestMessage.getSoap().getClient(),
                requestServiceId)) {
            throw new CodedException(X_ACCESS_DENIED,
                    "Request is not allowed: %s", requestServiceId);
        }

        String disabledNotice = ServerConf.getDisabledNotice(requestServiceId);
        if (disabledNotice != null) {
            throw new CodedException(X_SERVICE_DISABLED,
                    "Service %s is disabled: %s", requestServiceId,
                    disabledNotice);
        }
    }

    private void verifySecurityCategory(ServiceId service) throws Exception {
        Collection<SecurityCategoryId> required =
                ServerConf.getRequiredCategories(service);

        if (required == null || required.isEmpty()) {
            // Service requires nothing, we are satisfied.
            return;
        }

        Collection<SecurityCategoryId> provided =
                GlobalConf.getProvidedCategories(sslClientCert);

        for (SecurityCategoryId cat: required) {
            if (provided.contains(cat)) {
                return; // All OK.
            }
        }

        throw new CodedException(X_SECURITY_CATEGORY,
                "Service requires security categories (%s), " +
                        "but client only satisfies (%s)",
                StringUtils.join(required, ", "),
                StringUtils.join(provided, ", "));
    }

    private void verifySignature() throws Exception {
        LOG.trace("verifySignature()");

        decoder.verify(requestMessage.getSoap().getClient(),
                requestMessage.getSignature(), GlobalConf.getVerificationCtx());
    }

    private void logSignature() throws Exception {
        LOG.trace("logSignature()");

        SecureLog.logSignature(requestMessage.getSoap(),
                requestMessage.getSignature());
    }

    private void processRequest() throws Exception {
        LOG.trace("processRequest({})", requestServiceId);

        String serviceAddress = ServerConf.getServiceAddress(requestServiceId);
        if (serviceAddress == null || serviceAddress.isEmpty()) {
            throw new CodedException(X_SERVICE_MISSING_URL,
                    "Service address not specified for '%s'", requestServiceId);
        }

        int serviceTimeout = ServerConf.getServiceTimeout(requestServiceId);
        try (HttpSender httpSender = createHttpSender()) {
            httpSender.setTimeout(serviceTimeout * 1000); // to milliseconds
            sendRequest(serviceAddress, httpSender);
            parseResponse(httpSender);
        } catch (Exception ex) {
            throw translateException(ex).withPrefix(X_SERVICE_FAILED_X);
        }
    }

    private void sendRequest(String serviceAddress, HttpSender httpSender)
            throws Exception {
        LOG.trace("sendRequest({})", serviceAddress);

        URI address = new URI(serviceAddress);
        httpSender.doPost(address, requestMessage.getSoapContent(),
                requestMessage.getSoapContentType());
    }

    private void parseResponse(HttpSender httpSender) throws Exception {
        LOG.trace("parseResponse()");

        SoapMessageDecoder soapMessageDecoder =
                new SoapMessageDecoder(httpSender.getResponseContentType(),
                        new SoapMessageHandler());
        soapMessageDecoder.parse(httpSender.getResponseContent());

        // If we did not parse a response message (empty response
        // from server?), it is an error instead.
        if (responseSoap == null) {
            throw new CodedException(X_INVALID_MESSAGE,
                "No response message received from service");
        }
    }

    private void sign() throws Exception {
        ClientId memberId = requestServiceId.getClientId();
        LOG.trace("sign({})", memberId);

        encoder.sign(KeyConf.getSigningCtx(memberId));
    }

    private void close() throws Exception {
        LOG.trace("close()");

        encoder.close();
    }

    private void handleException(Exception ex) throws Exception {
        CodedException translated =
                translateWithPrefix(SERVER_SERVERPROXY_X, ex);

        monitorAgentNotifyFailure(translated);

        encoder.fault(SoapFault.createFaultXml(translated));
    }

    private void monitorAgentNotifyFailure(CodedException ex) {
        MessageInfo info = null;

        boolean requestIsComplete = requestMessage != null
                && requestMessage.getSoap() != null
                && requestMessage.getSignature() != null;

        // Include the request message only if the error was caused while
        // exchanging information with the adapter server.
        if (requestIsComplete && ex.getFaultCode().startsWith(
                SERVER_SERVERPROXY_X + "." + X_SERVICE_FAILED_X)) {
            info = createRequestMessageInfo();
        }

        MonitorAgent.failure(info, ex.getFaultCode(), ex.getFaultString());
    }

    protected MessageInfo createRequestMessageInfo() {
        SoapMessageImpl soap = requestMessage.getSoap();
        return new MessageInfo(Origin.SERVER_PROXY, soap.getClient(),
                requestServiceId, soap.getUserId(), soap.getQueryId());
    }

    private class SoapMessageHandler implements SoapMessageDecoder.Callback {
        @Override
        public void soap(SoapMessage message) throws Exception {
            responseSoap = (SoapMessageImpl) message;
            encoder.soap(responseSoap);
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            encoder.attachment(contentType, content, additionalHeaders);
        }

        @Override
        public void onCompleted() {
            // Do nothing.
        }

        @Override
        public void onError(Exception t) throws Exception {
            throw t;
        }
    }
}
