/*
 * The MIT License
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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.TimeUtils;

import jakarta.inject.Singleton;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.SOAPException;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_SOAP_ACTION;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_MALFORMED_URL;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_MISSING_URL;

/**
 * Default SOAP service handler that forwards the request to the configured service address.
 * This is a top-level {@link Singleton} CDI singleton — all per-request state is
 * kept in method-local variables and returned via {@link ServiceHandlerResult}.
 */
@Slf4j
@Singleton
public class DefaultServiceHandlerImpl extends AbstractServiceHandler {

    private final HttpSenderProvider httpSenderProvider;

    public DefaultServiceHandlerImpl(ServerConfProvider serverConfProvider,
                                     GlobalConfProvider globalConfProvider,
                                     HttpSenderProvider httpSenderProvider) {
        super(serverConfProvider, globalConfProvider);
        this.httpSenderProvider = httpSenderProvider;
    }

    @Override
    public boolean shouldVerifyAccess(ProxyMessage requestMessage) {
        return true;
    }

    @Override
    public boolean shouldVerifySignature() {
        return true;
    }

    @Override
    public boolean shouldLogSignature() {
        return true;
    }

    @Override
    public boolean canHandle(ServiceId requestSrvcId, ProxyMessage requestProxyMessage) {
        return true;
    }

    @Override
    public ServiceHandlerResult startHandling(RequestWrapper request, ProxyMessage proxyRequestMessage,
                                              OpMonitoringData monitoringData)
            throws SOAPException, JAXBException, IOException, URISyntaxException,
            HttpClientCreator.HttpClientCreatorException, ParserConfigurationException, SAXException {
        var sender = httpSenderProvider.createServerHttpSender();

        var requestServiceId = proxyRequestMessage.getSoap().getService();
        log.trace("processRequest({})", requestServiceId);

        String address = serverConfProvider.getServiceAddress(requestServiceId);

        if (address == null || address.isEmpty()) {
            throw XrdRuntimeException.systemException(SERVICE_MISSING_URL,
                    "Service address not specified for '%s'".formatted(requestServiceId));
        }

        int timeout = TimeUtils.secondsToMillis(serverConfProvider.getServiceTimeout(requestServiceId));

        sender.setConnectionTimeout(timeout);
        sender.setSocketTimeout(timeout);
        sender.setAttribute(ServiceId.class.getName(), requestServiceId);

        sender.addHeader("accept-encoding", "");
        // Read original SOAP action from request headers directly (avoids VerifyingProxyMessage cast)
        var originalSoapAction = SoapUtils.validateSoapActionHeader(
                request.getHeaders().get(HEADER_ORIGINAL_SOAP_ACTION));
        sender.addHeader("SOAPAction", originalSoapAction);

        sendRequest(address, sender, proxyRequestMessage, monitoringData);

        return new ServiceHandlerResult(sender.getResponseContentType(), sender.getResponseContent(), sender);
    }

    private void sendRequest(String serviceAddress, HttpSender httpSender, ProxyMessage requestMessage,
                             OpMonitoringData opMonitoringData) {
        log.trace("sendRequest({})", serviceAddress);

        URI uri;
        try {
            uri = new URI(serviceAddress);
        } catch (URISyntaxException e) {
            throw XrdRuntimeException.systemException(SERVICE_MALFORMED_URL,
                    "Malformed service address '%s': %s".formatted(serviceAddress, e.getMessage()));
        }

        log.info("Sending request to {}", uri);
        try {
            opMonitoringData.setRequestOutTs(getEpochMillisecond());
            httpSender.doPost(uri, new ProxyMessageSoapEntity(requestMessage));
            opMonitoringData.setResponseInTs(getEpochMillisecond());
        } catch (Exception ex) {
            if (ex instanceof XrdRuntimeException) {
                opMonitoringData.setResponseInTs(getEpochMillisecond());
            }
            throw translateException(ex).withPrefix(X_SERVICE_FAILED_X);
        }
    }
}
