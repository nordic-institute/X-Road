package ee.cyber.sdsb.signer.util;

import iaik.pkcs.pkcs11.Module;

public interface ModuleInstanceProvider {

    Module getInstance(String path) throws Exception;

}
