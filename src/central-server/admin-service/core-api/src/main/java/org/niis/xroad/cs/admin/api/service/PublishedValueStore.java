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
package org.niis.xroad.cs.admin.api.service;

import ee.ria.xroad.common.identifier.ClientId;

import org.niis.xroad.cs.admin.api.domain.PublishedValueResult;
import org.niis.xroad.cs.admin.api.domain.SystemPairCheck;

import java.util.Map;

/**
 * Stores the values a Security Server publishes: its SYSTEM DID with DSP base URL, and the DIDs of its
 * members. Every operation runs in a transaction, locks the Security Server row first and validates the
 * values against the server as registered at that moment.
 *
 * <p>Write-once operations serve the Security Server's own requests: a value is written once per registered
 * address, an equal resend changes nothing, a different value is refused, and a value written for an
 * earlier address is replaced. Admin replace operations serve the Central Server edit: they replace
 * existing values and never add a value to a server or member that has none.
 */
public interface PublishedValueStore {

    /**
     * Writes the SYSTEM DID and DSP base URL of a server as one pair, under the write-once rule.
     *
     * @param securityServerId the id of the server row
     * @param systemDid        the SYSTEM DID
     * @param dspBaseUrl       the DSP base URL
     * @return the outcome for the pair
     */
    PublishedValueResult writeSystemPairOnce(int securityServerId, String systemDid, String dspBaseUrl);

    /**
     * Checks a SYSTEM pair against a server without writing anything and without locking the server row.
     *
     * @param securityServerId the id of the server row
     * @param systemDid        the SYSTEM DID
     * @param dspBaseUrl       the DSP base URL
     * @return the validator's rejection, or whether the pair equals the stored pair
     */
    SystemPairCheck checkSystemPair(int securityServerId, String systemDid, String dspBaseUrl);

    /**
     * Replaces the stored SYSTEM pair of a server. A server without a stored pair is left without one.
     *
     * @param securityServerId the id of the server row
     * @param systemDid        the new SYSTEM DID
     * @param dspBaseUrl       the new DSP base URL
     * @return the outcome for the pair
     */
    PublishedValueResult replaceSystemPair(int securityServerId, String systemDid, String dspBaseUrl);

    /**
     * Writes the member DIDs of a server under the write-once rule, each entry on its own: a refused entry
     * does not stop the others.
     *
     * @param securityServerId the id of the server row
     * @param memberDids       the DID to write for each member
     * @return the outcome for each member, in the order of the given entries
     */
    Map<ClientId, PublishedValueResult> writeMemberDidsOnce(int securityServerId, Map<ClientId, String> memberDids);

    /**
     * Replaces the stored member DIDs of a server, each entry on its own. A member without a stored DID on
     * the server is left without one.
     *
     * @param securityServerId the id of the server row
     * @param memberDids       the new DID for each member
     * @return the outcome for each member, in the order of the given entries
     */
    Map<ClientId, PublishedValueResult> replaceMemberDids(int securityServerId, Map<ClientId, String> memberDids);
}
