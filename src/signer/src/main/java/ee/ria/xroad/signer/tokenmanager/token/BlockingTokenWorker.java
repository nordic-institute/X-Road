package ee.ria.xroad.signer.tokenmanager.token;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.tokenmanager.module.AbstractModuleWorker;

import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.ActivateTokenReq;
import org.niis.xroad.signer.proto.GenerateKeyReq;
import org.niis.xroad.signer.proto.SignCertificateReq;
import org.niis.xroad.signer.proto.SignReq;

import static ee.ria.xroad.common.ErrorCodes.translateException;

/**
 * A blocking (calls to token are synchronized) token worker.
 */
@Slf4j
@RequiredArgsConstructor
public class BlockingTokenWorker implements TokenWorker {
    private final AbstractModuleWorker moduleWorker;
    private final AbstractTokenWorker tokenWorker;

    @Override
    public void handleActivateToken(ActivateTokenReq message) {
        synchronizedAction(() -> tokenWorker.handleActivateToken(message));
    }

    @Override
    public KeyInfo handleGenerateKey(GenerateKeyReq message) {
        return synchronizedAction(() -> tokenWorker.handleGenerateKey(message));
    }

    @Override
    public void handleDeleteKey(String keyId) {
        synchronizedAction(() -> tokenWorker.handleDeleteKey(keyId));
    }

    @Override
    public void handleDeleteCert(String certificateId) {
        synchronizedAction(() -> tokenWorker.handleDeleteCert(certificateId));
    }

    @Override
    public byte[] handleSign(SignReq request) {
        return synchronizedAction(() -> tokenWorker.handleSign(request));
    }

    @Override
    public synchronized byte[] handleSignCertificate(SignCertificateReq request) {
        return synchronizedAction(() -> tokenWorker.handleSignCertificate(request));

    }

    @Override
    public synchronized void initializeToken(char[] pin) {
        synchronizedAction(() -> tokenWorker.initializeToken(pin));
    }

    @Override
    public synchronized void handleUpdateTokenPin(char[] oldPin, char[] newPin) {
        synchronizedAction(() -> tokenWorker.handleUpdateTokenPin(oldPin, newPin));
    }

    @Override
    public boolean isSoftwareToken() {
        return getInternalTokenWorker().isSoftwareToken();
    }

    /**
     * Returns unwrapped and unblocked token worker for internal operations.
     *
     * @return token worker
     */
    public AbstractTokenWorker getInternalTokenWorker() {
        return tokenWorker;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T, E extends Exception> {
        T get() throws E;
    }

    @FunctionalInterface
    public interface ThrowingRunnable<E extends Exception> {
        void run() throws E;
    }


    private synchronized <T> T synchronizedAction(ThrowingSupplier<T, Exception> action) {
        try {
            return action.get();
        } catch (PKCS11Exception pkcs11Exception) {
            log.warn("PKCS11Exception was thrown. Reloading underlying module and token workers.");
            moduleWorker.reload();
            throw translateException(pkcs11Exception);
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            tokenWorker.onActionHandled();
        }
    }


    private synchronized void synchronizedAction(ThrowingRunnable<Exception> action) {
        try {
            action.run();
        } catch (PKCS11Exception pkcs11Exception) {
            log.warn("PKCS11Exception was thrown. Reloading underlying module and token workers.");
            moduleWorker.reload();
            throw translateException(pkcs11Exception);
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            tokenWorker.onActionHandled();
        }
    }
}
