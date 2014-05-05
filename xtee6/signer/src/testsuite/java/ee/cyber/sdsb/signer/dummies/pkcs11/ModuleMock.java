package ee.cyber.sdsb.signer.dummies.pkcs11;

import java.io.IOException;

import iaik.pkcs.pkcs11.Module;
import iaik.pkcs.pkcs11.wrapper.PKCS11;

public class ModuleMock extends Module {

    protected ModuleMock(PKCS11 pkcs11Module) {
        super(pkcs11Module);
    }

    // static methods cannot be overridden. So I created new ones which 
    // do the same things as super's getInstance() methods, but use Mock classes
    public static Module getInstanceMock(String pkcs11ModuleName)
            throws IOException {
        if (pkcs11ModuleName == null) {
            throw new NullPointerException("Argument \"pkcs11ModuleName\" " +
                    "must not be null.");
        }
        PKCS11 pkcs11Module = PKCS11ConnectorMock.connectToPKCS11Module(
                pkcs11ModuleName);

        return new ModuleMock(pkcs11Module);
    }
}
