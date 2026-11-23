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
import org.niis.xroad.serverconf.impl.entity.PendingAddressChangeEntity;

import java.time.Duration;
import java.time.Instant;

/**
 * Decides what one globalconf check does with the pending address change. Pure: no clock, no storage.
 *
 * <p>An address that globalconf shows as the requested one wins over the expiry: the request is dropped and the
 * participants are re-identified. Any other registered address, and an address that is not registered or not
 * readable, leaves the request in place until it expires.
 */
@UtilityClass
public class PendingAddressChangeCheck {

    /**
     * @param pending           the pending address change
     * @param registeredAddress the address globalconf shows for this Security Server, {@code null} when it is not
     *                          registered or globalconf is not readable
     * @param now               the current time
     * @param expiry            how long a request may wait for globalconf to show its address
     * @return the action to apply to the pending address change
     */
    public static PendingAddressChangeAction decide(PendingAddressChangeEntity pending,
            @Nullable String registeredAddress, Instant now, Duration expiry) {
        if (pending.getRequestedAddress().equals(registeredAddress)) {
            return PendingAddressChangeAction.RE_IDENTIFY;
        }
        return isExpired(pending, now, expiry) ? PendingAddressChangeAction.EXPIRE : PendingAddressChangeAction.KEEP;
    }

    /**
     * @param pending the pending address change
     * @param now    the current time
     * @param expiry how long a request may wait for globalconf to show its address
     * @return whether the request has waited for the whole expiry
     */
    private static boolean isExpired(PendingAddressChangeEntity pending, Instant now, Duration expiry) {
        return Duration.between(pending.getRequestedAt(), now).compareTo(expiry) >= 0;
    }
}
