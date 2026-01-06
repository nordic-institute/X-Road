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
package org.niis.xroad.proxy.core.configuration;

import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.globalconf.spring.GlobalConfBeanConfig;
import org.niis.xroad.globalconf.spring.GlobalConfRefreshJobConfig;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.keyconf.impl.CachingKeyConfImpl;
import org.niis.xroad.proxy.core.auth.AuthKeyChangeManager;
import org.niis.xroad.proxy.core.clientproxy.AuthTrustVerifier;
import org.niis.xroad.proxy.core.clientproxy.ClientProxy;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;
import org.niis.xroad.proxy.core.conf.SigningCtxProviderImpl;
import org.niis.xroad.proxy.core.serverproxy.ServerProxy;
import org.niis.xroad.proxy.core.signature.BatchSigner;
import org.niis.xroad.proxy.core.signature.MessageSigner;
import org.niis.xroad.proxy.core.util.CertHashBasedOcspResponder;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.spring.ServerConfBeanConfig;
import org.niis.xroad.signer.client.SignerRpcClient;
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
        ProxyOpMonitoringConfig.class,
        GlobalConfBeanConfig.class,
        GlobalConfRefreshJobConfig.class,
        ServerConfBeanConfig.class,
})
@Configuration
@ArchUnitSuppressed("NoVanillaExceptions")
public class ProxyConfig {

    @Bean
    MessageSigner messageSigner(SignerRpcClient signerRpcClient) {
        return new BatchSigner(signerRpcClient);
    }

    @Bean
    SigningCtxProvider signingCtxProvider(GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider,
                                          MessageSigner messageSigner) {
        return new SigningCtxProviderImpl(globalConfProvider, keyConfProvider, messageSigner);
    }

    @Bean
    CommonBeanProxy commonBeanProxy(GlobalConfProvider globalConfProvider,
                                    KeyConfProvider keyConfProvider,
                                    SigningCtxProvider signingCtxProvider,
                                    ServerConfProvider serverConfProvider,
                                    CertChainFactory certChainFactory,
                                    CertHelper certHelper) {
        return new CommonBeanProxy(globalConfProvider, serverConfProvider, keyConfProvider, signingCtxProvider, certChainFactory,
                certHelper);
    }

    @Bean
    ClientProxy clientProxy(CommonBeanProxy commonBeanProxy,
                            GlobalConfProvider globalConfProvider,
                            KeyConfProvider keyConfProvider,
                            ServerConfProvider serverConfProvider,
                            AuthTrustVerifier authTrustVerifier) throws Exception {
        return new ClientProxy(commonBeanProxy, globalConfProvider, keyConfProvider, serverConfProvider, authTrustVerifier);
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
    ServerProxy serverProxy(CommonBeanProxy commonBeanProxy) throws Exception {
        return new ServerProxy(commonBeanProxy);
    }

    @Bean
    CertHashBasedOcspResponder certHashBasedOcspResponder(KeyConfProvider keyConfProvider) throws Exception {
        return new CertHashBasedOcspResponder(keyConfProvider);
    }

    @Bean
    KeyConfProvider keyConfProvider(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider,
                                    SignerRpcClient signerRpcClient) throws Exception {
        return new CachingKeyConfImpl(globalConfProvider, serverConfProvider, signerRpcClient);
    }

    @Bean
    AuthKeyChangeManager authKeyChangeManager(KeyConfProvider keyConfProvider, ClientProxy clientProxy, ServerProxy serverProxy) {
        return new AuthKeyChangeManager(keyConfProvider, clientProxy, serverProxy);
    }
}
