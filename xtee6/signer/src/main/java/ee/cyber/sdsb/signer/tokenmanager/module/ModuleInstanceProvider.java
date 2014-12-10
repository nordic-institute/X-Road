package ee.cyber.sdsb.signer.tokenmanager.module;

import iaik.pkcs.pkcs11.Module;

/**
 * Provides instances of modules based on pkcs11 library path.
 */
public interface ModuleInstanceProvider {

    /**
     * Returns the instance of a module based on pkcs11 library path.
     * @param path the pkcs11 library path
     * @return the module instance
     * @throws Exception if an error occurs
     */
    Module getInstance(String path) throws Exception;

}
