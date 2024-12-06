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
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.messagelog.MessageLogConfig;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.signer.SignerRpcClient;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.springframework.context.ApplicationContext;

import java.util.Map;

@Slf4j
public class XrdSpringBridgeExtension implements ServiceExtension {
    private static ApplicationContext applicationContext;

    @Provider
    public SignerRpcClient signerRpcClient() {
        return getBean(SignerRpcClient.class);
    }

    @Provider
    public GlobalConfProvider globalConfProvider() {
        return getBean(GlobalConfProvider.class);
    }

    @Provider
    public KeyConfProvider keyConfProvider() {
        return getBean(KeyConfProvider.class);
    }

    @Provider
    public TlsAuthKeyProvider tlsAuthKeyProvider() {
        return getBean(TlsAuthKeyProvider.class);
    }

    @Provider
    public ServerConfProvider serverConfProvider() {
        return getBean(ServerConfProvider.class);
    }

    @Provider
    public CertChainFactory certChainFactory() {
        return getBean(CertChainFactory.class);
    }

    @Provider
    public MessageLogConfig messageLogConfig() {
        return getBean(MessageLogConfig.class);
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
    }

    private <T> T getBean(Class<T> clazz) {
        Map<String, T> availableBeans = applicationContext.getBeansOfType(clazz);
        var bean = availableBeans.values().stream().findFirst().orElse(null);
        log.info("Bridging bean of type [{}] to resulting bean [{}] within EDC", clazz, bean);
        return availableBeans.values().stream().findFirst().orElse(null);
    }

    public static void attachContext(ApplicationContext context) {
        XrdSpringBridgeExtension.applicationContext = context;
    }
}
