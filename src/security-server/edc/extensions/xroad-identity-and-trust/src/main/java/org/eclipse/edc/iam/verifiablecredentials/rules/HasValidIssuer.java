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

import java.util.Collection;

import static org.eclipse.edc.spi.result.Result.failure;
import static org.eclipse.edc.spi.result.Result.success;

/**
 * A class that implements the {@link CredentialValidationRule} interface and checks if a {@link VerifiableCredential} has a valid issuer.
 * Valid issuers are stored in a global list.
 * <p>
 * If the issuer object is neither a string nor an object containing an "id" field, a failure is returned.
 */
public class HasValidIssuer implements CredentialValidationRule {
    private final Collection<String> trustedIssuers;

    public HasValidIssuer(Collection<String> trustedIssuers) {
        this.trustedIssuers = trustedIssuers;
    }

    @Override
    public Result<Void> apply(VerifiableCredential credential) {
        var issuer = credential.getIssuer();
        if (issuer.id() == null) {
            return failure("Issuer did not contain an 'id' field.");
        }
        return trustedIssuers.contains(issuer.id()) ? success() : failure("Issuer '%s' is not in the list of trusted issuers".formatted(issuer.id()));
    }
}
