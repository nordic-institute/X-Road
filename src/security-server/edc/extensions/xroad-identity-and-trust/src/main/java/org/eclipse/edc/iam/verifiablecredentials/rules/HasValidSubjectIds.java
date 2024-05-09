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

import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.CredentialValidationRule;
import org.eclipse.edc.spi.result.Result;

import static org.eclipse.edc.spi.result.Result.failure;
import static org.eclipse.edc.spi.result.Result.success;

/**
 * This class implements the CredentialValidationRule interface and checks if all subject IDs in a
 * VerifiableCredential match an expected subject ID, which in practice is the DID of the holder of a VP.
 */
public class HasValidSubjectIds implements CredentialValidationRule {

    private final String expectedSubjectId;

    public HasValidSubjectIds(String expectedSubjectId) {
        this.expectedSubjectId = expectedSubjectId;
    }


    @Override
    public Result<Void> apply(VerifiableCredential credential) {
        var violatingSubIds = credential.getCredentialSubject().stream()
                .map(CredentialSubject::getId)
                .filter(id -> !expectedSubjectId.equals(id))
                .toList();
        return violatingSubIds.isEmpty() ?
                success() : failure("Not all credential subject IDs match the expected subject ID '%s'. Violating subject IDs: %s".formatted(expectedSubjectId, violatingSubIds));
    }
}
