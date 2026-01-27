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
package org.niis.xroad.signer.core.tokenmanager.module;

import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.core.tokenmanager.token.AbstractTokenWorker;
import org.niis.xroad.signer.core.tokenmanager.token.TokenDefinition;
import org.niis.xroad.signer.core.tokenmanager.token.WorkerWithLifecycle;

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
    private volatile Map<String, AbstractTokenWorker> tokenWorkers = Collections.emptyMap();

    @Getter
    private final ModuleType moduleType;

    protected final TokenManager tokenManager;
    protected final TokenLookup tokenLookup;

    public Optional<AbstractTokenWorker> getTokenById(String tokenId) {
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
            log.error("Error during update of module {}", getClass().getSimpleName(), e);
            throw translateException(e);
        }
    }

    @Override
    public void destroy() {
        stopLostTokenWorkers(tokenWorkers, List.of());
        tokenWorkers = Collections.emptyMap();
    }

    protected abstract List<TokenDefinition> listTokens() throws TokenException;

    protected abstract AbstractTokenWorker createWorker(TokenInfo tokenInfo, TokenDefinition tokenDefinition);

    private void loadTokens(boolean reload) throws TokenException {
        final Map<String, AbstractTokenWorker> newTokens = new HashMap<>();

        final List<TokenDefinition> tokens = listTokens();
        log.trace("Got {} tokens from module '{}'", tokens.size(), getClass().getSimpleName());

        for (TokenDefinition tokenDefinition : tokens) {
            AbstractTokenWorker tokenWorker = tokenWorkers.get(tokenDefinition.getId());
            if (tokenWorker == null) {
                log.debug("Adding new token '{}#{}'", tokenDefinition.moduleType(), tokenDefinition.getId());
                tokenWorker = createWorker(getTokenInfo(tokenDefinition), tokenDefinition);
                tokenWorker.start();
            } else if (reload) {
                tokenWorker.reload();
            }

            tokenWorker.refresh();
            newTokens.put(tokenDefinition.getId(), tokenWorker);
        }

        final var oldTokenWorkers = tokenWorkers;
        tokenWorkers = Collections.unmodifiableMap(newTokens);
        stopLostTokenWorkers(oldTokenWorkers, tokens);
    }

    private void stopLostTokenWorkers(Map<String, AbstractTokenWorker> oldTokens, List<TokenDefinition> newTokens) {
        final Set<String> moduleTypes = newTokens.stream()
                .map(TokenDefinition::getId)
                .collect(Collectors.toSet());

        for (Map.Entry<String, AbstractTokenWorker> entry : oldTokens.entrySet()) {
            if (!moduleTypes.contains(entry.getKey())) {
                try {
                    log.trace("Stopping token worker for module '{}'", entry.getKey());
                    entry.getValue().destroy();
                } catch (Exception e) {
                    log.error("Failed to deinitialize ");
                }
            }
        }
    }

    private TokenInfo getTokenInfo(TokenDefinition tokenDefinition) {
        TokenInfo info = tokenLookup.getTokenInfo(tokenDefinition.getId());

        if (info != null) {
            tokenManager.enableToken(tokenDefinition);

            return info;
        } else {
            return tokenManager.createToken(tokenDefinition);
        }
    }
}
