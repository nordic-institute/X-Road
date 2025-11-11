/*
 * The MIT License
 *
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

import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.http.client.HttpClient;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.messagelog.MessageRecordEncryption;
import org.niis.xroad.common.messagelog.archive.EncryptionConfigProvider;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.addon.messagelog.LogRecordManager;
import org.niis.xroad.proxy.core.addon.messagelog.clientproxy.AsicContainerClientRequestProcessor;
import org.niis.xroad.proxy.core.addon.metaservice.clientproxy.MetadataClientRequestProcessor;
import org.niis.xroad.proxy.core.clientproxy.ClientRestMessageProcessor;
import org.niis.xroad.proxy.core.clientproxy.ClientSoapMessageProcessor;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.serverproxy.ServerRestMessageProcessor;
import org.niis.xroad.proxy.core.serverproxy.ServerSoapMessageProcessor;
import org.niis.xroad.proxy.core.serverproxy.ServiceHandlerLoader;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;

import static org.niis.xroad.proxy.core.configuration.ProxyClientConfig.CLIENT_PROXY_HTTP_CLIENT;
import static org.niis.xroad.proxy.core.configuration.ServerProxyConfig.SERVER_PROXY_HTTP_CLIENT;

@ApplicationScoped
public class MessageProcessorFactory {

    private final HttpClient proxyHttpClient;
    private final HttpClient serverProxyHttpClient;
    private final ProxyProperties proxyProperties;
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfProvider serverConfProvider;
    private final KeyConfProvider keyConfProvider;
    private final SigningCtxProvider signingCtxProvider;
    private final OcspVerifierFactory ocspVerifierFactory;
    private final CommonProperties commonProperties;
    private final LogRecordManager logRecordManager;
    private final ServiceHandlerLoader serviceHandlerLoader;
    private final CertHelper certHelper;
    private final ConfClientRpcClient confClientRpcClient;
    private final ClientAuthenticationService clientAuthenticationService;
    private final EncryptionConfigProvider encryptionConfigProvider;
    private final MessageRecordEncryption messageRecordEncryption;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public MessageProcessorFactory(@Named(CLIENT_PROXY_HTTP_CLIENT) HttpClient proxyHttpClient,
                                   @Named(SERVER_PROXY_HTTP_CLIENT) HttpClient serverProxyHttpClient,
                                   ProxyProperties proxyProperties, GlobalConfProvider globalConfProvider,
                                   ServerConfProvider serverConfProvider, ClientAuthenticationService clientAuthenticationService,
                                   KeyConfProvider keyConfProvider, SigningCtxProvider signingCtxProvider,
                                   OcspVerifierFactory ocspVerifierFactory, CommonProperties commonProperties,
                                   LogRecordManager logRecordManager, ConfClientRpcClient confClientRpcClient,
                                   ServiceHandlerLoader serviceHandlerLoader, CertHelper certHelper,
                                   EncryptionConfigProvider encryptionConfigProvider, MessageRecordEncryption messageRecordEncryption) {
        this.proxyHttpClient = proxyHttpClient;
        this.serverProxyHttpClient = serverProxyHttpClient;
        this.proxyProperties = proxyProperties;
        this.globalConfProvider = globalConfProvider;
        this.serverConfProvider = serverConfProvider;
        this.clientAuthenticationService = clientAuthenticationService;
        this.keyConfProvider = keyConfProvider;
        this.signingCtxProvider = signingCtxProvider;
        this.ocspVerifierFactory = ocspVerifierFactory;
        this.commonProperties = commonProperties;
        this.logRecordManager = logRecordManager;
        this.confClientRpcClient = confClientRpcClient;
        this.serviceHandlerLoader = serviceHandlerLoader;
        this.certHelper = certHelper;
        this.encryptionConfigProvider = encryptionConfigProvider;
        this.messageRecordEncryption = messageRecordEncryption;
    }

    public ClientSoapMessageProcessor createClientSoapMessageProcessor(RequestWrapper request, ResponseWrapper response,
                                                                       OpMonitoringData opMonitoringData) {
        try {
            return new ClientSoapMessageProcessor(request, response,
                    proxyProperties, globalConfProvider, serverConfProvider, clientAuthenticationService, keyConfProvider,
                    signingCtxProvider, ocspVerifierFactory, commonProperties.tempFilesPath(),
                    proxyHttpClient, opMonitoringData);
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    public ClientRestMessageProcessor createClientRestMessageProcessor(RequestWrapper request, ResponseWrapper response,
                                                                       OpMonitoringData opMonitoringData) {
        return new ClientRestMessageProcessor(request, response,
                proxyProperties, globalConfProvider, serverConfProvider, clientAuthenticationService, keyConfProvider, signingCtxProvider,
                ocspVerifierFactory, commonProperties.tempFilesPath(),
                proxyHttpClient, opMonitoringData);
    }

    public AsicContainerClientRequestProcessor createAsicContainerClientRequestProcessor(RequestWrapper request, ResponseWrapper response,
                                                                                         String target) {
        return new AsicContainerClientRequestProcessor(confClientRpcClient, encryptionConfigProvider,
                proxyProperties, globalConfProvider, serverConfProvider,
                logRecordManager, commonProperties.tempFilesPath(), messageRecordEncryption,
                target, request, response, clientAuthenticationService);
    }

    public MetadataClientRequestProcessor createMetadataClientRequestProcessor(RequestWrapper request, ResponseWrapper response,
                                                                               String target) {
        return new MetadataClientRequestProcessor(proxyProperties, globalConfProvider, serverConfProvider, clientAuthenticationService,
                target, request, response);
    }

    // ------ SERVER PROXY --------------

    public ServerRestMessageProcessor createServerRestMessageProcessor(RequestWrapper request, ResponseWrapper response,
                                                                       OpMonitoringData opMonitoringData) {
        return new ServerRestMessageProcessor(request, response,
                proxyProperties, globalConfProvider, serverConfProvider, clientAuthenticationService,
                signingCtxProvider, ocspVerifierFactory, certHelper, commonProperties.tempFilesPath(),
                serverProxyHttpClient, opMonitoringData, serviceHandlerLoader);
    }

    public ServerSoapMessageProcessor createServerSoapMessageProcessor(RequestWrapper request, ResponseWrapper response,
                                                                       OpMonitoringData opMonitoringData) {
        return new ServerSoapMessageProcessor(request, response,
                proxyProperties, globalConfProvider, serverConfProvider, clientAuthenticationService,
                signingCtxProvider, ocspVerifierFactory, certHelper, commonProperties.tempFilesPath(),
                serverProxyHttpClient, opMonitoringData, serviceHandlerLoader);
    }

}
