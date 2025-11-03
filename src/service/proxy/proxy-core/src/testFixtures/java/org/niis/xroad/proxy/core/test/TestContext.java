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

package org.niis.xroad.proxy.core.test;

import org.apache.http.client.HttpClient;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.common.rpc.NoopVaultKeyProvider;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.common.vault.NoopVaultClient;
import org.niis.xroad.common.vault.NoopVaultKeyClient;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.monitor.rpc.MonitorRpcClient;
import org.niis.xroad.proxy.core.addon.messagelog.LogRecordManager;
import org.niis.xroad.proxy.core.addon.metaservice.clientproxy.MetadataHandler;
import org.niis.xroad.proxy.core.addon.opmonitoring.NoOpMonitoringBuffer;
import org.niis.xroad.proxy.core.antidos.AntiDosConfiguration;
import org.niis.xroad.proxy.core.clientproxy.AuthTrustVerifier;
import org.niis.xroad.proxy.core.clientproxy.ClientProxy;
import org.niis.xroad.proxy.core.clientproxy.ClientSoapMessageHandler;
import org.niis.xroad.proxy.core.clientproxy.ReloadingSSLSocketFactory;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;
import org.niis.xroad.proxy.core.configuration.ProxyClientConfig;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.messagelog.NullLogManager;
import org.niis.xroad.proxy.core.serverproxy.ServerProxy;
import org.niis.xroad.proxy.core.serverproxy.ServiceHandlerLoader;
import org.niis.xroad.proxy.core.test.util.ListInstanceWrapper;
import org.niis.xroad.proxy.core.util.CertHashBasedOcspResponderClient;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;
import org.niis.xroad.test.globalconf.TestGlobalConfWrapper;
import org.niis.xroad.test.serverconf.TestServerConfWrapper;

import java.util.List;

import static org.mockito.Mockito.mock;

public class TestContext {
    TestGlobalConfWrapper globalConfProvider = new TestGlobalConfWrapper(new TestSuiteGlobalConf());
    OcspVerifierFactory ocspVerifierFactory = new OcspVerifierFactory();
    KeyConfProvider keyConfProvider = new TestSuiteKeyConf(globalConfProvider);
    TestServerConfWrapper serverConfProvider = new TestServerConfWrapper(new TestSuiteServerConf());

    public ServerProxy serverProxy;
    ClientProxy clientProxy;

    public TestContext(ProxyProperties proxyProperties, CommonProperties commonProperties) {
        this(proxyProperties, commonProperties, true);
    }

    public TestContext(ProxyProperties proxyProperties, CommonProperties commonProperties, boolean startServerProxy) {
        this(proxyProperties, commonProperties, startServerProxy, mock(MonitorRpcClient.class));
    }

    public TestContext(ProxyProperties proxyProperties, CommonProperties commonProperties,
                       boolean startServerProxy, MonitorRpcClient monitorRpcClient) {
        try {
            org.apache.xml.security.Init.init();
            SigningCtxProvider signingCtxProvider = new TestSuiteSigningCtxProvider(globalConfProvider, keyConfProvider);

            CertHelper certHelper = new CertHelper(globalConfProvider, ocspVerifierFactory);
            AuthTrustVerifier authTrustVerifier = new AuthTrustVerifier(mock(CertHashBasedOcspResponderClient.class),
                    globalConfProvider, keyConfProvider, certHelper);
            LogRecordManager logRecordManager = mock(LogRecordManager.class);
            VaultKeyProvider vaultKeyProvider = mock(NoopVaultKeyProvider.class);
            CommonBeanProxy commonBeanProxy = new CommonBeanProxy(globalConfProvider, serverConfProvider,
                    keyConfProvider, signingCtxProvider, certHelper, logRecordManager, vaultKeyProvider, new NoOpMonitoringBuffer(),
                    proxyProperties, ocspVerifierFactory, commonProperties);

            ReloadingSSLSocketFactory reloadingSSLSocketFactory = new ReloadingSSLSocketFactory(globalConfProvider, keyConfProvider);
            HttpClient httpClient = new ProxyClientConfig.ProxyHttpClientInitializer()
                    .proxyHttpClient(proxyProperties, authTrustVerifier, reloadingSSLSocketFactory);
            MetadataHandler metadataHandler = new MetadataHandler(commonBeanProxy, httpClient);
            ClientSoapMessageHandler soapMessageHandler = new ClientSoapMessageHandler(commonBeanProxy, httpClient);

            clientProxy = new ClientProxy(serverConfProvider, proxyProperties.clientProxy(), reloadingSSLSocketFactory,
                    new ListInstanceWrapper<>(List.of(metadataHandler, soapMessageHandler)));
            clientProxy.init();

            if (startServerProxy) {
                AntiDosConfiguration antiDosConfiguration = mock(AntiDosConfiguration.class);
                ServiceHandlerLoader serviceHandlerLoader = new ServiceHandlerLoader(serverConfProvider, globalConfProvider,
                        monitorRpcClient, commonProperties, proxyProperties);
                serverProxy = new ServerProxy(proxyProperties, antiDosConfiguration, commonBeanProxy, serviceHandlerLoader,
                        new NoopVaultClient(), new NoopVaultKeyClient());
                serverProxy.init();
            }

            MessageLog.init(new NullLogManager(globalConfProvider, serverConfProvider));
        } catch (Exception e) {
            throw new RuntimeException("Init failed", e);
        }
    }

    public void destroy() {
        if (serverProxy != null) {
            try {
                serverProxy.destroy();
            } catch (Exception ignored) {
            }
        }

        try {
            clientProxy.destroy();
        } catch (Exception ignored) {
        }
    }

}
