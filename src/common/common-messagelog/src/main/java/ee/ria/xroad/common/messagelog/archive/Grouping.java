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
package ee.ria.xroad.common.messagelog.archive;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.MessageRecord;

import lombok.Getter;

import java.util.Objects;

public interface Grouping {
    default ClientId getClientId() {
        return null;
    }

    boolean includes(MessageRecord record);

    default String name() {
        final ClientId id = getClientId();
        return id == null ? null : id.toShortString();
    }
}

class MemberGrouping implements Grouping {
    @Getter
    private final ClientId clientId;

    MemberGrouping(ClientId clientId) {
        this.clientId = clientId.getMemberId();
    }

    /**
     * checks if the record belongs to this record group
     */
    @Override
    public boolean includes(MessageRecord record) {
        return Objects.equals(record.getMemberClass(), clientId.getMemberClass())
                && Objects.equals(record.getMemberCode(), clientId.getMemberCode());
    }

    @Override
    public String toString() {
        return name();
    }
}

class SubsystemGrouping implements Grouping {
    @Getter
    private final ClientId clientId;

    SubsystemGrouping(ClientId clientId) {
        this.clientId = clientId;
    }

    /**
     * checks if the record belongs to this record group
     */
    @Override
    public boolean includes(MessageRecord record) {
        return Objects.equals(record.getMemberClass(), clientId.getMemberClass())
                && Objects.equals(record.getMemberCode(), clientId.getMemberCode())
                && Objects.equals(record.getSubsystemCode(), clientId.getSubsystemCode());
    }

    @Override
    public String toString() {
        return name();
    }
}
