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
package org.niis.xroad.ds.issuance.membership;

import org.eclipse.edc.spi.result.Result;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.cert.CertChainVerifier;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Date;

/**
 * PKIX cert chain validator that anchors trust in X-Road global conf. Delegates to the
 * existing {@link CertChainVerifier} which already implements the X-Road-specific
 * "find CA via globalconf, build PKIX path, validate against trust anchor" flow used by
 * SOAP signature verification.
 *
 * <p>OCSP is intentionally <em>not</em> checked here — the pinned-OCSP verification step is
 * performed downstream by {@link OcspVerifier}; this validator relies on
 * {@code CertChainVerifier.verifyChainOnly(...)}.
 *
 * <p>The X-Road instance is the verifier's own local instance from
 * {@link GlobalConfProvider#getInstanceIdentifier()}. Multi-instance federation (a holder
 * whose member is registered in a different X-Road instance) is not in scope.
 */
final class CertChainValidator {

    private final GlobalConfProvider globalConf;
    private final OcspVerifierFactory ocspVerifierFactory;
    private final Clock clock;

    CertChainValidator(GlobalConfProvider globalConf, OcspVerifierFactory ocspVerifierFactory, Clock clock) {
        this.globalConf = globalConf;
        this.ocspVerifierFactory = ocspVerifierFactory;
        this.clock = clock;
    }

    Result<Void> validate(X509Certificate memberCert) {
        try {
            var instanceId = globalConf.getInstanceIdentifier();
            var caCert = globalConf.getCaCert(instanceId, memberCert);
            var chain = CertChainFactory.create(instanceId, caCert, memberCert, null);
            new CertChainVerifier(globalConf, ocspVerifierFactory, chain).verifyChainOnly(Date.from(clock.instant()));
            return Result.success();
        } catch (Exception e) {
            return Result.failure(MembershipVerificationReason.CERT_CHAIN_INVALID.name() + ": " + e.getMessage());
        }
    }
}
