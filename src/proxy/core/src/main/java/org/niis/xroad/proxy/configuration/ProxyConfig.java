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
package org.niis.xroad.proxy.configuration;

import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.cert.CertHelper;
import ee.ria.xroad.common.conf.globalconf.GlobalConfBeanConfig;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.globalconf.GlobalConfRefreshJobConfig;
import ee.ria.xroad.common.conf.serverconf.ServerConfFactory;
import ee.ria.xroad.common.conf.serverconf.ServerConfProperties;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.db.DatabaseCtxV2;
import ee.ria.xroad.common.opmonitoring.AbstractOpMonitoringBuffer;
import ee.ria.xroad.common.signature.SimpleSigner;
import ee.ria.xroad.proxy.ProxyAddonConfig;
import ee.ria.xroad.proxy.ProxyAdminPortConfig;
import ee.ria.xroad.proxy.ProxyDiagnosticsConfig;
import ee.ria.xroad.proxy.ProxyJobConfig;
import ee.ria.xroad.proxy.ProxyMessageLogConfig;
import ee.ria.xroad.proxy.ProxyRpcConfig;
import ee.ria.xroad.proxy.antidos.AntiDosConfiguration;
import ee.ria.xroad.proxy.clientproxy.AuthTrustVerifier;
import ee.ria.xroad.proxy.conf.CachingKeyConfImpl;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.conf.SigningCtxProvider;
import ee.ria.xroad.proxy.opmonitoring.NullOpMonitoringBuffer;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoring;
import ee.ria.xroad.proxy.serverproxy.ServerProxy;
import ee.ria.xroad.proxy.serverproxy.ServiceHandlerLoader;
import ee.ria.xroad.proxy.util.CertHashBasedOcspResponder;
import ee.ria.xroad.proxy.util.CertHashBasedOcspResponderClient;
import ee.ria.xroad.signer.SignerClientConfiguration;
import ee.ria.xroad.signer.SignerRpcClient;

import org.niis.xroad.common.rpc.server.RpcServerConfig;
import org.niis.xroad.confclient.proto.ConfClientRpcClientConfiguration;
import org.niis.xroad.proxy.ProxyProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

//TODO  This is getting out of hand, refactor to be more readable.
@Import({
        ProxyRpcConfig.class,
        ProxyAdminPortConfig.class,
        ProxyAddonConfig.class,
        ProxyDiagnosticsConfig.class,
        ProxyJobConfig.class,
        ProxyMessageLogConfig.class,
        ProxyClientConfig.class,
        GlobalConfBeanConfig.class,
        GlobalConfRefreshJobConfig.class,
        SignerClientConfiguration.class,
        ConfClientRpcClientConfiguration.class,
        ProxyEdcControlPlaneConfig.class,
        RpcServerConfig.class
})
@ComponentScan("org.niis.xroad.proxy.edc")
@EnableConfigurationProperties({
        ProxyProperties.class,
        AntiDosConfiguration.class,
        ServerConfProperties.class
})
@Configuration
public class ProxyConfig {

    @Bean
    CertHelper certHelper(GlobalConfProvider globalConfProvider) {
        return new CertHelper(globalConfProvider);
    }

    @Bean
    CertChainFactory certChainFactory(GlobalConfProvider globalConfProvider) {
        return new CertChainFactory(globalConfProvider);
    }

    @Bean
    AuthTrustVerifier authTrustVerifier(CertHashBasedOcspResponderClient certHashBasedOcspResponderClient,
                                        KeyConfProvider keyConfProvider, CertHelper certHelper, CertChainFactory certChainFactory) {
        return new AuthTrustVerifier(certHashBasedOcspResponderClient, keyConfProvider, certHelper, certChainFactory);
    }

    @Bean
    ServerProxy serverProxy(ProxyProperties proxyProperties,
                            AntiDosConfiguration antiDosConfiguration,
                            GlobalConfProvider globalConfProvider,
                            KeyConfProvider keyConfProvider,
                            ServerConfProvider serverConfProvider,
                            CertChainFactory certChainFactory,
                            ServiceHandlerLoader serviceHandlerLoader) throws Exception {
        return new ServerProxy(proxyProperties.getServer(), antiDosConfiguration,
                globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory, serviceHandlerLoader);
    }

    @Bean
    ServiceHandlerLoader serviceHandlerLoader(ApplicationContext applicationContext) {
        return new ServiceHandlerLoader(applicationContext);
    }

    @Bean
    CertHashBasedOcspResponder certHashBasedOcspResponder(ProxyProperties proxyProperties, KeyConfProvider keyConfProvider)
            throws Exception {
        return new CertHashBasedOcspResponder(proxyProperties.getOcspResponder(), keyConfProvider);
    }

    @Bean
    CertHashBasedOcspResponderClient certHashBasedOcspResponderClient(ProxyProperties proxyProperties) {
        return new CertHashBasedOcspResponderClient(proxyProperties.getOcspResponder());
    }

    @Bean
    OpMonitoring opMonitoringBuffer(AbstractOpMonitoringBuffer opMonitoringBuffer) throws Exception {
        return OpMonitoring.init(opMonitoringBuffer);
    }

    @Bean
    @ConditionalOnMissingBean
    AbstractOpMonitoringBuffer nullOpMonitoringBuffer(ServerConfProvider serverConfProvider) {
        return new NullOpMonitoringBuffer(serverConfProvider);
    }

    @Bean
    public ServerConfProvider serverConfProvider(GlobalConfProvider globalConfProvider, ServerConfProperties serverConfProperties,
                                                 @Qualifier("serverConfDatabaseCtx") DatabaseCtxV2 databaseCtx) {
        return ServerConfFactory.create(serverConfProperties, globalConfProvider, databaseCtx);
    }

    @Bean("serverConfDatabaseCtx")
    DatabaseCtxV2 serverConfDatabaseCtx(ServerConfProperties serverConfProperties) {
        return new DatabaseCtxV2("serverconf", serverConfProperties.hibernate());
    }

    @Bean
    KeyConfProvider keyConfProvider(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider,
                                    SignerRpcClient signerRpcClient) {
        return new CachingKeyConfImpl(globalConfProvider, serverConfProvider, signerRpcClient);
    }

    @Bean
    SimpleSigner simpleSigner(SignerRpcClient signerRpcClient) {
        var signer = new SimpleSigner(signerRpcClient);
        SigningCtxProvider.setSigner(signer);
        return signer;
    }
}
