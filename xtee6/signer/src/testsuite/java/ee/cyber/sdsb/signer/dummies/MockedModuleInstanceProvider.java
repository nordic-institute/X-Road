package ee.cyber.sdsb.signer.dummies;

import iaik.pkcs.pkcs11.Module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.signer.dummies.pkcs11.ModuleMock;
import ee.cyber.sdsb.signer.util.ModuleInstanceProvider;

public class MockedModuleInstanceProvider implements ModuleInstanceProvider {

    private static final Logger LOG =
            LoggerFactory.getLogger(MockedModuleInstanceProvider.class);

    @Override
    public Module getInstance(String path) throws Exception {
        LOG.debug("getModuleInstance({})", path);
        return ModuleMock.getInstanceMock(path);
    }

}
