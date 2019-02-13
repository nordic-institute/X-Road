/**
 * The MIT License
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
package ee.ria.xroad.signer.tokenmanager.module;

import ee.ria.xroad.signer.SignerMain;

import akka.actor.Props;
import akka.actor.Terminated;
import lombok.extern.slf4j.Slf4j;

/**
 * Module manager that supports hardware tokens.
 */
@Slf4j
public class HardwareModuleManagerImpl extends DefaultModuleManagerImpl {

    private static final String DISPATCHER = "module-worker-dispatcher";

    @Override
    protected void initializeModule(ModuleType module) {
        if (module instanceof HardwareModuleType) {
            initializeHardwareModule((HardwareModuleType) module);
        } else if (module instanceof SoftwareModuleType) {
            initializeSoftwareModule((SoftwareModuleType) module);
        }
    }

    private void initializeHardwareModule(HardwareModuleType hardwareModule) {
        if (!isModuleInitialized(hardwareModule)) {
            try {
                Props props = Props.create(HardwareModuleWorker.class, hardwareModule).withDispatcher(DISPATCHER);
                initializeModuleWorker(hardwareModule, props);
            } catch (Exception e) {
                log.error("Error initializing hardware module '" + hardwareModule.getType() + "'", e);
            }
        }
    }

    @Override
    public void onMessage(Object message) throws Exception {
        if (message instanceof Terminated) {
            Terminated terminatedMessage = (Terminated) message;
            String name = terminatedMessage.getActor().path().name();
            log.error("Hardware module " + name + " terminated.");

            SignerMain.exit(1);
        } else {
            super.onMessage(message);
        }
    }

    @Override
    protected ModuleBackoffOptions getModuleBackoffOptions(ModuleType module) {
        if (module instanceof HardwareModuleType) {
            HardwareModuleType hardwareModuleType = (HardwareModuleType) module;
            ModuleBackoffOptions moduleBackoffOptions = new ModuleBackoffOptions();
            moduleBackoffOptions.setMaxNrOfRetries(hardwareModuleType.getBackoffMaxRetries());
            moduleBackoffOptions.setMinSeconds(hardwareModuleType.getBackoffMinSeconds());
            moduleBackoffOptions.setMaxSeconds(hardwareModuleType.getBackoffMaxSeconds());
            return moduleBackoffOptions;
        } else {
            return super.getModuleBackoffOptions(module);
        }
    }
}
