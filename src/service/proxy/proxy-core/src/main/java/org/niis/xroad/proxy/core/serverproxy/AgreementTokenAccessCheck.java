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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.agreementtoken.AgreementTokenRequestContext;
import org.niis.xroad.common.agreementtoken.AgreementTokenVerificationResult;
import org.niis.xroad.common.agreementtoken.AgreementTokenVerifier;
import org.niis.xroad.proxy.core.configuration.AgreementTokenKeyMaterial;
import org.niis.xroad.proxy.core.configuration.ProxyAgreementTokenProperties;

/**
 * Decides, at the exact point the server proxy would otherwise consult the ACL, whether an incoming
 * {@code x-road-agreement-token} header grants exactly what the request is asking for. Every outcome except a
 * full match — header absent or blank, no key material, a malformed or expired token, any claim mismatch, or an
 * unexpected failure while verifying — returns {@code false}, so the caller always falls back to the ACL. The
 * token never authenticates: it only ever widens which requests skip an ACL check that would otherwise run.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AgreementTokenAccessCheck {

    private final AgreementTokenKeyMaterial keyMaterial;
    private final ProxyAgreementTokenProperties agreementTokenProperties;

    /**
     * @return {@code true} if {@code agreementToken} is a valid token granting {@code requestClient} full access
     *         to {@code requestService}, so the SOAP ACL check may be skipped
     */
    public boolean allowsAclSkip(String agreementToken, ClientId requestClient, ServiceId requestService) {
        return allowsAclSkip(agreementToken, AgreementTokenRequestContext.forSoap(requestClient, requestService));
    }

    /**
     * @return {@code true} if {@code agreementToken} is a valid token granting {@code requestClient} access to
     *         {@code requestMethod}/{@code requestPath} on {@code requestService}, so the REST ACL check may be
     *         skipped
     */
    public boolean allowsAclSkip(String agreementToken, ClientId requestClient, ServiceId requestService,
                                  String requestMethod, String requestPath) {
        return allowsAclSkip(agreementToken,
                AgreementTokenRequestContext.forRest(requestClient, requestService, requestMethod, requestPath));
    }

    private boolean allowsAclSkip(String agreementToken, AgreementTokenRequestContext context) {
        if (agreementToken == null || agreementToken.isBlank()) {
            return false;
        }

        var provider = keyMaterial.provider();
        if (provider.isEmpty()) {
            return false;
        }

        try {
            var result = new AgreementTokenVerifier(provider.get(), agreementTokenProperties).verify(agreementToken, context);
            if (result instanceof AgreementTokenVerificationResult.Valid valid) {
                log.debug("agreement token accepted, skipping ACL: agreementId={}", valid.claims().agreementId());
                return true;
            }
            if (result instanceof AgreementTokenVerificationResult.Rejected rejected) {
                log.debug("agreement token rejected: reason={}", rejected.reason());
            }
        } catch (RuntimeException e) {
            log.debug("agreement token verification failed unexpectedly; falling back to the ACL", e);
        }
        return false;
    }
}
