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

import java.util.Objects;

/**
 * The Issuer Service's status, derived on every read from where each fact really lives: nothing here is
 * stored. {@link #derive} is the pure mapping from the six source facts to these rows and the overall state;
 * callers gather the facts (database reads, the lease row, and a "can sign" probe to the local Issuer
 * Service) and hand them to it.
 *
 * @param hostSet          whether the issuer host is set
 * @param certificateReady whether the connector TLS certificate is present, unexpired and valid for the host
 * @param runInProgress    whether a set-up run currently holds the lease
 * @param didToBind        the issuer DID that set-up would bind, built from the host and DID port, or
 *                          {@code null} when the host is not set
 * @param didBound         whether the issuer identity is already bound
 * @param canSign          whether the asking node's Issuer Service can currently sign for the bound (or
 *                          to-be-bound) DID
 * @param canSignCause     why it cannot, or {@code null} when {@code canSign} is {@code true}
 * @param overall          the overall state derived from the rows above
 */
public record IssuerServiceStatus(
        boolean hostSet,
        boolean certificateReady,
        boolean runInProgress,
        String didToBind,
        boolean didBound,
        boolean canSign,
        CanSignCause canSignCause,
        Overall overall) {

    /** The issuer's overall set-up and signing state, derived from {@link IssuerServiceStatus}'s rows. */
    public enum Overall {
        NOT_CONFIGURED,
        WAITING_FOR_CERTIFICATE,
        READY_TO_INITIALIZE,
        INITIALIZING,
        READY,
        FAILED
    }

    /**
     * Derives the status from its six source facts. An absent participant context counts as "no active key":
     * once the issuer DID is bound, that is a real failure (the key went missing); before binding, it simply
     * does not yet affect the overall state, which is governed by {@code runInProgress} and whether a DID is
     * bound.
     *
     * @param boundDid the bound issuer DID, or {@code null} when none is bound yet
     */
    public static IssuerServiceStatus derive(boolean hostSet, boolean certificateReady, boolean runInProgress,
            String didToBind, String boundDid, CanSignResult canSignResult) {
        boolean didBound = boundDid != null;
        String expectedDid = didBound ? boundDid : didToBind;
        boolean matches = canSignResult.outcome() == CanSignOutcome.CAN_SIGN
                && Objects.equals(canSignResult.did(), expectedDid);

        boolean canSign = matches;
        CanSignCause canSignCause = matches ? null
                : switch (canSignResult.outcome()) {
                    case CAN_SIGN -> CanSignCause.DID_MISMATCH;
                    case ABSENT -> CanSignCause.NO_ACTIVE_KEY;
                    case CANNOT_SIGN -> canSignResult.cause();
                };

        Overall overall;
        if (didBound) {
            overall = matches ? Overall.READY : Overall.FAILED;
        } else if (!hostSet) {
            overall = Overall.NOT_CONFIGURED;
        } else if (!certificateReady) {
            overall = Overall.WAITING_FOR_CERTIFICATE;
        } else if (runInProgress) {
            overall = Overall.INITIALIZING;
        } else if (canSignResult.outcome() == CanSignOutcome.ABSENT || matches) {
            overall = Overall.READY_TO_INITIALIZE;
        } else {
            overall = Overall.FAILED;
        }

        return new IssuerServiceStatus(hostSet, certificateReady, runInProgress, didToBind, didBound, canSign, canSignCause, overall);
    }
}
