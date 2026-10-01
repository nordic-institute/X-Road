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

import java.util.function.Supplier;

/**
 * Decides, at the exact point the server proxy would otherwise consult the ACL, what an incoming
 * {@code x-road-agreement-token} header means for the current request. A presented token is authoritative: a
 * full match ({@link Decision.Accepted}) skips the ACL, and anything else the verifier can pin on the token
 * itself — a malformed or expired token, any claim mismatch, an unknown key id, an unbuildable request context,
 * or an unexpected failure while verifying — is a {@link Decision.Rejected} that refuses the request outright.
 * Only the absence of a header ({@link Decision.Absent}) or the absence of key material to judge it with
 * ({@link Decision.Unavailable}) falls back to the ACL, exactly as today. The token never authenticates: it is
 * never read before the header is known to be present, and a rejection never widens access.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AgreementTokenAccessCheck {

    private static final Decision ABSENT = new Decision.Absent();
    private static final Decision UNAVAILABLE = new Decision.Unavailable();
    private static final Decision ACCEPTED = new Decision.Accepted();

    private final AgreementTokenKeyMaterial keyMaterial;
    private final ProxyAgreementTokenProperties agreementTokenProperties;

    /** What a presented (or absent) agreement token means for the request at hand. */
    public sealed interface Decision {
        /** No header was presented; the ACL runs exactly as today. */
        record Absent() implements Decision { }

        /** A header was presented but no key material is available to judge it; the ACL runs, logged at warn. */
        record Unavailable() implements Decision { }

        /** The token fully matches the request; the ACL is skipped. */
        record Accepted() implements Decision { }

        /** The token was presented but refused; the request must be refused, never the ACL. */
        record Rejected(String reasonClass) implements Decision { }
    }

    /**
     * @return the decision for a SOAP request: {@code requestClient} and {@code requestService} are the
     *         signature-proven client and the requested service
     */
    public Decision decide(String agreementToken, ClientId requestClient, ServiceId requestService) {
        return decide(agreementToken, requestClient, requestService,
                () -> AgreementTokenRequestContext.forSoap(requestClient, requestService));
    }

    /**
     * @return the decision for a REST request: {@code requestClient} and {@code requestService} are the
     *         signature-proven client and the requested service, {@code requestMethod}/{@code requestPath} the
     *         actual verb and path
     */
    public Decision decide(String agreementToken, ClientId requestClient, ServiceId requestService,
                            String requestMethod, String requestPath) {
        return decide(agreementToken, requestClient, requestService,
                () -> AgreementTokenRequestContext.forRest(requestClient, requestService, requestMethod, requestPath));
    }

    private Decision decide(String agreementToken, ClientId requestClient, ServiceId requestService,
                             Supplier<AgreementTokenRequestContext> contextSupplier) {
        if (agreementToken == null || agreementToken.isBlank()) {
            return ABSENT;
        }

        var provider = keyMaterial.provider();
        if (provider.isEmpty()) {
            log.warn("agreement token presented but no key material is available to verify it; falling back to the ACL: "
                    + "client={}, service={}", requestClient, requestService);
            return UNAVAILABLE;
        }

        AgreementTokenRequestContext context;
        try {
            context = contextSupplier.get();
        } catch (RuntimeException e) {
            log.warn("agreement token presented but the request context could not be built; refusing the request: "
                    + "client={}, service={}", requestClient, requestService, e);
            return new Decision.Rejected("CONTEXT");
        }

        try {
            var result = new AgreementTokenVerifier(provider.get(), agreementTokenProperties).verify(agreementToken, context);
            if (result instanceof AgreementTokenVerificationResult.Valid valid) {
                log.debug("agreement token accepted, skipping ACL: agreementId={}", valid.claims().agreementId());
                return ACCEPTED;
            }
            var reason = ((AgreementTokenVerificationResult.Rejected) result).reason();
            log.warn("agreement token rejected: reason={}, client={}, service={}", reason, requestClient, requestService);
            return new Decision.Rejected(reason.name());
        } catch (RuntimeException e) {
            log.warn("agreement token verification failed unexpectedly; refusing the request: client={}, service={}",
                    requestClient, requestService, e);
            return new Decision.Rejected("VERIFIER_ERROR");
        }
    }
}
