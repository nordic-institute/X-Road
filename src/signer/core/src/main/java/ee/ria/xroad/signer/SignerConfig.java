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
package ee.ria.xroad.signer;

import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.signer.certmanager.FileBasedOcspCache;
import ee.ria.xroad.signer.certmanager.OcspClient;
import ee.ria.xroad.signer.certmanager.OcspClientWorker;
import ee.ria.xroad.signer.certmanager.OcspResponseManager;
import ee.ria.xroad.signer.job.NoopOcspClientExecuteScheduler;
import ee.ria.xroad.signer.job.OcspClientExecuteScheduler;
import ee.ria.xroad.signer.job.OcspClientExecuteSchedulerImpl;
import ee.ria.xroad.signer.tokenmanager.module.AbstractModuleManager;
import ee.ria.xroad.signer.tokenmanager.module.DefaultModuleManagerImpl;
import ee.ria.xroad.signer.tokenmanager.module.HardwareModuleManagerImpl;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
public class SignerConfig {

    @ConfigProperty(name = "xroad.signer.addon.hwtoken.enabled", defaultValue = "false")
    boolean isHwTokenEnabled;

    @ApplicationScoped
    @Startup
    AbstractModuleManager moduleManager(SignerProperties signerProperties, OcspResponseManager ocspResponseManager) {
        AbstractModuleManager moduleManager;
        if (isHwTokenEnabled) {
            log.info("Hardware token manager enabled.");
            moduleManager = new HardwareModuleManagerImpl(signerProperties, ocspResponseManager);
        } else {
            log.debug("Using default module manager implementation");
            moduleManager = new DefaultModuleManagerImpl(signerProperties, ocspResponseManager);
        }

        moduleManager.start();
        return moduleManager;
    }

    @ApplicationScoped
    OcspResponseManager ocspResponseManager(GlobalConfProvider globalConfProvider, OcspClient ocspClient, FileBasedOcspCache ocspCache) {
        OcspResponseManager ocspResponseManager = new OcspResponseManager(globalConfProvider, ocspClient, ocspCache);
        ocspResponseManager.init();
        return ocspResponseManager;
    }

    @ApplicationScoped
    CertChainFactory certChainFactory(GlobalConfProvider globalConfProvider) {
        return new CertChainFactory(globalConfProvider);
    }

    @ApplicationScoped
    @Startup
    OcspClientExecuteScheduler ocspClientExecuteScheduler(OcspClientWorker ocspClientWorker,
                                                          GlobalConfProvider globalConfProvider,
                                                          SignerProperties signerProperties) {
        if (signerProperties.ocspResponseRetrievalActive()) {
            OcspClientExecuteSchedulerImpl scheduler = new OcspClientExecuteSchedulerImpl(ocspClientWorker, globalConfProvider, signerProperties);
            scheduler.init();
            return scheduler;
        } else {
            return new NoopOcspClientExecuteScheduler();
        }
    }

}
