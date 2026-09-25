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
package org.niis.xroad.common.agreementtoken;

import java.time.Duration;

/**
 * The protocol-level configuration {@link AgreementTokenMinter} and {@link AgreementTokenVerifier} both need to
 * agree on out of band. A minimal, framework-agnostic interface — like {@code AcmeSchedulingProperties} in the
 * ACME library — so the host application (Spring or Quarkus) can implement it directly from its own
 * configuration surface instead of this library dictating one.
 */
public interface AgreementTokenProtocolProperties {

    /**
     * The recommended default token time-to-live (XRDADR-40: ~60 seconds, matching the ACL cache's own
     * staleness bound). Not applied automatically — every host must wire a concrete {@link #tokenTtl()}, so the
     * TTL is always explicit configuration, never a value baked into the signing logic.
     */
    Duration DEFAULT_TOKEN_TTL = Duration.ofSeconds(60);

    /**
     * @return the {@code iss} claim value the minter stamps and the verifier requires
     */
    String issuer();

    /**
     * @return the {@code aud} claim value the minter stamps and the verifier requires
     */
    String audience();

    /**
     * @return how long a minted token remains valid from the moment it is minted
     */
    Duration tokenTtl();
}
