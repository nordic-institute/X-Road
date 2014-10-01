package ee.cyber.sdsb.signer.tokenmanager.module;

import java.util.Collection;

import lombok.extern.slf4j.Slf4j;
import akka.actor.ActorRef;
import akka.actor.Props;

import ee.cyber.sdsb.signer.tokenmanager.TokenManager;
import ee.cyber.sdsb.signer.util.AbstractUpdateableActor;
import ee.cyber.sdsb.signer.util.Update;

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
        for (ModuleType module : modules) {
            initializeModule(module);
        }
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
        for (ModuleType module : modules) {
            if (module.getType().equals(moduleId)) {
                return true;
            }
        }

        return false;
    }
}
