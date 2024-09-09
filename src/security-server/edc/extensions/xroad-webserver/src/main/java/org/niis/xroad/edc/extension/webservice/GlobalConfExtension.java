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

package org.niis.xroad.edc.extension.webservice;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.FileSystemGlobalConfSource;
import ee.ria.xroad.common.conf.globalconf.GlobalConfImpl;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.CachingServerConfImpl;
import ee.ria.xroad.common.conf.serverconf.ServerConfImpl;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.proxy.conf.CachingKeyConfImpl;
import ee.ria.xroad.proxy.conf.KeyConfProvider;

import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import static ee.ria.xroad.common.SystemProperties.getConfigurationPath;

// todo move to separate extension?
@Provides({GlobalConfProvider.class, KeyConfProvider.class, ServerConfProvider.class, CertChainFactory.class})
public class GlobalConfExtension implements ServiceExtension {

    @Override
    public void initialize(ServiceExtensionContext context) {

        try {
            GlobalConfProvider globalConfProvider = new GlobalConfImpl(
                    new FileSystemGlobalConfSource(getConfigurationPath()));

            ServerConfProvider serverConfProvider = (SystemProperties.getServerConfCachePeriod() > 0)
                    ? new CachingServerConfImpl(globalConfProvider)
                    : new ServerConfImpl(globalConfProvider);

            CertChainFactory certChainFactory = new CertChainFactory(globalConfProvider);

            KeyConfProvider keyConfProvider = CachingKeyConfImpl.newInstance(globalConfProvider, serverConfProvider);

            context.registerService(GlobalConfProvider.class, globalConfProvider);
            context.registerService(ServerConfProvider.class, serverConfProvider);
            context.registerService(CertChainFactory.class, certChainFactory);
            context.registerService(KeyConfProvider.class, keyConfProvider);
        } catch (Exception e) {
            throw new EdcException("Initialization failed", e);
        }
    }
}
