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

package org.niis.xroad.edc.extension.iam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.iam.verifiablecredentials.rules.HasValidIssuer;
import org.eclipse.edc.iam.verifiablecredentials.rules.IsInValidityPeriod;
import org.eclipse.edc.iam.verifiablecredentials.rules.IsNotRevoked;
import org.eclipse.edc.iam.verifiablecredentials.spi.RevocationListService;
import org.eclipse.edc.iam.verifiablecredentials.spi.VerifiableCredentialValidationService;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiablePresentation;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiablePresentationContainer;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.CredentialValidationRule;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.PresentationVerifier;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.GRAPH;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.spi.result.Result.failure;

public class VerifiableCredentialValidationServiceImpl implements VerifiableCredentialValidationService {

    private static final String GAIA_X_NAMESPACE = "https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#";

    private final PresentationVerifier presentationVerifier;
    private final TrustedIssuerRegistry trustedIssuerRegistry;
    private final RevocationListService revocationListService;
    private final Clock clock;

    private final ObjectMapper objectMapper;

    private final Monitor monitor;

    public VerifiableCredentialValidationServiceImpl(PresentationVerifier presentationVerifier, TrustedIssuerRegistry trustedIssuerRegistry, RevocationListService revocationListService, Clock clock,
                                                     ObjectMapper objectMapper, Monitor monitor) {
        this.presentationVerifier = presentationVerifier;
        this.trustedIssuerRegistry = trustedIssuerRegistry;
        this.revocationListService = revocationListService;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.monitor = monitor;
    }

    @Override
    public Result<Void> validate(List<VerifiablePresentationContainer> presentations, Collection<? extends CredentialValidationRule> additionalRules) {
        return presentations.stream().map(verifiablePresentation -> {
            var credentials = verifiablePresentation.presentation().getCredentials();
            // verify, that the VP and all VPs are cryptographically OK
            var presentationIssuer = verifiablePresentation.presentation().getHolder();
            var validationResult = presentationVerifier.verifyPresentation(verifiablePresentation)
                    .compose(u -> validateVerifiableCredentials(credentials, presentationIssuer, additionalRules));
            return validationResult.failed() ? validationResult : verifyGaiaXCompliance(verifiablePresentation);
        }).reduce(Result.success(), Result::merge);
    }

    @NotNull
    private Result<Void> validateVerifiableCredentials(List<VerifiableCredential> credentials, String presentationHolder, Collection<? extends CredentialValidationRule> additionalRules) {

        // in addition, verify that all VCs are valid
        var filters = new ArrayList<>(List.of(
                new IsInValidityPeriod(clock),
                // credentialSubject.id in Gaia-X VCs doesn't seem to follow this rule
                //new HasValidSubjectIds(presentationHolder),
                new IsNotRevoked(revocationListService),
                new HasValidIssuer(getTrustedIssuerIds())));

        filters.addAll(additionalRules);

        var results = credentials
                .stream()
                .map(c -> filters.stream().reduce(t -> Result.success(), CredentialValidationRule::and).apply(c))
                .reduce(Result::merge);
        return results.orElseGet(() -> failure("Could not determine the status of the VC validation"));
    }

    private List<String> getTrustedIssuerIds() {
        return trustedIssuerRegistry.getTrustedIssuers().stream().map(Issuer::id).toList();
    }

    private Result<Void> verifyGaiaXCompliance(VerifiablePresentationContainer verifiablePresentation) {
        return findComplianceCredential(verifiablePresentation)
                .map(complianceCredential -> verifyComplianceCredential(complianceCredential, verifiablePresentation))
                .orElse(failure("Gaia-X compliance credential not found!"));
    }

    private Optional<VerifiableCredential> findComplianceCredential(VerifiablePresentationContainer verifiablePresentation) {
        Predicate<VerifiableCredential> containsLegalRegistrationNumberReference = credential ->
                containsCredentialSubjectClaim("gx:legalRegistrationNumber", GAIA_X_NAMESPACE, "type", credential);
        Predicate<VerifiableCredential> containsTermsAndConditionsReference = credential ->
                containsCredentialSubjectClaim("gx:GaiaXTermsAndConditions", GAIA_X_NAMESPACE, "type", credential);
        Predicate<VerifiableCredential> containsLegalParticipantReference = credential ->
                containsCredentialSubjectClaim("gx:LegalParticipant", GAIA_X_NAMESPACE, "type", credential);

        return verifiablePresentation.presentation().getCredentials().stream()
                .filter(containsLegalRegistrationNumberReference.and(containsTermsAndConditionsReference).and(containsLegalParticipantReference))
                .findFirst();
    }

    private boolean containsCredentialSubjectClaim(String expectedClaimValue, String claimNamespace, String claimProperty, VerifiableCredential credential) {
        return credential.getCredentialSubject().stream().anyMatch(sub -> expectedClaimValue.equals(sub.getClaim(claimNamespace, claimProperty)));
    }

    private Result<Void> verifyComplianceCredential(VerifiableCredential complianceCredential, VerifiablePresentationContainer verifiablePresentation) {
        return complianceCredential.getCredentialSubject().stream()
                .map(complianceCredentialSubject -> verifyTargetCredential(complianceCredentialSubject, verifiablePresentation))
                .reduce(Result::merge)
                .orElse(failure("Could not determine the status of Gaia-X compliance credential verification"));
    }

    Result<Void> verifyTargetCredential(CredentialSubject complianceCredentialSubject, VerifiablePresentationContainer verifiablePresentation) {
        List<JsonObject> credentials;
        try {
            var vpJsonObject = objectMapper.readValue(verifiablePresentation.rawVp(), JsonObject.class);
            credentials = vpJsonObject.getJsonArray(VerifiablePresentation.VERIFIABLE_PRESENTATION_VC_PROPERTY).stream()
                    .map(JsonValue::asJsonObject)
                    .toList();
        } catch (JsonProcessingException e) {
            String message = "Unable to read credentials from VP";
            monitor.severe(message, e);
            return failure(message);
        }

        return credentials.stream()
                .filter(credential -> complianceCredentialSubject.getId().equals(getExpandedCredentialId(credential)))
                .findFirst()
                .map(credential -> verifyCredentialIntegrity(credential, complianceCredentialSubject))
                .orElse(failure("'" + complianceCredentialSubject.getId() + "' credential referenced by Gaia-X compliance credential not found!"));
    }

    private String getExpandedCredentialId(JsonObject credential) {
        return credential.getJsonArray(GRAPH).stream()
                .map(entry -> entry.asJsonObject().getString(ID))
                .findFirst()
                .orElse(null);
    }

    private Result<Void> verifyCredentialIntegrity(JsonObject credential, CredentialSubject complianceCredentialSubject) {
        /* TODO: the RFC8785 normalization currently used in Gaia-X compliance credenential's integrity checks
        do not work when priorly the credential is expanded and compacted again which is the case with IATP.
        https://gitlab.com/gaia-x/technical-committee/federation-services/icam/-/issues/81 - Ticket for replacing
        the normalization with a more robust solution (URDNA2015)
        */
        return Result.success();
    }

}
