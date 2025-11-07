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
package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;
import ee.ria.xroad.common.util.UriUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.DescriptionType;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * Base class for message processors.
 */
@Slf4j
@ArchUnitSuppressed("NoVanillaExceptions")
public abstract class MessageProcessorBase {
    protected final ProxyProperties proxyProperties;
    protected final GlobalConfProvider globalConfProvider;
    protected final ServerConfProvider serverConfProvider;
    protected final ClientAuthenticationService clientAuthenticationService;

    /**
     * The servlet request.
     */
    protected final RequestWrapper jRequest;

    /**
     * The servlet response.
     */
    protected final ResponseWrapper jResponse;

    /**
     * The http client instance.
     */
    protected final HttpClient httpClient;

    protected MessageProcessorBase(RequestWrapper request, ResponseWrapper response,
                                   ProxyProperties proxyProperties, GlobalConfProvider globalConfProvider,
                                   ServerConfProvider serverConfProvider, ClientAuthenticationService clientAuthenticationService,
                                   HttpClient httpClient) {
        this.proxyProperties = proxyProperties;
        this.globalConfProvider = globalConfProvider;
        this.serverConfProvider = serverConfProvider;
        this.jRequest = request;
        this.jResponse = response;
        this.httpClient = httpClient;
        this.clientAuthenticationService = clientAuthenticationService;

        this.globalConfProvider.verifyValidity();
    }

    /**
     * Returns a new instance of http sender.
     */
    protected HttpSender createHttpSender() {
        return new HttpSender(httpClient, proxyProperties.clientProxy().poolEnableConnectionReuse());
    }

    /**
     * Called when processing started.
     */
    protected void preprocess() {
    }

    /**
     * Called when processing successfully completed.
     */
    protected void postprocess() {
    }

    /**
     * Processes the incoming message.
     *
     * @throws Exception in case of any errors
     */
    public abstract void process() throws Exception;

    /**
     * Update operational monitoring data with SOAP message header data and
     * the size of the message.
     *
     * @param opMonitoringData monitoring data to update
     * @param soapMessage      SOAP message
     */
    protected void updateOpMonitoringDataBySoapMessage(OpMonitoringData opMonitoringData, SoapMessageImpl soapMessage) {
        if (opMonitoringData != null && soapMessage != null) {
            opMonitoringData.setClientId(soapMessage.getClient());
            opMonitoringData.setServiceId(soapMessage.getService());
            opMonitoringData.setMessageId(soapMessage.getQueryId());
            opMonitoringData.setMessageUserId(soapMessage.getUserId());
            opMonitoringData.setMessageIssue(soapMessage.getIssue());
            opMonitoringData.setRepresentedParty(soapMessage.getRepresentedParty());
            opMonitoringData.setMessageProtocolVersion(soapMessage.getProtocolVersion());
            opMonitoringData.setServiceType(DescriptionType.WSDL.name());
            opMonitoringData.setRequestSize(soapMessage.getBytes().length);
            opMonitoringData.setXRoadVersion(Version.XROAD_VERSION);
        }
    }

    /**
     * Update operational monitoring data with REST message header data
     */
    protected void updateOpMonitoringDataByRestRequest(OpMonitoringData opMonitoringData, RestRequest request) {
        if (opMonitoringData != null && request != null) {
            opMonitoringData.setClientId(request.getSender());
            opMonitoringData.setServiceId(request.getServiceId());
            opMonitoringData.setMessageId(request.getQueryId());
            opMonitoringData.setMessageUserId(request.findHeaderValueByName(MimeUtils.HEADER_USER_ID));
            opMonitoringData.setMessageIssue(request.findHeaderValueByName(MimeUtils.HEADER_ISSUE));
            opMonitoringData.setRepresentedParty(request.getRepresentedParty());
            opMonitoringData.setMessageProtocolVersion(String.valueOf(request.getVersion()));
            opMonitoringData.setServiceType(Optional
                    .ofNullable(serverConfProvider.getDescriptionType(request.getServiceId()))
                    .orElse(DescriptionType.REST).name());
            opMonitoringData.setRestMethod(request.getVerb().name());
            // we log rest path data only for PRODUCER
            opMonitoringData.setRestPath(opMonitoringData.isProducer()
                    ? getNormalizedServicePath(request.getServicePath()) : null);
            opMonitoringData.setXRoadVersion(Version.XROAD_VERSION);
        }
    }

    private String getNormalizedServicePath(String servicePath) {
        return Optional.of(UriUtils.uriPathPercentDecode(URI.create(servicePath).normalize().getRawPath(), true))
                .orElse(servicePath);
    }

    /**
     * Check that message transfer was successful.
     */
    public boolean verifyMessageExchangeSucceeded() {
        return true;
    }

    private String getSecurityServerAddress() {
        return globalConfProvider.getSecurityServerAddress(serverConfProvider.getIdentifier());
    }

    protected void updateOpMonitoringClientSecurityServerAddress(OpMonitoringData opMonitoringData) {
        try {
            opMonitoringData.setClientSecurityServerAddress(getSecurityServerAddress());
        } catch (Exception e) {
            log.error("Failed to assign operational monitoring data field {}",
                    OpMonitoringData.CLIENT_SECURITY_SERVER_ADDRESS, e);
        }
    }

    protected void updateOpMonitoringServiceSecurityServerAddress(OpMonitoringData opMonitoringData) {
        try {
            opMonitoringData.setServiceSecurityServerAddress(getSecurityServerAddress());
        } catch (Exception e) {
            log.error("Failed to assign operational monitoring data field {}",
                    OpMonitoringData.SERVICE_SECURITY_SERVER_ADDRESS, e);
        }
    }

    protected void updateOpMonitoringClientSecurityServerAddress(OpMonitoringData opMonitoringData, X509Certificate authCert) {
        try {
            if (authCert != null) {
                opMonitoringData.setClientSecurityServerAddress(globalConfProvider.getSecurityServerAddress(
                        globalConfProvider.getServerId(authCert)));
            }
        } catch (Exception e) {
            log.error("Failed to assign operational monitoring data field {}",
                    OpMonitoringData.CLIENT_SECURITY_SERVER_ADDRESS, e);
        }
    }

}
