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
package org.niis.xroad.cs.admin.api.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IssuerServiceStatusTest {

    private static final String DID = "did:web:issuer.example.org%3A6183:issuer";

    private static final String OTHER_DID = "did:web:other.example.org%3A6183:issuer";

    @Test
    void notConfiguredWhenHostIsNotSet() {
        var status = IssuerServiceStatus.derive(false, false, false, null, null, CanSignResult.absent());

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.NOT_CONFIGURED);
    }

    @Test
    void waitingForCertificateWhenHostIsSetButCertificateIsNotReady() {
        var status = IssuerServiceStatus.derive(true, false, false, DID, null, CanSignResult.absent());

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.WAITING_FOR_CERTIFICATE);
    }

    @Test
    void readyToSetUpWhenPrerequisitesPassAndNothingIsBoundOrRunning() {
        var status = IssuerServiceStatus.derive(true, true, false, DID, null, CanSignResult.absent());

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.READY_TO_SET_UP);
    }

    @Test
    void readyToSetUpWhenUnboundAndTheIssuerCanSignForTheDidToBind() {
        var status = IssuerServiceStatus.derive(true, true, false, DID, null, CanSignResult.canSign(DID));

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.READY_TO_SET_UP);
        assertThat(status.canSign()).isTrue();
        assertThat(status.canSignCause()).isNull();
    }

    @Test
    void readyWhenBoundEvenThoughARunHoldsTheLease() {
        var status = IssuerServiceStatus.derive(true, true, true, DID, DID, CanSignResult.canSign(DID));

        assertThat(status.runInProgress()).isTrue();
        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.READY);
    }

    @Test
    void settingUpWhenARunHoldsTheLease() {
        var status = IssuerServiceStatus.derive(true, true, true, DID, null, CanSignResult.absent());

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.SETTING_UP);
    }

    @Test
    void readyWhenBoundAndTheIssuerCanSign() {
        var status = IssuerServiceStatus.derive(true, true, false, DID, DID, CanSignResult.canSign(DID));

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.READY);
        assertThat(status.canSign()).isTrue();
        assertThat(status.canSignCause()).isNull();
    }

    @Test
    void failedWithTheCanSignCauseWhenBoundAndTheIssuerCannotSign() {
        var status = IssuerServiceStatus.derive(true, true, false, DID, DID,
                CanSignResult.cannotSign(CanSignCause.DID_NOT_PUBLISHED));

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.FAILED);
        assertThat(status.canSign()).isFalse();
        assertThat(status.canSignCause()).isEqualTo(CanSignCause.DID_NOT_PUBLISHED);
    }

    @Test
    void failedWithNoActiveKeyWhenBoundButTheParticipantContextIsAbsent() {
        var status = IssuerServiceStatus.derive(true, true, false, DID, DID, CanSignResult.absent());

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.FAILED);
        assertThat(status.canSign()).isFalse();
        assertThat(status.canSignCause()).isEqualTo(CanSignCause.NO_ACTIVE_KEY);
    }

    @Test
    void readyWhenBoundEvenThoughCertificateIsNotReady() {
        var status = IssuerServiceStatus.derive(true, false, false, DID, DID, CanSignResult.canSign(DID));

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.READY);
        assertThat(status.canSign()).isTrue();
        assertThat(status.canSignCause()).isNull();
    }

    @Test
    void failedWhenBoundAndCertificateIsNotReadyButTheIssuerCannotSign() {
        var status = IssuerServiceStatus.derive(true, false, false, DID, DID,
                CanSignResult.cannotSign(CanSignCause.DID_NOT_PUBLISHED));

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.FAILED);
        assertThat(status.canSign()).isFalse();
        assertThat(status.canSignCause()).isEqualTo(CanSignCause.DID_NOT_PUBLISHED);
    }

    @Test
    void failedWithDidMismatchWhenBoundButTheIssuerCanSignForAnotherDid() {
        var status = IssuerServiceStatus.derive(true, true, false, DID, DID, CanSignResult.canSign(OTHER_DID));

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.FAILED);
        assertThat(status.canSign()).isFalse();
        assertThat(status.canSignCause()).isEqualTo(CanSignCause.DID_MISMATCH);
    }

    @Test
    void failedWithDidMismatchWhenUnboundButTheIssuerCanSignForAnotherDid() {
        var status = IssuerServiceStatus.derive(true, true, false, DID, null, CanSignResult.canSign(OTHER_DID));

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.FAILED);
        assertThat(status.canSign()).isFalse();
        assertThat(status.canSignCause()).isEqualTo(CanSignCause.DID_MISMATCH);
    }

    @Test
    void failedWhenUnboundButALeftoverParticipantContextCannotSign() {
        var status = IssuerServiceStatus.derive(true, true, false, DID, null,
                CanSignResult.cannotSign(CanSignCause.DID_NOT_PUBLISHED));

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.FAILED);
        assertThat(status.canSign()).isFalse();
        assertThat(status.canSignCause()).isEqualTo(CanSignCause.DID_NOT_PUBLISHED);
    }
}
