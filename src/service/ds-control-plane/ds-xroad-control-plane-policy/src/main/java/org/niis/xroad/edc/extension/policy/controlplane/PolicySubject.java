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

package org.niis.xroad.edc.extension.policy.controlplane;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;

import org.niis.xroad.edc.extension.policy.controlplane.util.LocalGroupConstraintValue;

import java.util.Optional;

/**
 * The subject an X-Road policy was written for, as stored in the subject constraint of the policy.
 */
public sealed interface PolicySubject {

    /**
     * A {@code xroad:clientId} subject: a subsystem, or a member when the client id has no subsystem code.
     */
    record Client(ClientId clientId) implements PolicySubject {

        public boolean isSubsystem() {
            return clientId.getSubsystemCode() != null;
        }
    }

    /**
     * A {@code xroad:localGroupMember} subject. The stored value is kept as written; it decodes to the group
     * owner and code only when it has the current owner-and-code format.
     */
    record LocalGroup(String value) implements PolicySubject {

        public Optional<LocalGroupConstraintValue> decoded() {
            return LocalGroupConstraintValue.decode(value);
        }
    }

    /**
     * A {@code xroad:globalGroupMember} subject. The stored value is kept as written; it decodes to a group id
     * only when it has the {@code instance:groupCode} format.
     */
    record GlobalGroup(String value) implements PolicySubject {

        public Optional<GlobalGroupId> groupId() {
            var parts = value.split(":", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return Optional.empty();
            }
            return Optional.of(GlobalGroupId.Conf.create(parts[0], parts[1]));
        }
    }
}
