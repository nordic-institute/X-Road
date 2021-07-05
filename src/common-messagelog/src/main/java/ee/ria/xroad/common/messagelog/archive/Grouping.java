/**
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

import ee.ria.xroad.common.messagelog.MessageRecord;

import java.util.Objects;

interface Grouping {
    boolean includes(MessageRecord record);

    String name();
}

final class MemberGrouping implements Grouping {
    private final String memberClass;
    private final String memberCode;

    MemberGrouping(MessageRecord record) {
        this.memberClass = record.getMemberClass();
        this.memberCode = record.getMemberCode();
    }

    /**
     * checks if the record belongs to this record group
     */
    @Override
    public boolean includes(MessageRecord record) {
        return Objects.equals(memberClass, record.getMemberClass())
                && Objects.equals(memberCode, record.getMemberCode());
    }

    public String name() {
        StringBuilder b = new StringBuilder();
        b.append(memberClass);
        b.append("-");
        b.append(memberCode);
        return b.toString();
    }
}

final class SubsystemGrouping implements Grouping {
    private final String memberClass;
    private final String memberCode;
    private final String subsystemCode;

    SubsystemGrouping(MessageRecord record) {
        this.memberClass = record.getMemberClass();
        this.memberCode = record.getMemberCode();
        this.subsystemCode = record.getSubsystemCode();
    }

    /**
     * checks if the record belongs to this record group
     */
    @Override
    public boolean includes(MessageRecord record) {
        return Objects.equals(memberClass, record.getMemberClass())
                && Objects.equals(memberCode, record.getMemberCode())
                && Objects.equals(subsystemCode, record.getSubsystemCode());

    }

    public String name() {
        StringBuilder b = new StringBuilder();
        b.append(memberClass);
        b.append("-");
        b.append(memberCode);
        if (subsystemCode != null) {
            b.append("-");
            b.append(subsystemCode);
        }
        return b.toString();
    }
}

