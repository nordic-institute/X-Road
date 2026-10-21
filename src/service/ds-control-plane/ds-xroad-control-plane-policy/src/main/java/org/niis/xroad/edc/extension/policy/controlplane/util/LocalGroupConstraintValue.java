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

package org.niis.xroad.edc.extension.policy.controlplane.util;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * The DSP {@code xroad:localGroupMember} constraint value: the group owner's client id alongside
 * the group code, so evaluation never has to look the consumer up as a client of the provider
 * Security Server. Shared by {@code PolicyMapper}, which writes the value, and
 * {@code XRoadLocalGroupMemberConstraintFunction}, which reads it.
 */
@Slf4j
public record LocalGroupConstraintValue(ClientId owner, String groupCode) {

    private static final char SEPARATOR = ';';

    public String encode() {
        return owner.asEncodedId() + SEPARATOR + groupCode;
    }

    public LocalGroupId localGroupId() {
        return LocalGroupId.Conf.create(groupCode);
    }

    /**
     * An old-format value (bare group code, no separator) or a malformed one (unparseable owner,
     * blank group code) decodes to empty rather than throwing.
     */
    public static Optional<LocalGroupConstraintValue> decode(String value) {
        int sepIndex = value.indexOf(SEPARATOR);
        if (sepIndex < 0) {
            log.debug("Local-group constraint value '{}' has no owner separator", value);
            return Optional.empty();
        }
        var ownerPart = value.substring(0, sepIndex);
        var groupCode = value.substring(sepIndex + 1);
        try {
            var owner = PolicyContextHelper.parseClientId(ownerPart);
            LocalGroupId.Conf.create(groupCode);
            return Optional.of(new LocalGroupConstraintValue(owner, groupCode));
        } catch (RuntimeException e) {
            log.debug("Local-group constraint value '{}' could not be decoded: {}", value, e.getMessage());
            return Optional.empty();
        }
    }
}
