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

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.niis.xroad.serverconf.model.PublicationState;

/**
 * Decides how one globalconf check moves the publication state of a member DID. Pure: no clock, no storage.
 *
 * <p>A DID that globalconf carries for the member but that differs from the stored one changes nothing: only an
 * absence counts as missing, and only while the member is hosted on this server.
 */
@UtilityClass
public class MemberPublicationCheck {

    /**
     * @param storedState the stored publication state of the member row; {@code null} counts as not sent
     * @param comparison  how the DID globalconf carries for the member on this server compares with the DID of the
     *                    member row
     * @param hostedHere  whether the member is still hosted on this server
     * @return the transition to apply to the row
     */
    public static PublicationCheckAction decide(@Nullable PublicationState storedState,
            GlobalConfComparison comparison, boolean hostedHere) {
        var state = storedState == null ? PublicationState.NOT_SENT : storedState;
        return switch (comparison) {
            case EQUAL -> state == PublicationState.SUBMITTED ? PublicationCheckAction.PUBLISH
                    : PublicationCheckAction.NONE;
            case ABSENT -> state == PublicationState.PUBLISHED && hostedHere ? PublicationCheckAction.RESEND
                    : PublicationCheckAction.NONE;
            case DIFFERENT, STALE -> PublicationCheckAction.NONE;
        };
    }
}
