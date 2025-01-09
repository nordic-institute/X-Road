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
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.opmonitoring.AbstractOpMonitoringBuffer;
import ee.ria.xroad.common.signature.SimpleSigner;
import ee.ria.xroad.proxy.conf.CachingKeyConfImpl;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.conf.SigningCtxProvider;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoring;
import ee.ria.xroad.signer.SignerRpcClient;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

//TODO  This is getting out of hand, refactor to be more readable.
//@Import({
//        ProxyRpcConfig.class,
//        ProxyAdminPortConfig.class,
//        ProxyAddonConfig.class,
//        ProxyDiagnosticsConfig.class,
//        ProxyJobConfig.class,
//        ProxyMessageLogConfig.class,
//        ProxyClientConfig.class,
//        GlobalConfBeanConfig.class,
//        GlobalConfRefreshJobConfig.class,
//        ServerConfBeanConfig.class,
//        SignerClientConfiguration.class,
//        ConfClientRpcClientConfiguration.class,
//        ProxyEdcControlPlaneConfig.class,
//        RpcServerConfig.class
//})
//@EnableConfigurationProperties({
//        ProxyProperties.class,
//        AntiDosConfiguration.class,
//})
@ApplicationScoped
public class ProxyConfig {

    @Produces
    CertHelper certHelper(GlobalConfProvider globalConfProvider) {
        return new CertHelper(globalConfProvider);
    }

    @Produces
    CertChainFactory certChainFactory(GlobalConfProvider globalConfProvider) {
        return new CertChainFactory(globalConfProvider);
    }

//    @Bean
//    AuthTrustVerifier authTrustVerifier(CertHashBasedOcspResponderClient certHashBasedOcspResponderClient,
//                                        KeyConfProvider keyConfProvider, CertHelper certHelper, CertChainFactory certChainFactory) {
//        return new AuthTrustVerifier(certHashBasedOcspResponderClient, keyConfProvider, certHelper, certChainFactory);
//    }

//    @Bean
//    ServerProxy serverProxy(ProxyProperties proxyProperties,
//                            AntiDosConfiguration antiDosConfiguration,
//                            GlobalConfProvider globalConfProvider,
//                            KeyConfProvider keyConfProvider,
//                            ServerConfProvider serverConfProvider,
//                            CertChainFactory certChainFactory,
//                            ServiceHandlerLoader serviceHandlerLoader) throws Exception {
//        return new ServerProxy(proxyProperties.getServer(), antiDosConfiguration,
//                globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory, serviceHandlerLoader);
//    }

//    @Bean
//    ServiceHandlerLoader serviceHandlerLoader(ApplicationContext applicationContext) {
//        return new ServiceHandlerLoader(applicationContext);
//    }

//    @Bean
//    CertHashBasedOcspResponder certHashBasedOcspResponder(ProxyProperties proxyProperties, KeyConfProvider keyConfProvider)
//            throws Exception {
//        return new CertHashBasedOcspResponder(proxyProperties.getOcspResponder(), keyConfProvider);
//    }

    //    @Bean
//    CertHashBasedOcspResponderClient certHashBasedOcspResponderClient(ProxyProperties proxyProperties) {
//        return new CertHashBasedOcspResponderClient(proxyProperties.getOcspResponder());
//    }
    @Produces
    @ApplicationScoped
    OpMonitoring opMonitoringBuffer(AbstractOpMonitoringBuffer opMonitoringBuffer) throws Exception {
        return OpMonitoring.init(opMonitoringBuffer);
    }

    @Produces
    @ApplicationScoped
    KeyConfProvider keyConfProvider(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider,
                                    SignerRpcClient signerRpcClient) throws Exception {
        return new CachingKeyConfImpl(globalConfProvider, serverConfProvider, signerRpcClient);
    }

    @Produces
    @Startup
    SimpleSigner simpleSigner(SignerRpcClient signerRpcClient) {
        var signer = new SimpleSigner(signerRpcClient);
        SigningCtxProvider.setSigner(signer);
        return signer;
    }

}
