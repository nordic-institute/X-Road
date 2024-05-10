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
package ee.ria.xroad.signer.tokenmanager.module;

import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.tokenmanager.token.AbstractTokenWorker;
import ee.ria.xroad.signer.tokenmanager.token.BlockingTokenWorker;
import ee.ria.xroad.signer.tokenmanager.token.TokenType;
import ee.ria.xroad.signer.tokenmanager.token.TokenWorker;
import ee.ria.xroad.signer.tokenmanager.token.WorkerWithLifecycle;

import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.translateException;

/**
 * Module worker base class.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractModuleWorker implements WorkerWithLifecycle {
    @SuppressWarnings("java:S3077")
    private volatile Map<String, BlockingTokenWorker> tokenWorkers = Collections.emptyMap();

    @Getter
    private final ModuleType moduleType;

    public Optional<TokenWorker> getTokenById(String tokenId) {
        return Optional.ofNullable(tokenWorkers.get(tokenId));
    }

    @Override
    public void reload() {
        log.warn("Reloading {}.. ", getClass().getSimpleName());
        try {
            loadTokens(true);
        } catch (Exception e) {
            log.error("Error during module {} reload. It will be repeated on next scheduled module refresh..",
                    getClass().getSimpleName(), e);
            throw translateException(e);
        }
    }

    @Override
    public void refresh() {
        try {
            loadTokens(false);
        } catch (PKCS11Exception pkcs11Exception) {
            log.warn("PKCS11Exception was thrown. Reloading underlying module and token workers.", pkcs11Exception);
            reload();
        } catch (Exception e) {
            log.error("Error during update of module " + getClass().getSimpleName(), e);
            throw translateException(e);
        }
    }

    @Override
    public void stop() {
        stopLostTokenWorkers(tokenWorkers, List.of());
        tokenWorkers = Collections.emptyMap();
    }

    protected abstract List<TokenType> listTokens() throws Exception;

    protected abstract AbstractTokenWorker createWorker(TokenInfo tokenInfo, TokenType tokenType);

    private void loadTokens(boolean reload) throws Exception {
        final Map<String, BlockingTokenWorker> newTokens = new HashMap<>();

        final List<TokenType> tokens = listTokens();
        log.trace("Got {} tokens from module '{}'", tokens.size(), getClass().getSimpleName());

        for (TokenType tokenType : tokens) {
            BlockingTokenWorker tokenWorker = tokenWorkers.get(tokenType.getId());
            if (tokenWorker == null) {
                log.debug("Adding new token '{}#{}'", tokenType.getModuleType(), tokenType.getId());
                tokenWorker = new BlockingTokenWorker(createWorker(getTokenInfo(tokenType), tokenType));
                tokenWorker.start();
            } else if (reload) {
                tokenWorker.reload();
            }

            tokenWorker.refresh();
            newTokens.put(tokenType.getId(), tokenWorker);
        }

        final var oldTokenWorkers = tokenWorkers;
        tokenWorkers = Collections.unmodifiableMap(newTokens);
        stopLostTokenWorkers(oldTokenWorkers, tokens);
    }

    private void stopLostTokenWorkers(Map<String, BlockingTokenWorker> oldTokens, List<TokenType> newTokens) {
        final Set<String> moduleTypes = newTokens.stream()
                .map(TokenType::getId)
                .collect(Collectors.toSet());

        for (Map.Entry<String, BlockingTokenWorker> entry : oldTokens.entrySet()) {
            if (!moduleTypes.contains(entry.getKey())) {
                try {
                    log.trace("Stopping token worker for module '{}'", entry.getKey());
                    entry.getValue().stop();
                } catch (Exception e) {
                    log.error("Failed to deinitialize ");
                }
            }
        }
    }

    private static TokenInfo getTokenInfo(TokenType tokenType) {
        TokenInfo info = TokenManager.getTokenInfo(tokenType.getId());

        if (info != null) {
            TokenManager.setTokenAvailable(tokenType, true);

            return info;
        } else {
            return TokenManager.createToken(tokenType);
        }
    }
}
