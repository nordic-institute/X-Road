/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import lombok.extern.slf4j.Slf4j;
import akka.actor.Props;

/**
 * Default module manager supporting only software modules.
 */
@Slf4j
public class DefaultModuleManagerImpl extends AbstractModuleManager {

    @Override
    protected void initializeModule(ModuleType module) {
        if (module instanceof SoftwareModuleType) {
            initializeSoftwareModule((SoftwareModuleType) module);
        }
    }

    void initializeSoftwareModule(SoftwareModuleType softwareModule) {
        if (getContext().getChild(softwareModule.getType()) != null) {
            // already initialized
            return;
        }

        log.debug("Initializing software module");

        Props props = Props.create(SoftwareModuleWorker.class);
        initializeModuleWorker(softwareModule.getType(), props);
    }

}
