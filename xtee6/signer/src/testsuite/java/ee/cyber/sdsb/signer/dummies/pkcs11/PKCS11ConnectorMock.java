package ee.cyber.sdsb.signer.dummies.pkcs11;

import java.io.IOException;

import iaik.pkcs.pkcs11.wrapper.PKCS11;
import iaik.pkcs.pkcs11.wrapper.PKCS11Connector;
import iaik.pkcs.pkcs11.wrapper.PKCS11Implementation;

public class PKCS11ConnectorMock {

    protected PKCS11ConnectorMock() { /* left empty intentionally */
    }

    public static PKCS11 connectToPKCS11Module(
            String pkcs11ModulePath) throws IOException {
        return new PKCS11ImplMock(pkcs11ModulePath);
    }
}
