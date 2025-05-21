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
package org.niis.xroad.signer.core.config;


import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.signer.core.certmanager.OcspClientWorker;
import org.niis.xroad.signer.core.certmanager.OcspResponseManager;
import org.niis.xroad.signer.core.job.OcspClientExecuteScheduler;
import org.niis.xroad.signer.core.job.OcspClientExecuteSchedulerImpl;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.core.tokenmanager.TokenRegistry;
import org.niis.xroad.signer.core.tokenmanager.module.AbstractModuleManager;
import org.niis.xroad.signer.core.tokenmanager.module.DefaultModuleManagerImpl;
import org.niis.xroad.signer.core.tokenmanager.module.HardwareModuleManagerImpl;
import org.niis.xroad.signer.core.tokenmanager.module.ModuleConf;

@Slf4j
public class SignerConfig {

    @ApplicationScoped
    @Startup
    AbstractModuleManager moduleManager(ModuleConf moduleConf, TokenManager tokenManager, TokenRegistry tokenRegistry,
                                        SignerProperties signerProperties,
                                        SignerHwTokenAddonProperties hwTokenAddonProperties, OcspResponseManager ocspResponseManager) {
        AbstractModuleManager moduleManager;
        if (hwTokenAddonProperties.enabled()) {
            log.info("Hardware token manager enabled.");
            moduleManager = new HardwareModuleManagerImpl(moduleConf, tokenManager, tokenRegistry, signerProperties, hwTokenAddonProperties,
                    ocspResponseManager);
        } else {
            log.debug("Using default module manager implementation");
            moduleManager = new DefaultModuleManagerImpl(moduleConf, tokenManager, tokenRegistry, signerProperties, ocspResponseManager);
        }

        moduleManager.start();
        return moduleManager;
    }

    public void cleanup(@Disposes AbstractModuleManager moduleManager) {
        moduleManager.destroy();
    }

    @ApplicationScoped
    @Startup
    OcspClientExecuteScheduler ocspClientExecuteScheduler(OcspClientWorker ocspClientWorker,
                                                          GlobalConfProvider globalConfProvider,
                                                          SignerProperties signerProperties) {
        if (signerProperties.ocspResponseRetrievalActive()) {
            var scheduler = new OcspClientExecuteSchedulerImpl(ocspClientWorker, globalConfProvider, signerProperties);
            scheduler.init();
            return scheduler;
        } else {
            return new OcspClientExecuteScheduler.NoopScheduler();
        }
    }

}
