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
import org.niis.xroad.signer.core.config.SignerProperties;
import org.niis.xroad.signer.core.model.Cert;
import org.niis.xroad.signer.core.model.CertRequest;
import org.niis.xroad.signer.core.model.Key;
import org.niis.xroad.signer.core.model.Token;
import org.niis.xroad.signer.core.tokenmanager.merge.MergeOntoFileTokensStrategy;
import org.niis.xroad.signer.core.tokenmanager.merge.TokenMergeAddedCertificatesListener;
import org.niis.xroad.signer.core.tokenmanager.merge.TokenMergeStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    // configure the implementation somewhere else if multiple implementations created
    private static final TokenMergeStrategy MERGE_STRATEGY = new MergeOntoFileTokensStrategy();

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final SignerProperties signerProperties;

    private final TokenContext tokenContext = new TokenContext();
    private List<Token> currentTokens;

    /**
     * Initializes the manager -- loads the tokens from the token configuration.
     *
     * @throws Exception if an error occurs
     */
    @PostConstruct
    public void init() {
        try {
            TokenConf.getInstance().load(signerProperties);
        } catch (Exception e) {
            log.error("Failed to load token conf", e);
        }

        currentTokens = new ArrayList<>(TokenConf.getInstance().getTokens());
    }

    /**
     * Saves the current tokens to the configuration.
     *
     * @throws Exception if an error occurs
     */
    public synchronized void saveToConf() throws Exception {
        log.trace("persist()");

        TokenConf.getInstance().save(currentTokens);
    }

    /**
     * Merge the in-memory configuration and the on-disk configuration if the configuration on
     * disk has changed.
     *
     * @param listener
     */
    public void merge(TokenMergeAddedCertificatesListener listener) {

        if (TokenConf.getInstance().hasChanged()) {
            log.debug("The key configuration on disk has changed, merging changes.");

            List<Token> fileTokens;
            try {
                fileTokens = TokenConf.getInstance().retrieveTokensFromConf();

            } catch (TokenConf.TokenConfException e) {
                log.error("Failed to load the new key configuration from disk.", e);
                return;
            }

            TokenMergeStrategy.MergeResult result;
            synchronized (TokenManager.class) {
                result = MERGE_STRATEGY.merge(fileTokens, currentTokens);
                currentTokens = result.getResultTokens();
            }
            if (listener != null) {
                listener.mergeDone(result.getAddedCertificates());
            }


            log.info("Merged new key configuration.");

        } else {
            log.debug("The key configuration on disk has not changed, skipping merge.");
        }
    }

    // Functional interfaces for token operations

    public class TokenContext {

        public List<Token> getTokens() {
            return currentTokens;
        }

        public Token findToken(String tokenId) {
            return TokenLookupUtils.findToken(currentTokens, tokenId);
        }

        public Key findKey(String keyId) {
            return TokenLookupUtils.findKey(currentTokens, keyId);
        }

        public Cert findCert(String certId) {
            return TokenLookupUtils.findCert(currentTokens, certId);
        }

        public <T> Optional<T> forCert(BiPredicate<Key, Cert> tester,
                                       BiFunction<Key, Cert, T> mapper) {
            return TokenLookupUtils.forCert(currentTokens, tester, mapper);
        }

        public <T> Optional<T> forKey(BiPredicate<Token, Key> tester,
                                      BiFunction<Token, Key, T> mapper) {
            return TokenLookupUtils.forKey(currentTokens, tester, mapper);
        }

        public <T> Optional<T> forToken(Predicate<Token> tester, Function<Token, T> mapper) {
            return TokenLookupUtils.forToken(currentTokens, tester, mapper);
        }

        <T> Optional<T> forCertRequest(BiPredicate<Key, CertRequest> tester,
                                       BiFunction<Key, CertRequest, T> mapper) {
            return TokenLookupUtils.forCertRequest(currentTokens, tester, mapper);
        }

    }

    @FunctionalInterface
    public interface TokenSupplier<T> {
        T get(TokenContext ctx);

    }

    @FunctionalInterface
    public interface TokenRunnable {
        void accept(TokenContext ctx);
    }

    public <T> T readAction(TokenSupplier<T> action) {
        rwLock.readLock().lock();
        try {
            return action.get(tokenContext);
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public <T> T writeAction(TokenSupplier<T> action) {
        rwLock.writeLock().lock();
        try {
            return action.get(tokenContext);
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void writeRun(TokenRunnable action) {
        rwLock.writeLock().lock();
        try {
            action.accept(tokenContext);
        } catch (Exception e) {
            throw translateException(e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

}
