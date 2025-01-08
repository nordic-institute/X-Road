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

package org.niis.xroad.proxy.edc;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jwt.JWTParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static java.lang.System.currentTimeMillis;

@Slf4j
@Singleton
public class InMemoryAuthorizedAssetRegistry implements AuthorizedAssetRegistry {

    @SuppressWarnings("checkstyle:MagicNumber")
    private final Cache<CacheKey, GrantedAssetInfo> cache = Caffeine.newBuilder()
//TODO dp signaling token does not have exp set on it
//            .expireAfter(new Expiry<CacheKey, GrantedAssetInfo>() {
//                @Override
//                public long expireAfterCreate(CacheKey key, GrantedAssetInfo value, long currentTime) {
//                    long expirationUnixTimeNanos = extractExpirationTimeFromJWT(value.authCode());
//
//                    var expiresIn = Math.max(expirationUnixTimeNanos - currentTimeMillis(), 0);
//                    log.trace("transferId {} will expire in {} seconds", value.id(), TimeUnit.MILLISECONDS.toSeconds(expiresIn));
//                    return TimeUnit.MILLISECONDS.toNanos(expiresIn);
//                }
//
//                @Override
//                public long expireAfterUpdate(CacheKey key, GrantedAssetInfo value,
//                                              long currentTime, long currentDuration) {
//                    return currentDuration;
//                }
//
//                @Override
//                public long expireAfterRead(CacheKey key, GrantedAssetInfo value,
//                                            long currentTime, long currentDuration) {
//                    return currentDuration;
//                }
//            })
            .build();

    @Override
    public Optional<GrantedAssetInfo> getAssetInfo(String clientId, String serviceId) {
        return Optional.ofNullable(cache.getIfPresent(new CacheKey(clientId, serviceId)));
    }

    @Override
    public void registerAsset(String clientId, String serviceId, GrantedAssetInfo assetInfo) {
        cache.put(new CacheKey(clientId, serviceId), assetInfo);
    }

    public record CacheKey(String clientId, String serviceId) {
        @Override
        public int hashCode() {
            return clientId.hashCode() + serviceId.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            CacheKey other = (CacheKey) obj;
            return clientId.equals(other.clientId) && serviceId.equals(other.serviceId);
        }
    }

    private static long extractExpirationTimeFromJWT(String authCode) {
        if (StringUtils.isNotBlank(authCode)) {
            try {
                var jwt = JWTParser.parse(authCode);

                return jwt.getJWTClaimsSet().getExpirationTime().getTime();
            } catch (Exception e) {
                log.error("Failed to parse JWT expirationdate", e);
            }
        }
        return currentTimeMillis();
    }
}
