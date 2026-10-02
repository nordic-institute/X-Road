/*
 * The MIT License
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
package org.niis.xroad.opmonitor.api;

import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Length limits of the operational data text fields, matching the op-monitor database columns.
 * Applied by the Security Server before sending records and by op-monitor before persisting them.
 */
@UtilityClass
public class OperationalDataTruncationPolicy {
    private static final int TEXT_MAX_LENGTH = 255;
    private static final int FAULT_STRING_MAX_LENGTH = 2048;

    private static final Map<String, Integer> MAX_LENGTHS = Stream.concat(
            Stream.of(
                    "securityServerInternalIp", "securityServerType",
                    "clientXRoadInstance", "clientMemberClass", "clientMemberCode", "clientSubsystemCode",
                    "serviceXRoadInstance", "serviceMemberClass", "serviceMemberCode", "serviceSubsystemCode",
                    "serviceCode", "serviceVersion", "serviceType",
                    "restMethod", "restPath", "xRoadVersion",
                    "representedPartyClass", "representedPartyCode",
                    "messageId", "messageUserId", "messageIssue", "messageProtocolVersion",
                    "clientSecurityServerAddress", "serviceSecurityServerAddress",
                    "faultCode", "xRequestId")
                    .map(field -> Map.entry(field, TEXT_MAX_LENGTH)),
            Stream.of(Map.entry("faultString", FAULT_STRING_MAX_LENGTH)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    /**
     * @return names of the operational data text fields that have a length limit
     */
    public static Set<String> truncatedFields() {
        return MAX_LENGTHS.keySet();
    }

    /**
     * Truncates a text value to the limit of the given operational data field. Applying it again changes nothing.
     * A field without its own rule gets the common text limit.
     * @param field operational data field name
     * @param value field value, may be null
     * @return the value cut to the field limit, or null for a null value
     */
    public static String truncate(String field, String value) {
        int maxLength = MAX_LENGTHS.getOrDefault(field, TEXT_MAX_LENGTH);
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
