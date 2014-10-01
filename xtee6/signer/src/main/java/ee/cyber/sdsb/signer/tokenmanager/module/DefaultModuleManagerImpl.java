package ee.cyber.sdsb.signer.tokenmanager.module;

import lombok.extern.slf4j.Slf4j;
import akka.actor.Props;

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
