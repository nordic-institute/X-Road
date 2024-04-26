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

import com.nimbusds.jwt.SignedJWT;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class JwtToVerifiableCredentialTransformer extends AbstractJwtTransformer<VerifiableCredential> {

    private static final String ID_PROPERTY = "id";
    private static final String VC_CLAIM = "vc";
    private static final String CREDENTIAL_SUBJECT_PROPERTY = "credentialSubject";
    private static final String CREDENTIAL_STATUS_PROPERTY = "credentialStatus";
    private static final String EXPIRATION_DATE_PROPERTY = "expirationDate";
    private static final String ISSUANCE_DATE_PROPERTY = "issuanceDate";

    private final Monitor monitor;


    public JwtToVerifiableCredentialTransformer(Monitor monitor) {
        super(VerifiableCredential.class);
        this.monitor = monitor;
    }

    @Override
    public @Nullable VerifiableCredential transform(@NotNull String serializedJwt, @NotNull TransformerContext context) {
        try {
            var jwt = SignedJWT.parse(serializedJwt);
            var claims = jwt.getJWTClaimsSet();

            var vcObject = claims.getClaim(VC_CLAIM);

            if (vcObject instanceof Map vc) {
                var builder = VerifiableCredential.Builder.newInstance();

                // types
                listOrReturn(vc.get(TYPE_PROPERTY), Object::toString).forEach(builder::type);

                // credential subjects
                listOrReturn(vc.get(CREDENTIAL_SUBJECT_PROPERTY), o -> extractSubject((Map<String, ?>) o, claims.getSubject())).forEach(builder::credentialSubject);

                // credential status
                listOrReturn(vc.get(CREDENTIAL_STATUS_PROPERTY), o -> extractStatus((Map<String, Object>) o)).forEach(builder::credentialStatus);

                // expiration date
                extractDate(vc.get(EXPIRATION_DATE_PROPERTY), claims.getExpirationTime()).ifPresent(builder::expirationDate);

                // issuance date
                extractDate(vc.get(ISSUANCE_DATE_PROPERTY), claims.getExpirationTime()).ifPresent(builder::issuanceDate);

                builder.issuer(new Issuer(claims.getIssuer(), Map.of()));
                builder.name(claims.getSubject()); // todo: is this correct?
                return builder.build();
            }
        } catch (ParseException e) {
            monitor.warning("Error parsing JWT", e);
            context.reportProblem("Error parsing JWT: %s".formatted(e.getMessage()));
        }
        return null;
    }

    private Optional<Instant> extractDate(@Nullable Object dateObject, Date fallback) {
        return ofNullable(dateObject)
                .map(Object::toString)
                .map(Instant::parse)
                .or(() -> ofNullable(fallback).map(Date::toInstant));
    }

    private CredentialStatus extractStatus(Map<String, Object> status) {
        if (status == null || status.isEmpty()) {
            return null;
        }
        var id = status.remove(ID_PROPERTY).toString();
        var type = status.remove(TYPE_PROPERTY).toString();

        return new CredentialStatus(id, type, status);
    }

    private CredentialSubject extractSubject(Map<String, ?> subject, String fallback) {
        var builder = CredentialSubject.Builder.newInstance();
        var id = Objects.requireNonNullElse(subject.remove(ID_PROPERTY), fallback);
        builder.id(id.toString());
        subject.forEach(builder::claim);
        return builder.build();
    }
}
