/*
 *  Copyright (c) 2022 - 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.token.rules;

import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.token.spi.TokenValidationRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.util.Map;

import static com.nimbusds.jwt.JWTClaimNames.EXPIRATION_TIME;
import static com.nimbusds.jwt.JWTClaimNames.ISSUED_AT;


/**
 * Token validation rule that checks if the token is not expired and if the "issued at" claim is valued correctly
 */
public class ExpirationIssuedAtValidationRule implements TokenValidationRule {

    private final Clock clock;
    private final int issuedAtLeeway;

    public ExpirationIssuedAtValidationRule(Clock clock, int issuedAtLeeway) {
        this.clock = clock;
        this.issuedAtLeeway = issuedAtLeeway;
    }

    @Override
    public Result<Void> checkRule(@NotNull ClaimToken toVerify, @Nullable Map<String, Object> additional) {
        var now = clock.instant();
        var expires = toVerify.getInstantClaim(EXPIRATION_TIME);
        if (expires == null) {
            return Result.failure("Required expiration time (exp) claim is missing in token");
        } else if (now.isAfter(expires)) {
            return Result.failure("Token has expired (exp)");
        }

        var issuedAt = toVerify.getInstantClaim(ISSUED_AT);
        if (issuedAt != null) {
            if (issuedAt.isAfter(expires)) {
                return Result.failure("Issued at (iat) claim is after expiration time (exp) claim in token");
            } else if (now.plusSeconds(issuedAtLeeway).isBefore(issuedAt)) {
                return Result.failure("Current date/time before issued at (iat) claim in token");
            }
        }

        return Result.success();
    }

}
