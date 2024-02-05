/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.signer.tokenmanager.token;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;

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
public class BlockingTokenWorker implements TokenWorker, WorkerWithLifecycle {
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
        return tokenWorker.isSoftwareToken();
    }

    @Override
    public void start() {
        synchronizedAction(tokenWorker::start);
    }

    @Override
    public void stop() {
        synchronizedAction(tokenWorker::stop);
    }

    @Override
    public void reload() {
        synchronizedAction(tokenWorker::reload);
    }

    @Override
    public void refresh() throws Exception {
        synchronizedAction(tokenWorker::refresh);
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
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            tokenWorker.onActionHandled();
        }
    }


    private synchronized void synchronizedAction(ThrowingRunnable<Exception> action) {
        try {
            action.run();
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            tokenWorker.onActionHandled();
        }
    }
}
