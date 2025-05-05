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
import org.niis.xroad.signer.core.model.Cert;
import org.niis.xroad.signer.core.model.CertRequest;
import org.niis.xroad.signer.core.model.Key;
import org.niis.xroad.signer.core.model.Token;

import java.util.Collection;
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

    static Token findToken(Collection<Token> tokens, String tokenId) {
        log.trace("findToken({})", tokenId);

        return forToken(tokens, t -> t.getId().equals(tokenId), t -> t)
                .orElseThrow(() -> tokenNotFound(tokenId));
    }

    static Key findKey(Collection<Token> tokens, String keyId) {
        log.trace("findKey({})", keyId);

        return forKey(tokens, (t, k) -> k.getId().equals(keyId), (t, k) -> k)
                .orElseThrow(() -> keyNotFound(keyId));
    }

    static Cert findCert(Collection<Token> tokens, String certId) {
        log.trace("findCert({})", certId);

        return forCert(tokens, (k, c) -> c.getId().equals(certId), (k, c) -> c)
                .orElseThrow(() -> certWithIdNotFound(certId));
    }

    static <T> Optional<T> forToken(Collection<Token> tokens, Predicate<Token> tester, Function<Token, T> mapper) {
        for (Token token : tokens) {
            if (tester.test(token)) {
                return Optional.ofNullable(mapper.apply(token));
            }
        }

        return Optional.empty();
    }

    static <T> Optional<T> forKey(Collection<Token> tokens,
                                  BiPredicate<Token, Key> tester,
                                  BiFunction<Token, Key, T> mapper) {
        for (Token token : tokens) {
            for (Key key : token.getKeys()) {
                if (tester.test(token, key)) {
                    return Optional.ofNullable(mapper.apply(token, key));
                }
            }
        }

        return Optional.empty();
    }

    static <T> Optional<T> forCert(Collection<Token> tokens,
                                   BiPredicate<Key, Cert> tester,
                                   BiFunction<Key, Cert, T> mapper) {
        for (Token token : tokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (tester.test(key, cert)) {
                        return Optional.ofNullable(mapper.apply(key, cert));
                    }
                }
            }
        }

        return Optional.empty();
    }

    static <T> Optional<T> forCertRequest(Collection<Token> tokens,
                                          BiPredicate<Key, CertRequest> tester,
                                          BiFunction<Key, CertRequest, T> mapper) {
        for (Token token : tokens) {
            for (Key key : token.getKeys()) {
                for (CertRequest certReq : key.getCertRequests()) {
                    if (tester.test(key, certReq)) {
                        return Optional.ofNullable(mapper.apply(key, certReq));
                    }
                }
            }
        }

        return Optional.empty();
    }
}
