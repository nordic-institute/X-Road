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

import static com.nimbusds.jwt.JWTClaimNames.NOT_BEFORE;


/**
 * Token validation rule that checks if the "not before" claim is valid
 */
public class NotBeforeValidationRule implements TokenValidationRule {
    private final Clock clock;
    private final int notBeforeLeeway;

    public NotBeforeValidationRule(Clock clock, int notBeforeLeeway) {
        this.clock = clock;
        this.notBeforeLeeway = notBeforeLeeway;
    }

    @Override
    public Result<Void> checkRule(@NotNull ClaimToken toVerify, @Nullable Map<String, Object> additional) {
        var now = clock.instant();
        var leewayNow = now.plusSeconds(notBeforeLeeway);
        var notBefore = toVerify.getInstantClaim(NOT_BEFORE);

        if (notBefore == null) {
            return Result.failure("Required not before (nbf) claim is missing in token");
        } else if (leewayNow.isBefore(notBefore)) {
            return Result.failure("Current date/time with leeway before the not before (nbf) claim in token");
        }

        return Result.success();
    }
}
