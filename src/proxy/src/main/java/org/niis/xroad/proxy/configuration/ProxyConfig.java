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

import ee.ria.xroad.common.opmonitoring.AbstractOpMonitoringBuffer;
import ee.ria.xroad.common.signature.DSSSigner;
import ee.ria.xroad.common.signature.MessageSigner;
import ee.ria.xroad.proxy.ProxyAddonConfig;
import ee.ria.xroad.proxy.ProxyAdminPortConfig;
import ee.ria.xroad.proxy.ProxyDiagnosticsConfig;
import ee.ria.xroad.proxy.ProxyJobConfig;
import ee.ria.xroad.proxy.ProxyMessageLogConfig;
import ee.ria.xroad.proxy.ProxyRpcConfig;
import ee.ria.xroad.proxy.conf.SigningCtxProvider;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoring;
import ee.ria.xroad.proxy.serverproxy.ServerProxy;
import ee.ria.xroad.proxy.util.CertHashBasedOcspResponder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Import({
        ProxyRpcConfig.class,
        ProxyAdminPortConfig.class,
        ProxyAddonConfig.class,
        ProxyDiagnosticsConfig.class,
        ProxyJobConfig.class,
        ProxyMessageLogConfig.class,
        ProxyClientConfig.class
})
@ComponentScan(basePackages = {"org.niis.xroad.proxy"})
@Configuration
public class ProxyConfig {

    @Bean(destroyMethod = "shutdown")
    MessageSigner messageSigner() {
        MessageSigner signer;
//        if (SystemProperties.isBatchMessageSigningEnabled()) {
//            signer = BatchSigner.init();
//        } else {
//            signer = new SimpleSigner();
//        }
        signer = new DSSSigner();
        //TODO this is a hack, we should not set the signer here
        SigningCtxProvider.setSigner(signer);
        return signer;
    }


    @Bean(initMethod = "start", destroyMethod = "stop")
    ServerProxy serverProxy() throws Exception {
        return new ServerProxy();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    CertHashBasedOcspResponder certHashBasedOcspResponder() throws Exception {
        return new CertHashBasedOcspResponder();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    AbstractOpMonitoringBuffer opMonitoringBuffer() throws Exception {
        return OpMonitoring.init();
    }
}
