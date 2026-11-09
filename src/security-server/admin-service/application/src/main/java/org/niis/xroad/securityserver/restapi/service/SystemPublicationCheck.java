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
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.serverconf.model.PublicationState;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Decides how one globalconf check moves the publication state of the SYSTEM values. Pure: no clock, no storage.
 *
 * <p>Values that globalconf carries but that differ from the stored ones change nothing: only an absence counts as
 * missing.
 */
@UtilityClass
public class SystemPublicationCheck {

    /**
     * @param storedState        the stored publication state of the SYSTEM row; {@code null} counts as not sent
     * @param ownDid             the SYSTEM DID of the row
     * @param expectedDspBaseUrl the DSP base URL built for this server, {@code null} while it cannot be built;
     *                           asked only for a submitted value while globalconf carries values
     * @param carried            the SYSTEM values globalconf carries for this server, {@code null} when it carries none
     * @param authCertRegistered whether the authentication certificate is registered; asked only when the answer
     *                           can change the decision
     * @return the transition to apply to the row
     */
    public static PublicationCheckAction decide(@Nullable PublicationState storedState, String ownDid,
            Supplier<String> expectedDspBaseUrl, @Nullable ServerSystemValues carried,
            BooleanSupplier authCertRegistered) {
        var state = storedState == null ? PublicationState.NOT_SENT : storedState;
        if (carried != null) {
            var publish = state == PublicationState.SUBMITTED
                    && ownDid.equals(carried.systemDid())
                    && carried.dspBaseUrl().equals(expectedDspBaseUrl.get());
            return publish ? PublicationCheckAction.PUBLISH : PublicationCheckAction.NONE;
        }
        return state == PublicationState.PUBLISHED && authCertRegistered.getAsBoolean()
                ? PublicationCheckAction.RESEND : PublicationCheckAction.NONE;
    }
}
