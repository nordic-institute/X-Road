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

import java.util.Collection;

import lombok.extern.slf4j.Slf4j;
import akka.actor.ActorRef;
import akka.actor.Props;

import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.util.AbstractUpdateableActor;
import ee.ria.xroad.signer.util.Update;

/**
 * Module worker base class.
 */
@Slf4j
public abstract class AbstractModuleManager extends AbstractUpdateableActor {

    @Override
    protected void onUpdate() throws Exception {
        loadModules();
        updateModuleWorkers();
        persistConfiguration();
    }

    @Override
    public void onMessage(Object message) throws Exception {
        unhandled(message);
    }

    protected abstract void initializeModule(ModuleType module);

    private void loadModules() throws Exception {
        log.trace("loadModules()");

        if (!ModuleConf.hasChanged()) {
            // do not reload, if conf has not changed
            return;
        }

        ModuleConf.reload();

        Collection<ModuleType> modules = ModuleConf.getModules();
        addNewModules(modules);
        removeLostModules(modules);
    }

    private void updateModuleWorkers() {
        for (ActorRef worker : getContext().getChildren()) {
            worker.tell(new Update(), getSelf());
        }
    }

    private void persistConfiguration() {
        try {
            TokenManager.saveToConf();
        } catch (Exception e) {
            log.error("Failed to save conf", e);
        }
    }

    private void addNewModules(Collection<ModuleType> modules) {
        modules.forEach(this::initializeModule);
    }

    private void removeLostModules(Collection<ModuleType> modules) {
        for (ActorRef module : getContext().getChildren()) {
            String moduleId = module.path().name();
            if (!containsModule(moduleId, modules)) {
                deinitializeModuleWorker(moduleId);
            }
        }
    }

    void initializeModuleWorker(String name, Props props) {
        log.trace("Starting module worker for module '{}'", name);

        getContext().actorOf(props, name);
    }

    void deinitializeModuleWorker(String name) {
        ActorRef worker = getContext().getChild(name);
        if (worker != null) {
            log.trace("Stopping module worker for module '{}'", name);

            getContext().stop(worker);
        } else {
            log.warn("Module worker for module '{}' not found", name);
        }
    }

    boolean isModuleInitialized(ModuleType module) {
        return getContext().getChild(module.getType()) != null;
    }

    private static boolean containsModule(String moduleId,
            Collection<ModuleType> modules) {
        return modules.stream().filter(m -> m.getType().equals(moduleId))
                .findFirst().isPresent();
    }
}
