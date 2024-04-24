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

import org.eclipse.edc.iam.verifiablecredentials.spi.RevocationListService;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.CredentialValidationRule;
import org.eclipse.edc.spi.result.Result;

import static org.eclipse.edc.spi.result.Result.success;

/**
 * This class represents a rule that checks if a given VerifiableCredential is revoked based on a BitStringStatusList/StatusList2021 credential.
 * A credential is regarded as "not revoked" if:
 * <ul>
 *     <li>{@code Credential.credentialStatus.statusPurpose == StatusListCredential.credentialSubject.statusPurpose}</li>
 *     <li> value of the bit-string obtained from {@code StatusListCredential.encodedList} at the index obtained from {@code Credential.credentialStatus.statusIndex} is 1</li>
 * </ul>
 * <p>
 * All other situations, such as missing properties, invalid values etc. will be interpreted as "revoked".
 * Credentials that don't have a {@code credentialStatus} property are regarded as "not revoked".
 */
public class IsNotRevoked implements CredentialValidationRule {

    private final RevocationListService revocationListService;

    public IsNotRevoked(RevocationListService revocationListService) {
        this.revocationListService = revocationListService;
    }

    @Override
    public Result<Void> apply(VerifiableCredential credential) {
        if (credential.getCredentialStatus().isEmpty()) {
            return success();
        }

        return revocationListService.checkValidity(credential);
    }
}
