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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyProvider;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.CLAIM_AGREEMENT_ID;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.CLAIM_CLIENT;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.CLAIM_SCOPE;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.CLAIM_SERVICE;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.encodeClient;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.encodeScope;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.encodeService;

/**
 * Mints agreement tokens: a compact, HMAC-signed JWS (see {@code XRDADR-40} and the key-trust slice's
 * signing-scheme decision) stating exactly one grant, with the protocol envelope (issuer, audience, expiry)
 * derived from {@link AgreementTokenProtocolProperties} rather than hardcoded here.
 */
public final class AgreementTokenMinter {

    private final AgreementTokenKeyProvider keyProvider;
    private final AgreementTokenProtocolProperties properties;
    private final Clock clock;

    public AgreementTokenMinter(AgreementTokenKeyProvider keyProvider, AgreementTokenProtocolProperties properties) {
        this(keyProvider, properties, Clock.systemUTC());
    }

    public AgreementTokenMinter(AgreementTokenKeyProvider keyProvider, AgreementTokenProtocolProperties properties, Clock clock) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * @param grant exactly what is being granted; the minter adds the issuer/audience/expiry envelope
     * @return the compact-serialized, signed token
     */
    public String mint(AgreementTokenGrant grant) {
        Objects.requireNonNull(grant, "grant must not be null");

        var activeKey = keyProvider.activeKey();
        var now = Instant.now(clock);
        var expiresAt = now.plus(properties.tokenTtl());

        var claimsSet = new JWTClaimsSet.Builder()
                .issuer(properties.issuer())
                .audience(properties.audience())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim(CLAIM_AGREEMENT_ID, grant.agreementId())
                .claim(CLAIM_CLIENT, encodeClient(grant.client()))
                .claim(CLAIM_SERVICE, encodeService(grant.service()))
                .claim(CLAIM_SCOPE, encodeScope(grant.scope()))
                .build();

        var header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .keyID(activeKey.keyId())
                .build();

        var signedJwt = new SignedJWT(header, claimsSet);
        try {
            signedJwt.sign(new MACSigner(activeKey.secret()));
        } catch (JOSEException e) {
            throw XrdRuntimeException.systemException(ErrorCode.AGREEMENT_TOKEN_SIGNING_FAILED)
                    .cause(e)
                    .details("Failed to sign agreement token")
                    .build();
        }

        return signedJwt.serialize();
    }
}
