/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.iam.identitytrust.transform.to;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import jakarta.json.JsonObject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiablePresentation;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.util.Map;

@SuppressWarnings("unchecked")
public class JwtToVerifiablePresentationTransformer extends AbstractJwtTransformer<VerifiablePresentation> {

    private static final String VERIFIABLE_CREDENTIAL_PROPERTY = "verifiableCredential";
    private static final String VP_CLAIM = "vp";

    private final Monitor monitor;
    private final ObjectMapper objectMapper;
    private final JsonLd jsonLd;

    public JwtToVerifiablePresentationTransformer(Monitor monitor, ObjectMapper objectMapper, JsonLd jsonLd) {
        super(VerifiablePresentation.class);
        this.monitor = monitor;
        this.objectMapper = objectMapper;
        this.jsonLd = jsonLd;
    }

    @Override
    public @Nullable VerifiablePresentation transform(@NotNull String jsonWebToken, @NotNull TransformerContext context) {
        try {
            var builder = VerifiablePresentation.Builder.newInstance();
            var signedJwt = SignedJWT.parse(jsonWebToken);
            var claimsSet = signedJwt.getJWTClaimsSet();

            var vpObject = claimsSet.getClaim(VP_CLAIM);
            builder.holder(claimsSet.getIssuer());
            builder.id(claimsSet.getJWTID());

            if (vpObject instanceof String) {
                vpObject = objectMapper.readValue(vpObject.toString(), Map.class);
            }

            if (vpObject instanceof Map vp) {
                // types
                listOrReturn(vp.get(TYPE_PROPERTY), Object::toString).forEach(builder::type);

                // verifiable credentials
                listOrReturn(vp.get(VERIFIABLE_CREDENTIAL_PROPERTY), o -> extractCredentials(o, context)).forEach(builder::credential);

                return builder.build();
            }
        } catch (ParseException | JsonProcessingException e) {
            monitor.warning("Error parsing JWT", e);
            context.reportProblem("Error parsing JWT: %s".formatted(e.getMessage()));
        }
        context.reportProblem("Could not parse VerifiablePresentation from JWT.");
        return null;
    }

    @Nullable
    private VerifiableCredential extractCredentials(Object credential, TransformerContext context) {
        if (credential instanceof String) { // VC is JWT
            return context.transform(credential.toString(), VerifiableCredential.class);
        }
        // VC is LDP
        var input = objectMapper.convertValue(credential, JsonObject.class);
        var expansion = jsonLd.expand(input);
        if (expansion.succeeded()) {
            return context.transform(expansion.getContent(), VerifiableCredential.class);
        }
        context.reportProblem("Error expanding embedded VC: %s".formatted(expansion.getFailureDetail()));
        return null;

    }
}
