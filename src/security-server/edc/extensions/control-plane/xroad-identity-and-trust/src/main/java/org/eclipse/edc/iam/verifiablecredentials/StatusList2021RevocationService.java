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
import org.eclipse.edc.spi.result.AbstractResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.util.collection.Cache;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

import static org.eclipse.edc.spi.result.Result.success;

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
        cache = new Cache<>(this::downloadStatusListCredential, cacheValidity);
    }

    @Override
    public Result<Void> checkValidity(VerifiableCredential credential) {
        return credential.getCredentialStatus().stream().map(StatusListStatus::parse)
                .map(this::checkStatus)
                .reduce(Result::merge)
                .orElse(Result.failure("Could not check the validity of the credential with ID '%s'".formatted(credential.getId())));
    }

    @Override
    public Result<String> getStatusPurpose(VerifiableCredential credential) {
        if (credential.getCredentialStatus().isEmpty()) {
            return success(null);
        }
        var res = credential.getCredentialStatus().stream()
                .map(StatusListStatus::parse)
                .map(this::getStatusInternal)
                .collect(Collectors.groupingBy(AbstractResult::succeeded)); //partition by succeeded/failed

        if (res.containsKey(false)) {
            return Result.failure(res.get(false).stream().map(AbstractResult::getFailureDetail).toList());
        }

        var list = res.get(true).stream()
                .filter(r -> r.getContent() != null)
                .map(AbstractResult::getContent).toList();

        // get(0) is OK, because there should only be 1 credentialStatus
        return list.isEmpty() ? success(null) : success(list.get(0));

    }

    private Result<Void> checkStatus(StatusListStatus status) {
        var index = status.getStatusListIndex();
        return getStatusInternal(status)
                .compose(purpose -> purpose != null ?
                        Result.failure("Credential status is '%s', status at index %d is '1'".formatted(purpose, index)) :
                        success());
    }

    /**
     * Obtains the status purpose for a particular credentialStatus entry if it is set, otherwise returns a successful result with a {@code null} content.
     * So, a successful result with a non-null content indicates, that the respective credentialStatus is set.
     */
    private Result<String> getStatusInternal(StatusListStatus status) {
        var index = status.getStatusListIndex();
        var slCredUrl = status.getStatusListCredential();
        var credential = cache.get(slCredUrl);
        var slCred = StatusList2021Credential.parse(credential);

        // check that the "statusPurpose" values match
        var purpose = status.getStatusListPurpose();
        var slCredPurpose = slCred.statusPurpose();
        if (!purpose.equalsIgnoreCase(slCredPurpose)) {
            return Result.failure("Credential's statusPurpose value must match the status list's purpose: '%s' != '%s'"
                    .formatted(purpose, slCredPurpose));
        }

        var bitStringResult = BitString.Parser.newInstance().parse(slCred.encodedList());

        if (bitStringResult.failed()) {
            return bitStringResult.mapTo();
        }
        var bitString = bitStringResult.getContent();

        // check that the value at index in the bitset is "1"
        if (bitString.get(index)) {
            return success(purpose);
        }
        return success(null);
    }

    private VerifiableCredential downloadStatusListCredential(String credentialUrl) {
        try {
            return objectMapper.readValue(URI.create(credentialUrl).toURL(), VerifiableCredential.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
