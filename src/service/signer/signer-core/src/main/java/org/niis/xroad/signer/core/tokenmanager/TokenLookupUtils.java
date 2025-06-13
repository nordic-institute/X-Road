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

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.core.model.CertRequestData;
import org.niis.xroad.signer.core.model.RuntimeCertImpl;
import org.niis.xroad.signer.core.model.RuntimeKeyImpl;
import org.niis.xroad.signer.core.model.RuntimeTokenImpl;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.niis.xroad.signer.core.util.ExceptionHelper.certWithIdNotFound;
import static org.niis.xroad.signer.core.util.ExceptionHelper.keyNotFound;
import static org.niis.xroad.signer.core.util.ExceptionHelper.tokenNotFound;

@Slf4j
@UtilityClass
class TokenLookupUtils {

    static RuntimeTokenImpl findToken(Iterable<RuntimeTokenImpl> tokens, String tokenId) {
        log.trace("findToken({})", tokenId);

        return forToken(tokens, t -> t.externalId().equals(tokenId), t -> t)
                .orElseThrow(() -> tokenNotFound(tokenId));
    }

    static RuntimeKeyImpl findKey(Iterable<RuntimeTokenImpl> tokens, String keyId) {
        log.trace("findKey({})", keyId);

        return forKey(tokens, (t, k) -> k.externalId().equals(keyId), (t, k) -> k)
                .orElseThrow(() -> keyNotFound(keyId));
    }

    static RuntimeCertImpl getCert(Iterable<RuntimeTokenImpl> tokens, String certId) {
        log.trace("findCert({})", certId);

        return findCert(tokens, certId)
                .orElseThrow(() -> certWithIdNotFound(certId));
    }

    static Optional<RuntimeCertImpl> findCert(Iterable<RuntimeTokenImpl> tokens, String certId) {
        log.trace("findCert({})", certId);

        return forCert(tokens, (k, c) ->
                c.externalId().equals(certId), (k, c) -> c);
    }

    static Optional<CertRequestData> findCertRequest(Iterable<RuntimeTokenImpl> tokens, String certReqId) {
        log.trace("findCertRequest({})", certReqId);

        return forCertRequest(tokens, (k, c) ->
                c.externalId().equals(certReqId), (k, c) -> c);
    }

    static <T> Optional<T> forToken(Iterable<RuntimeTokenImpl> tokens, Predicate<RuntimeTokenImpl> tester,
                                    Function<RuntimeTokenImpl, T> mapper) {
        for (RuntimeTokenImpl token : tokens) {
            if (tester.test(token)) {
                return Optional.ofNullable(mapper.apply(token));
            }
        }

        return Optional.empty();
    }

    static <T> Optional<T> forKey(Iterable<RuntimeTokenImpl> tokens,
                                  BiPredicate<RuntimeTokenImpl, RuntimeKeyImpl> tester,
                                  BiFunction<RuntimeTokenImpl, RuntimeKeyImpl, T> mapper) {
        for (var token : tokens) {
            for (var key : token.keys()) {
                if (tester.test(token, (RuntimeKeyImpl) key)) {
                    return Optional.ofNullable(mapper.apply(token, (RuntimeKeyImpl) key));
                }
            }
        }

        return Optional.empty();
    }

    static <T> Optional<T> forCert(Iterable<RuntimeTokenImpl> tokens,
                                   BiPredicate<RuntimeKeyImpl, RuntimeCertImpl> tester,
                                   BiFunction<RuntimeKeyImpl, RuntimeCertImpl, T> mapper) {
        for (var token : tokens) {
            for (var key : token.keys()) {
                for (var cert : key.certs()) {
                    if (tester.test((RuntimeKeyImpl) key, (RuntimeCertImpl) cert)) {
                        return Optional.ofNullable(mapper.apply((RuntimeKeyImpl) key, (RuntimeCertImpl) cert));
                    }
                }
            }
        }

        return Optional.empty();
    }

    static <T> Optional<T> forCertRequest(Iterable<RuntimeTokenImpl> tokens,
                                          BiPredicate<RuntimeKeyImpl, CertRequestData> tester,
                                          BiFunction<RuntimeKeyImpl, CertRequestData, T> mapper) {
        for (var token : tokens) {
            for (var key : token.keys()) {
                for (var certReq : key.certRequests()) {
                    if (tester.test((RuntimeKeyImpl) key, certReq)) {
                        return Optional.ofNullable(mapper.apply((RuntimeKeyImpl) key, certReq));
                    }
                }
            }
        }

        return Optional.empty();
    }
}
