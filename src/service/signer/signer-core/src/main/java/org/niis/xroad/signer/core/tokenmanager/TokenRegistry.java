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
package org.niis.xroad.signer.core.tokenmanager;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.core.model.CertRequestData;
import org.niis.xroad.signer.core.model.RuntimeCertImpl;
import org.niis.xroad.signer.core.model.RuntimeKeyImpl;
import org.niis.xroad.signer.core.model.RuntimeToken;
import org.niis.xroad.signer.core.model.RuntimeTokenImpl;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static ee.ria.xroad.common.ErrorCodes.translateException;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TokenRegistry {

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    private final TokenContext tokenContext = new TokenContext();
    private final ModifiableTokenContext modifiableTokenContext = new ModifiableTokenContext();
    private final TokenRegistryLoader tokenRegistryLoader;
    private Set<RuntimeTokenImpl> currentTokens;

    /**
     * Initializes the manager -- loads the tokens from the token configuration.
     *
     * @throws Exception if an error occurs
     */
    @PostConstruct
    public void init() {
        currentTokens = tokenRegistryLoader.loadTokens();
    }

    public void refresh() {
        rwLock.writeLock().lock();
        try {
            currentTokens = tokenRegistryLoader.refreshTokens(currentTokens);
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public int getCurrentKeyConfChecksum() {
        return currentTokens.hashCode(); //TODO implement hashcode calculation based on tokens
    }

    public boolean isInitialized() {
        return currentTokens != null;
    }

    // Functional interfaces for token operations
    public class ModifiableTokenContext extends BaseTokenContext<RuntimeTokenImpl> {

        public void invalidateCache() {
            log.debug("Invalidating token cache");
            currentTokens = tokenRegistryLoader.refreshTokens(currentTokens);
        }

        public RuntimeTokenImpl findToken(String tokenId) {
            return TokenLookupUtils.findToken(currentTokens, tokenId);
        }

        public RuntimeKeyImpl findKey(String keyId) {
            return TokenLookupUtils.findKey(currentTokens, keyId);
        }

        public RuntimeCertImpl getCert(String certId) {
            return TokenLookupUtils.getCert(currentTokens, certId);
        }

        public Optional<RuntimeCertImpl> findCert(String certId) {
            return TokenLookupUtils.findCert(currentTokens, certId);
        }

        public Optional<CertRequestData> findCertRequest(String certReqId) {
            return TokenLookupUtils.findCertRequest(currentTokens, certReqId);
        }

    }

    public class TokenContext extends BaseTokenContext<RuntimeToken> {
        public Set<RuntimeToken> getTokens() {
            return new HashSet<>(currentTokens);
        }

        public RuntimeToken findToken(String tokenId) {
            return TokenLookupUtils.findToken(currentTokens, tokenId);
        }

        public RuntimeKeyImpl findKey(String keyId) {
            return TokenLookupUtils.findKey(currentTokens, keyId);
        }

        public <T> Optional<T> forCert(BiPredicate<RuntimeKeyImpl, RuntimeCertImpl> tester,
                                       BiFunction<RuntimeKeyImpl, RuntimeCertImpl, T> mapper) {
            return TokenLookupUtils.forCert(currentTokens, tester, mapper);
        }

        public <T> Optional<T> forKey(BiPredicate<RuntimeTokenImpl, RuntimeKeyImpl> tester,
                                      BiFunction<RuntimeTokenImpl, RuntimeKeyImpl, T> mapper) {
            return TokenLookupUtils.forKey(currentTokens, tester, mapper);
        }

        public <T> Optional<T> forToken(Predicate<RuntimeTokenImpl> tester, Function<RuntimeTokenImpl, T> mapper) {
            return TokenLookupUtils.forToken(currentTokens, tester, mapper);
        }

        <T> Optional<T> forCertRequest(BiPredicate<RuntimeKeyImpl, CertRequestData> tester,
                                       BiFunction<RuntimeKeyImpl, CertRequestData, T> mapper) {
            return TokenLookupUtils.forCertRequest(currentTokens, tester, mapper);
        }

    }

    public class BaseTokenContext<Token> {

    }

    @FunctionalInterface
    public interface TokenSupplier<T, V extends BaseTokenContext<?>> {
        T get(V ctx);
    }

    @FunctionalInterface
    public interface TokenRunnable<T extends BaseTokenContext<?>> {
        void accept(T ctx);
    }

    public <T> T readAction(TokenSupplier<T, TokenContext> action) {
        rwLock.readLock().lock();
        try {
            return action.get(tokenContext);
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public <T> T writeAction(TokenSupplier<T, ModifiableTokenContext> action) {
        rwLock.writeLock().lock();
        try {
            return action.get(modifiableTokenContext);
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void writeRun(TokenRunnable<ModifiableTokenContext> action) {
        rwLock.writeLock().lock();
        try {
            action.accept(modifiableTokenContext);
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

}
