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
package ee.ria.xroad.proxy;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.serverconf.ServerConfFactory;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.opmonitoring.AbstractOpMonitoringBuffer;
import ee.ria.xroad.common.signature.BatchSigner;
import ee.ria.xroad.proxy.clientproxy.AuthTrustVerifier;
import ee.ria.xroad.proxy.clientproxy.ClientProxy;
import ee.ria.xroad.proxy.conf.CachingKeyConfImpl;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoring;
import ee.ria.xroad.proxy.serverproxy.ServerProxy;
import ee.ria.xroad.proxy.util.CertHashBasedOcspResponder;

import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.globalconf.spring.GlobalConfBeanConfig;
import org.niis.xroad.globalconf.spring.GlobalConfRefreshJobConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Import({
        ProxyRpcConfig.class,
        ProxyAdminPortConfig.class,
        ProxyAddonConfig.class,
        ProxyDiagnosticsConfig.class,
        ProxyJobConfig.class,
        ProxyMessageLogConfig.class,
        GlobalConfBeanConfig.class,
        GlobalConfRefreshJobConfig.class
})
@Configuration
public class ProxyConfig {

    @Bean
    BatchSigner batchSigner() {
        return BatchSigner.init();
    }

    @Bean
    ClientProxy clientProxy(GlobalConfProvider globalConfProvider,
                            KeyConfProvider keyConfProvider,
                            ServerConfProvider serverConfProvider,
                            CertChainFactory certChainFactory,
                            AuthTrustVerifier authTrustVerifier) throws Exception {
        return new ClientProxy(globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory, authTrustVerifier);
    }

    @Bean
    CertHelper certHelper(GlobalConfProvider globalConfProvider) {
        return new CertHelper(globalConfProvider);
    }

    @Bean
    CertChainFactory certChainFactory(GlobalConfProvider globalConfProvider) {
        return new CertChainFactory(globalConfProvider);
    }

    @Bean
    AuthTrustVerifier authTrustVerifier(KeyConfProvider keyConfProvider, CertHelper certHelper, CertChainFactory certChainFactory) {
        return new AuthTrustVerifier(keyConfProvider, certHelper, certChainFactory);
    }

    @Bean
    ServerProxy serverProxy(GlobalConfProvider globalConfProvider,
                            KeyConfProvider keyConfProvider,
                            ServerConfProvider serverConfProvider,
                            CertChainFactory certChainFactory) throws Exception {
        return new ServerProxy(globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory);
    }

    @Bean
    CertHashBasedOcspResponder certHashBasedOcspResponder(KeyConfProvider keyConfProvider) throws Exception {
        return new CertHashBasedOcspResponder(keyConfProvider);
    }

    @Bean
    AbstractOpMonitoringBuffer opMonitoringBuffer(ServerConfProvider serverConfProvider) throws Exception {
        return OpMonitoring.init(serverConfProvider);
    }

    @Bean
    KeyConfProvider keyConfProvider(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider) throws Exception {
        return CachingKeyConfImpl.newInstance(globalConfProvider, serverConfProvider);
    }

    @Bean
    public ServerConfProvider serverConfProvider(GlobalConfProvider globalConfProvider) {
        return ServerConfFactory.create(globalConfProvider, SystemProperties.getServerConfCachePeriod());
    }
}
