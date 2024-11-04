
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
package org.niis.xroad.edc.extension.bridge.spring;

import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.conf.globalconf.GlobalConfBeanConfig;
import ee.ria.xroad.common.conf.globalconf.GlobalConfPropertiesConfig;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.SignerClientConfiguration;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.boot.system.runtime.BaseRuntime;
import org.eclipse.edc.spi.system.configuration.Config;
import org.niis.xroad.common.rpc.RpcServiceProperties;
import org.niis.xroad.confclient.proto.ConfClientRpcClientConfiguration;
import org.niis.xroad.edc.extension.bridge.config.MapConfigImpl;
import org.niis.xroad.edc.extension.bridge.config.XrdSpringConfigExtension;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

@Slf4j
@Import({
        GlobalConfPropertiesConfig.class,
        GlobalConfBeanConfig.class,
        ConfClientRpcClientConfiguration.class,
        SignerClientConfiguration.class,
        XrdEdcServerconfBeanBridgeConfig.class})
@EnableConfigurationProperties({XrdEdcBeanBridgeConfig.EdcDataPlaneRpcServiceProperties.class})
@Configuration
public class XrdEdcBeanBridgeConfig {

    @Bean
    CertChainFactory certChainFactory(GlobalConfProvider globalConfProvider) {
        return new CertChainFactory(globalConfProvider);
    }

    @Bean
    Config springEdcConfig(ConfigurableEnvironment environment) {
        //TODO xroad8 maybe limit to edc and web prefix'ed props?
        Map<String, String> entries = new HashMap<>();
        MutablePropertySources propSrcs = environment.getPropertySources();
        StreamSupport.stream(propSrcs.spliterator(), false)
                .filter(EnumerablePropertySource.class::isInstance)
                .map(ps -> ((EnumerablePropertySource<?>) ps).getPropertyNames())
                .flatMap(Arrays::stream)
                .forEach(propName -> entries.put(propName, environment.getProperty(propName)));
        return new MapConfigImpl(entries);
        //TODO: this impl has issues..
//        return new SpringEdcConfig(environment);
    }

    @Bean
    BaseRuntime baseRuntimeConfig(ApplicationContext applicationContext, Config springEdcConfig) {
        XrdSpringConfigExtension.attachConfiguration(springEdcConfig);
        XrdSpringBridgeExtension.attachContext(applicationContext);

        return new SpringEdcRuntime();
    }

    /**
     * TODO xroad8, this is a temporary solution to provide a custom TLS key for CS. Consider better alternatives
     */
    @Bean
    @ConditionalOnProperty(value = "web.custom-tls-keystore.enabled", havingValue = "true")
    TlsAuthKeyProvider customAuthKey(@Value("${web.custom-tls-keystore.path}") String pkcs12Path,
                                     CertChainFactory certChainFactory, GlobalConfProvider globalConfProvider) throws Exception {
        KeyStore keyStore = CryptoUtils.loadPkcs12KeyStore(Paths.get(pkcs12Path).toFile(), "management-service".toCharArray());

        keyStore.getKey("management-service", "management-service".toCharArray());

        var cert = keyStore.getCertificate("management-service");
        var certChain = certChainFactory.create("cs", new X509Certificate[]{
                (X509Certificate) cert,
                globalConfProvider.getAllCaCerts().stream().findFirst().orElseThrow()
        });
        var pkey = (PrivateKey) keyStore.getKey("management-service", "management-service".toCharArray());

        return () -> new AuthKey(certChain, pkey);
    }

    @ConfigurationProperties(prefix = "xroad.edc-data-plane.grpc")
    static class EdcDataPlaneRpcServiceProperties extends RpcServiceProperties {

        EdcDataPlaneRpcServiceProperties(String listenAddress, int port,
                                         String tlsTrustStore, char[] tlsTrustStorePassword,
                                         String tlsKeyStore, char[] tlsKeyStorePassword) {
            super(listenAddress, port, tlsTrustStore, tlsTrustStorePassword, tlsKeyStore, tlsKeyStorePassword);
        }
    }

    public static class SpringEdcRuntime extends BaseRuntime implements InitializingBean, DisposableBean {

        public SpringEdcRuntime() {
            super();
        }

        @Override
        public void destroy() {
            shutdown();
        }

        @Override
        public void afterPropertiesSet() {
            log.info("Loading EDC runtime..");
            try {
                boot(false);
            } catch (Exception e) {
                log.error("Failed to boot EDC runtime", e);
            }
        }
    }

}
