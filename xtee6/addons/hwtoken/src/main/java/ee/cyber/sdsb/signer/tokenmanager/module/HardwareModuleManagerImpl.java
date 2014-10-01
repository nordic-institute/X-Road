package ee.cyber.sdsb.signer.tokenmanager.module;

import lombok.extern.slf4j.Slf4j;
import akka.actor.Props;

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
                Props props = Props.create(HardwareModuleWorker.class,
                        hardwareModule).withDispatcher(DISPATCHER);
                initializeModuleWorker(hardwareModule.getType(), props);
            } catch (Exception e) {
                log.error("Error initializing module '"
                        + hardwareModule.getType() + "'", e);
            }
        }
    }
}
