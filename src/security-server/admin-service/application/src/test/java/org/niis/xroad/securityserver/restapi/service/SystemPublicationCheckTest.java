/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.securityserver.restapi.service.SystemPublicationCheck.Action;
import org.niis.xroad.serverconf.model.PublicationState;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPublicationCheckTest {

    private static final String DID = "did:web:ss.example.test%3A7183:xrd-ss0";
    private static final String DSP_BASE_URL = "https://ss.example.test:7181/dsp";
    private static final ServerSystemValues EQUAL = new ServerSystemValues(DID, DSP_BASE_URL);
    private static final ServerSystemValues OTHER_DID =
            new ServerSystemValues("did:web:other.example.test%3A7183:xrd-ss0", DSP_BASE_URL);
    private static final ServerSystemValues OTHER_URL =
            new ServerSystemValues(DID, "https://other.example.test:7181/dsp");
    private static final ServerSystemValues MISSING = null;
    private static final BooleanSupplier REGISTERED = () -> true;
    private static final BooleanSupplier NOT_REGISTERED = () -> false;

    private static Action decide(PublicationState state, ServerSystemValues carried, BooleanSupplier authCert) {
        return SystemPublicationCheck.decide(state, DID, () -> DSP_BASE_URL, carried, authCert);
    }

    @Test
    void aSubmittedValueThatGlobalconfCarriesUnchangedIsPublished() {
        assertThat(decide(PublicationState.SUBMITTED, EQUAL, REGISTERED)).isEqualTo(Action.PUBLISH);
    }

    @Test
    void aSubmittedValueIsNotPublishedWhileTheDspBaseUrlCannotBeBuilt() {
        var action = SystemPublicationCheck.decide(PublicationState.SUBMITTED, DID, () -> null, EQUAL, REGISTERED);

        assertThat(action).isEqualTo(Action.NONE);
    }

    @Test
    void aPublishedValueMissingFromGlobalconfIsSentAgain() {
        assertThat(decide(PublicationState.PUBLISHED, MISSING, REGISTERED)).isEqualTo(Action.RESEND);
    }

    @Test
    void aPublishedValueIsNeverResetWhileTheAuthCertIsNotRegistered() {
        assertThat(decide(PublicationState.PUBLISHED, MISSING, NOT_REGISTERED)).isEqualTo(Action.NONE);
    }

    @Test
    void aPublishedValueThatGlobalconfCarriesUnchangedIsLeftAlone() {
        assertThat(decide(PublicationState.PUBLISHED, EQUAL, REGISTERED)).isEqualTo(Action.NONE);
    }

    @Test
    void aDifferentValueIsNeverTreatedAsMissing() {
        assertThat(decide(PublicationState.PUBLISHED, OTHER_DID, REGISTERED)).isEqualTo(Action.NONE);
        assertThat(decide(PublicationState.PUBLISHED, OTHER_URL, REGISTERED)).isEqualTo(Action.NONE);
    }

    @Test
    void aDifferentValueNeverPublishesASubmittedValue() {
        assertThat(decide(PublicationState.SUBMITTED, OTHER_DID, REGISTERED)).isEqualTo(Action.NONE);
        assertThat(decide(PublicationState.SUBMITTED, OTHER_URL, REGISTERED)).isEqualTo(Action.NONE);
    }

    @Test
    void aSubmittedValueThatGlobalconfDoesNotCarryIsLeftAlone() {
        assertThat(decide(PublicationState.SUBMITTED, MISSING, REGISTERED)).isEqualTo(Action.NONE);
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"NOT_SENT", "REJECTED"})
    void aNotSentOrRejectedValueIsNeverTouched(PublicationState state) {
        for (var carried : Arrays.asList(EQUAL, OTHER_DID, MISSING)) {
            assertThat(decide(state, carried, REGISTERED)).isEqualTo(Action.NONE);
        }
    }

    @Test
    void aValueWithNoStoredStateIsNotSentAndNeverTouched() {
        assertThat(decide(null, EQUAL, REGISTERED)).isEqualTo(Action.NONE);
        assertThat(decide(null, MISSING, REGISTERED)).isEqualTo(Action.NONE);
    }

    @Test
    void theAuthCertIsAskedOnlyWhenTheAnswerCanChangeTheDecision() {
        BooleanSupplier mustNotBeAsked = () -> {
            throw new AssertionError("the auth cert must not be asked");
        };

        decide(PublicationState.PUBLISHED, EQUAL, mustNotBeAsked);
        decide(PublicationState.SUBMITTED, MISSING, mustNotBeAsked);
        decide(PublicationState.NOT_SENT, MISSING, mustNotBeAsked);
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"NOT_SENT", "PUBLISHED", "REJECTED"})
    void theDspBaseUrlIsBuiltOnlyForASubmittedValueThatGlobalconfCarries(PublicationState state) {
        Supplier<String> mustNotBeAsked = () -> {
            throw new AssertionError("the DSP base URL must not be built");
        };

        for (var carried : Arrays.asList(EQUAL, OTHER_DID, MISSING)) {
            SystemPublicationCheck.decide(state, DID, mustNotBeAsked, carried, REGISTERED);
        }
        SystemPublicationCheck.decide(PublicationState.SUBMITTED, DID, mustNotBeAsked, MISSING, REGISTERED);
    }
}
