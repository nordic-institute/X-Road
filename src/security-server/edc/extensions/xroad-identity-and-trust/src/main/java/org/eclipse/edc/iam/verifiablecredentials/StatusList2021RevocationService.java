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

package org.eclipse.edc.iam.verifiablecredentials;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.iam.verifiablecredentials.spi.RevocationListService;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.statuslist.BitString;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.statuslist.StatusList2021Credential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.statuslist.StatusListStatus;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.util.collection.Cache;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Service to check if a particular {@link VerifiableCredential} is "valid", where "validity" is defined as not revoked and not suspended.
 * Credentials, that don't have a {@code credentialStatus} object are deemed "valid" as well.
 * <p>
 * To achieve that, the {@link VerifiableCredential#getCredentialStatus()} object is inspected and checked against the status list credential referenced therein.
 * <p>
 * To limit traffic on the actual StatusList2021 credential, it is cached in a thread-safe {@link Map}, and only re-downloaded if the cache is expired.
 */
public class StatusList2021RevocationService implements RevocationListService {
    private final ObjectMapper objectMapper;
    private final Cache<String, VerifiableCredential> cache;

    public StatusList2021RevocationService(ObjectMapper objectMapper, long cacheValidity) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY) // technically, credential subjects and credential status can be objects AND Arrays
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); // let's make sure this is disabled, because the "@context" would cause problems
        cache = new Cache<>(this::updateCredential, cacheValidity);
    }

    @Override
    public Result<Void> checkValidity(VerifiableCredential credential) {
        return credential.getCredentialStatus().stream().map(StatusListStatus::parse)
                .map(this::checkStatus)
                .reduce(Result::merge)
                .orElse(Result.failure("Could not check the validity of the credential with ID '%s'".formatted(credential.getId())));
    }

    private Result<Void> checkStatus(StatusListStatus status) {
        var slCredUrl = status.getStatusListCredential();
        var credential = cache.get(slCredUrl);
        var slCred = StatusList2021Credential.parse(credential);

        // check that the "statusPurpose" values match
        var purpose = status.getStatusListPurpose();
        var slCredPurpose = slCred.statusPurpose();
        if (!purpose.equalsIgnoreCase(slCredPurpose)) {
            return Result.failure("Credential's statusPurpose value must match the status list's purpose: '%s' != '%s'".formatted(purpose, slCredPurpose));
        }

        var bitStringResult = BitString.Parser.newInstance().parse(slCred.encodedList());

        if (bitStringResult.failed()) {
            return bitStringResult.mapTo();
        }
        var bitString = bitStringResult.getContent();

        var index = status.getStatusListIndex();
        // check that the value at index in the bitset is "1"
        if (bitString.get(index)) {
            return Result.failure("Credential status is '%s', status at index %d is '1'".formatted(purpose, index));
        }
        return Result.success();
    }

    private VerifiableCredential updateCredential(String credentialUrl) {
        try {
            return objectMapper.readValue(URI.create(credentialUrl).toURL(), VerifiableCredential.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
