package ee.ria.xroad.signer.tokenmanager.token;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;

import org.niis.xroad.signer.proto.ActivateTokenReq;
import org.niis.xroad.signer.proto.GenerateKeyReq;
import org.niis.xroad.signer.proto.SignCertificateReq;
import org.niis.xroad.signer.proto.SignReq;

public interface TokenWorker {

    void handleActivateToken(ActivateTokenReq message);

    KeyInfo handleGenerateKey(GenerateKeyReq message);

    void handleDeleteKey(String keyId);

    void handleDeleteCert(String certificateId);

    byte[] handleSign(SignReq request);

    byte[] handleSignCertificate(SignCertificateReq request);

    void handleUpdateTokenPin(char[] oldPin, char[] newPin);

    void initializeToken(char[] pin);

    boolean isSoftwareToken();
}
