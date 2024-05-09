/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.iam.verifiablecredentials.rules;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.CredentialValidationRule;
import org.eclipse.edc.spi.result.Result;

import java.time.Clock;

/**
 * Rule that verifies, that a credential is already valid ({@code issuanceDate} is before NOW), and that it is not yet expired.
 * {@code expirationDate} is not mandatory, so expiration is only checked if it is present.
 */
public class IsInValidityPeriod implements CredentialValidationRule {
    private final Clock clock;

    public IsInValidityPeriod(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Result<Void> apply(VerifiableCredential credential) {
        var now = clock.instant();
        // issuance date can not be null, due to builder validation
        if (credential.getIssuanceDate().isAfter(now)) {
            return Result.failure("Credential is not yet valid.");
        }
        if (credential.getExpirationDate() != null && credential.getExpirationDate().isBefore(now)) {
            return Result.failure("Credential expired.");
        }
        return Result.success();
    }
}
