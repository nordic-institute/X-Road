/*
 *  Copyright (c) 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.jwt.rules;

import org.assertj.core.api.Assertions;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.token.rules.ExpirationIssuedAtValidationRule;
import org.eclipse.edc.token.spi.TokenValidationRule;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.nimbusds.jwt.JWTClaimNames.EXPIRATION_TIME;
import static com.nimbusds.jwt.JWTClaimNames.ISSUED_AT;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

class ExpirationIssuedAtValidationRuleTest {

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private final Clock clock = Clock.fixed(now, UTC);
    private final TokenValidationRule rule = new ExpirationIssuedAtValidationRule(clock, 0);

    @Test
    void validationOk() {
        var token = ClaimToken.Builder.newInstance()
                .claim(EXPIRATION_TIME, Date.from(now.plusSeconds(600)))
                .build();

        var result = rule.checkRule(token, emptyMap());

        Assertions.assertThat(result.succeeded()).isTrue();
    }

    @Test
    void validationKoBecauseExpirationTimeNotRespected() {
        var token = ClaimToken.Builder.newInstance()
                .claim(EXPIRATION_TIME, Date.from(now.minusSeconds(10)))
                .build();

        var result = rule.checkRule(token, emptyMap());

        Assertions.assertThat(result.succeeded()).isFalse();
        Assertions.assertThat(result.getFailureMessages()).hasSize(1)
                .contains("Token has expired (exp)");
    }

    @Test
    void validationKoBecauseExpirationTimeNotProvided() {
        var token = ClaimToken.Builder.newInstance().build();

        var result = rule.checkRule(token, emptyMap());

        Assertions.assertThat(result.succeeded()).isFalse();
        Assertions.assertThat(result.getFailureMessages()).hasSize(1)
                .contains("Required expiration time (exp) claim is missing in token");
    }

    @Test
    void validationKoBecauseIssuedAtAfterExpires() {
        var token = ClaimToken.Builder.newInstance()
                .claim(EXPIRATION_TIME, Date.from(now.plusSeconds(60)))
                .claim(ISSUED_AT, Date.from(now.plusSeconds(65)))
                .build();

        var result = rule.checkRule(token, emptyMap());

        Assertions.assertThat(result.succeeded()).isFalse();
        Assertions.assertThat(result.getFailureMessages()).hasSize(1)
                .contains("Issued at (iat) claim is after expiration time (exp) claim in token");
    }

    @Test
    void validationKoBecauseIssuedAtInFuture() {
        var token = ClaimToken.Builder.newInstance()
                .claim(EXPIRATION_TIME, Date.from(now.plusSeconds(60)))
                .claim(ISSUED_AT, Date.from(now.plusSeconds(10)))
                .build();

        var result = rule.checkRule(token, emptyMap());

        Assertions.assertThat(result.succeeded()).isFalse();
        Assertions.assertThat(result.getFailureMessages()).hasSize(1).contains("Current date/time before issued at (iat) claim in token");
    }

    @Test
    void validationKoBecauseIssuedAtInFutureOutsideLeeway() {
        var expirationRule = new ExpirationIssuedAtValidationRule(clock, 5);

        var token = ClaimToken.Builder.newInstance()
                .claim(EXPIRATION_TIME, Date.from(now.plusSeconds(60)))
                .claim(ISSUED_AT, Date.from(now.plusSeconds(10)))
                .build();

        var result = expirationRule.checkRule(token, emptyMap());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.getFailureMessages()).hasSize(1).contains("Current date/time before issued at (iat) claim in token");
    }

    @Test
    void validationOkBecauseIssuedAtInFutureButWithinLeeway() {
        var expirationRule = new ExpirationIssuedAtValidationRule(clock, 20);

        var token = ClaimToken.Builder.newInstance()
                .claim(EXPIRATION_TIME, Date.from(now.plusSeconds(60)))
                .claim(ISSUED_AT, Date.from(now.plusSeconds(10)))
                .build();

        var result = expirationRule.checkRule(token, emptyMap());

        assertThat(result.succeeded()).isTrue();
    }

    /**
     * Demonstrates situation where rounded dates in the JWT token cause validation failures
     * <br>
     * Rounding of dates in JWT is within spec and the direction of rounding is platform-dependant.
     */
    @Test
    void validationKoWithRoundedIssuedAtAndNoLeeway() {
        // time skew: tokens have dates rounded up to the second
        var issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var expiresAt = issuedAt.plusSeconds(60);

        // time skew: the connector is still in the previous second, with unrounded dates
        var testNow = issuedAt.minus(250, ChronoUnit.MILLIS);

        var fixedClock = Clock.fixed(testNow, UTC);
        var expirationRule = new ExpirationIssuedAtValidationRule(fixedClock, 0);

        var token = ClaimToken.Builder.newInstance()
                .claim(EXPIRATION_TIME, Date.from(expiresAt))
                .claim(ISSUED_AT, Date.from(issuedAt))
                .build();

        var result = expirationRule.checkRule(token, emptyMap());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.getFailureMessages()).hasSize(1).contains("Current date/time before issued at (iat) claim in token");
    }

    /**
     * Regression test against clock skew and platform-dependant rounding of dates, solved with a 2s leeway.
     * <br>
     * Rounding of dates in JWT is within spec and the direction of rounding is platform-dependant.
     */
    @Test
    void validationOkWithRoundedIssuedAtAndMinimalLeeway() {
        // time skew: tokens have dates rounded up to the second
        var issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var expiresAt = issuedAt.plusSeconds(60);

        // time skew: the connector is still in the previous second, with unrounded dates
        var testNow = issuedAt.minus(250, ChronoUnit.MILLIS);

        var fixedClock = Clock.fixed(testNow, UTC);
        var expirationRule = new ExpirationIssuedAtValidationRule(fixedClock, 2);

        var token = ClaimToken.Builder.newInstance()
                .claim(EXPIRATION_TIME, Date.from(expiresAt))
                .claim(ISSUED_AT, Date.from(issuedAt))
                .build();

        var result = expirationRule.checkRule(token, emptyMap());

        assertThat(result.succeeded()).isTrue();
    }
}
