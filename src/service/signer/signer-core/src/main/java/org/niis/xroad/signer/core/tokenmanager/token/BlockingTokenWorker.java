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
package org.niis.xroad.signer.core.tokenmanager.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.proto.ActivateTokenReq;
import org.niis.xroad.signer.proto.GenerateKeyReq;
import org.niis.xroad.signer.proto.SignCertificateReq;
import org.niis.xroad.signer.proto.SignReq;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static ee.ria.xroad.common.ErrorCodes.translateException;

/**
 * A token worker with read-write locking to allow concurrent read operations.
 */
@Slf4j
@RequiredArgsConstructor
public class BlockingTokenWorker implements TokenWorker, WorkerWithLifecycle {
    private final AbstractTokenWorker tokenWorker;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    @Override
    public void handleActivateToken(ActivateTokenReq message) {
        writeAction(() -> tokenWorker.handleActivateToken(message));
    }

    @Override
    public KeyInfo handleGenerateKey(GenerateKeyReq message) {
        return writeAction(() -> tokenWorker.handleGenerateKey(message));
    }

    @Override
    public void handleDeleteKey(String keyId) {
        writeAction(() -> tokenWorker.handleDeleteKey(keyId));
    }

    @Override
    public void handleDeleteCert(String certificateId) {
        writeAction(() -> tokenWorker.handleDeleteCert(certificateId));
    }

    @Override
    public byte[] handleSign(SignReq request) {
        return readAction(() -> tokenWorker.handleSign(request));
    }

    @Override
    public byte[] handleSignCertificate(SignCertificateReq request) {
        return readAction(() -> tokenWorker.handleSignCertificate(request));
    }

    @Override
    public void initializeToken(char[] pin) {
        writeAction(() -> tokenWorker.initializeToken(pin));
    }

    @Override
    public void handleUpdateTokenPin(char[] oldPin, char[] newPin) {
        writeAction(() -> tokenWorker.handleUpdateTokenPin(oldPin, newPin));
    }

    @Override
    public boolean isSoftwareToken() {
        return tokenWorker.isSoftwareToken();
    }

    @Override
    public void start() {
        writeAction(tokenWorker::start);
    }

    @Override
    public void destroy() {
        writeAction(tokenWorker::destroy);
    }

    @Override
    public void reload() {
        writeAction(tokenWorker::reload);
    }

    @Override
    public void refresh() throws Exception {
        writeAction(tokenWorker::refresh);
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T, E extends Exception> {
        T get() throws E;
    }

    @FunctionalInterface
    public interface ThrowingRunnable<E extends Exception> {
        void run() throws E;
    }

    private <T> T readAction(ThrowingSupplier<T, Exception> action) {
        rwLock.readLock().lock();
        try {
            return action.get();
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            tokenWorker.onActionHandled();
            rwLock.readLock().unlock();
        }
    }

    private <T> T writeAction(ThrowingSupplier<T, Exception> action) {
        rwLock.writeLock().lock();
        try {
            return action.get();
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            tokenWorker.onActionHandled();
            rwLock.writeLock().unlock();
        }
    }

    private void writeAction(ThrowingRunnable<Exception> action) {
        rwLock.writeLock().lock();
        try {
            action.run();
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            tokenWorker.onActionHandled();
            rwLock.writeLock().unlock();
        }
    }
}
