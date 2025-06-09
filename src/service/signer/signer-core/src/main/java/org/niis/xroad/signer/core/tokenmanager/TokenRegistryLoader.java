/*
 * The MIT License
 *
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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.core.certmanager.OcspResponseManager;
import org.niis.xroad.signer.core.model.BasicCertInfo;
import org.niis.xroad.signer.core.model.BasicKeyInfo;
import org.niis.xroad.signer.core.model.BasicTokenInfo;
import org.niis.xroad.signer.core.model.RuntimeCertImpl;
import org.niis.xroad.signer.core.model.RuntimeKey;
import org.niis.xroad.signer.core.model.RuntimeKeyImpl;
import org.niis.xroad.signer.core.model.RuntimeTokenImpl;
import org.niis.xroad.signer.core.service.TokenService;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TokenRegistryLoader {
    private final TokenService tokenService;
    private final OcspResponseManager ocspResponseManager;

    public Set<RuntimeTokenImpl> loadTokens() {
        try {
            var stopWatch = StopWatch.createStarted();

            var loadedTokenData = tokenService.loadAllTokens();

            var result = loadedTokenData.tokens().stream()
                    .map(basicTokenInfo -> createRuntimeToken(loadedTokenData, basicTokenInfo))
                    .collect(Collectors.toSet());
            log.info("Loaded {} tokens with {} keys and {} certs in {} ms",
                    result.size(),
                    loadedTokenData.keys().size(),
                    loadedTokenData.certs().size(),
                    stopWatch.getDuration().toMillis());

            return result;
        } catch (Exception e) {
            throw new SignerException("Failed to load tokens", e);
        }
    }

    public Set<RuntimeTokenImpl> refreshTokens(Set<RuntimeTokenImpl> currentTokens) {
        try {
            var stopWatch = StopWatch.createStarted();

            var loadedTokenData = tokenService.loadAllTokens();
            // Create maps for efficient lookup of existing entries
            var existingTokens = currentTokens.stream()
                    .collect(Collectors.toMap(RuntimeTokenImpl::id, token -> token));

            // Create new set of tokens
            var newTokens = loadedTokenData.tokens().stream()
                    .map(basicTokenInfo -> createRuntimeToken(loadedTokenData, basicTokenInfo))
                    .collect(Collectors.toSet());

            // Process each token from the loaded data
            newTokens.forEach(newToken -> {
                var existingToken = existingTokens.get(newToken.id());
                if (existingToken != null) {
                    updateTokenWithTransientData(newToken, existingToken);
                }
            });

            long tokensLoadTime = stopWatch.getDuration().toMillis();

            var certHashesToRefresh = loadedTokenData.certs().values().stream()
                    .flatMap(Collection::stream)
                    .map(BasicCertInfo::sha256hash)
                    .toList();

            ocspResponseManager.refreshCache(certHashesToRefresh);

            log.info("Reloaded {} tokens with {} keys and {} certs in {} ms. OCSP cache refreshed for {} certs in {} ms",
                    currentTokens.size(),
                    loadedTokenData.keys().size(),
                    loadedTokenData.certs().size(),
                    tokensLoadTime,
                    certHashesToRefresh.size(),
                    stopWatch.getDuration().toMillis());


            return newTokens;
        } catch (Exception e) {
            throw new SignerException("Failed to reload tokens", e);
        }
    }

    private void updateTokenWithTransientData(RuntimeTokenImpl newToken, RuntimeTokenImpl existingToken) {

        newToken.transferTransientData(existingToken);

        var existingKeys = existingToken.keys().stream()
                .collect(Collectors.toMap(RuntimeKey::id, key -> key));
        newToken.keys().forEach(newKey -> {

            var existingKey = existingKeys.get(newKey.id());
            if (existingKey != null) {
                ((RuntimeKeyImpl) newKey).transferTransientData(existingKey);
            }
        });


    }

    private RuntimeTokenImpl createRuntimeToken(TokenService.LoadedTokens loadedTokens, BasicTokenInfo basicTokenInfo) {
        var runtimeToken = new RuntimeTokenImpl();
        runtimeToken.setData(basicTokenInfo);

        var keys = loadedTokens.keys().get(basicTokenInfo.id());
        if (keys != null) {
            keys.forEach(key -> runtimeToken.addKey(createRuntimeKey(loadedTokens, key)));
        }
        return runtimeToken;
    }

    private RuntimeKeyImpl createRuntimeKey(TokenService.LoadedTokens loadedTokens, BasicKeyInfo basicKeyInfo) {
        var runtimeKey = new RuntimeKeyImpl();
        runtimeKey.setData(basicKeyInfo);

        var certs = loadedTokens.certs().get(basicKeyInfo.id());
        if (certs != null) {
            certs.forEach(certInfo -> {
                var runtimeCert = createRuntimeCert(certInfo);
                runtimeKey.addCert(runtimeCert);
            });
        }

        var certRequests = loadedTokens.certRequests().get(basicKeyInfo.id());
        if (certRequests != null) {
            certRequests.forEach(runtimeKey::addCertRequest);
        }
        return runtimeKey;
    }


    private RuntimeCertImpl createRuntimeCert(BasicCertInfo certInfo) {
        var runtimeCert = new RuntimeCertImpl();
        runtimeCert.setData(certInfo);
        return runtimeCert;
    }

}
