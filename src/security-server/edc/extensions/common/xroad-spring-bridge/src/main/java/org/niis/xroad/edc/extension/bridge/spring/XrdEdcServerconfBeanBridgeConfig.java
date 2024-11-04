package org.niis.xroad.edc.extension.bridge.spring;

import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfBeanConfig;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.proxy.conf.CachingKeyConfImpl;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.signer.SignerRpcClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@ConditionalOnProperty(value = "edc.extension.spring-bridge.serverconf.enabled", havingValue = "true")
@Import({ServerConfBeanConfig.class})
@Configuration
public class XrdEdcServerconfBeanBridgeConfig {

    @Bean
    KeyConfProvider keyConfProvider(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider,
                                    SignerRpcClient signerRpcClient) {
        return new CachingKeyConfImpl(globalConfProvider, serverConfProvider, signerRpcClient);
    }

    @Bean
    @ConditionalOnMissingBean(TlsAuthKeyProvider.class)
    TlsAuthKeyProvider keyConfAuthKey(KeyConfProvider keyConfProvider) {
        return keyConfProvider::getAuthKey;
    }

}
