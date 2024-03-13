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

package org.eclipse.edc.jwt.rules;

import org.assertj.core.api.Assertions;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.token.rules.AudienceValidationRule;
import org.eclipse.edc.token.spi.TokenValidationRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.nimbusds.jwt.JWTClaimNames.AUDIENCE;
import static java.util.Collections.emptyMap;

class AudienceValidationRuleTest {

    private final String endpointAudience = "test-audience";
    private final TokenValidationRule rule = new AudienceValidationRule(endpointAudience);

    @Test
    void validAudience() {
        var token = ClaimToken.Builder.newInstance()
                .claim(AUDIENCE, List.of(endpointAudience))
                .build();

        var result = rule.checkRule(token, emptyMap());

        Assertions.assertThat(result.succeeded()).isTrue();
    }

    @Test
    void validationKoBecauseAudienceNotRespected() {
        var token = ClaimToken.Builder.newInstance()
                .claim(AUDIENCE, List.of("fake-audience"))
                .build();

        var result = rule.checkRule(token, emptyMap());

        Assertions.assertThat(result.succeeded()).isFalse();
        Assertions.assertThat(result.getFailureMessages())
                .containsExactly("Token audience claim (aud -> [fake-audience]) did not contain expected audience: test-audience");
    }

    @Test
    void validationKoBecauseAudienceNotProvided() {
        var token = ClaimToken.Builder.newInstance()
                .build();

        var result = rule.checkRule(token, emptyMap());

        Assertions.assertThat(result.succeeded()).isFalse();
        Assertions.assertThat(result.getFailureMessages()).hasSize(1)
                .contains("Required audience (aud) claim is missing in token");
    }
}
