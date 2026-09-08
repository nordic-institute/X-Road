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

import ee.ria.xroad.common.identifier.ClientId;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.CredentialStatus;

/**
 * Re-anchor decision unit for the SYSTEM participant's membership credential. Pure and hub/database-free:
 * every input is a plain value, so the owner-vs-credential-subject decision is testable in isolation.
 *
 * <p>The SYSTEM identifier is owner-free by design (XRDADR-41), but its credential is issued to the
 * current owner. Since the identity hub exposes no surface to read a credential's subject directly, the
 * subject is tracked implicitly: {@link #holderPidBase} salts the holder-pid slot namespace with the
 * owner, so a slot found active there was necessarily requested for that owner. An owner change derives
 * a different, still-empty namespace — the scan lands on nothing, {@link #decide} calls for a fresh
 * issuance there, and the credential parked under the previous owner's namespace is left untouched to
 * expire on its own (no revocation).
 */
@UtilityClass
class SystemCredentialAnchor {

    enum Decision { SKIP, ISSUE }

    /**
     * Salts an unsalted holder-pid base with the owner, anchoring the SYSTEM participant's credential
     * request namespace to the current owner without touching the owner-free SYSTEM identifier itself.
     *
     * @param unsaltedBase the base used by every other participant kind ({@code participantId-<constant>})
     * @param owner         the current owner member — the credential subject
     */
    static String holderPidBase(String unsaltedBase, ClientId owner) {
        return unsaltedBase + "-" + ParticipantIdentifierScheme.memberCtxId(owner);
    }

    /**
     * Re-anchor decision for the credential status found at the current owner's holder-pid base.
     *
     * @param statusAtCurrentOwnerBase the status of a request already found there ({@code null} when none
     *                                 was ever submitted)
     * @return {@code SKIP} while a request there is not terminally failed — issued, in flight for the
     *         current owner (matching subject), or in an unrecognized hub state that may still be in
     *         flight; {@code ISSUE} when nothing usable is there — no request was ever submitted (owner
     *         just changed, or first provisioning) or the slot is in terminal ERROR
     */
    static Decision decide(@Nullable CredentialStatus statusAtCurrentOwnerBase) {
        return statusAtCurrentOwnerBase == null || statusAtCurrentOwnerBase == CredentialStatus.ERROR
                ? Decision.ISSUE
                : Decision.SKIP;
    }
}
