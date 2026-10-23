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
package org.niis.xroad.cs.admin.api.domain;

/**
 * What happened to one published value.
 *
 * @param outcome   whether the value was written, left as it was, or refused
 * @param rejection the reason when the value was refused, otherwise {@code null}
 */
public record ParticipantValueResult(Outcome outcome, ParticipantValueRejection rejection) {

    public static final ParticipantValueResult STORED = new ParticipantValueResult(Outcome.STORED, null);
    public static final ParticipantValueResult UNCHANGED = new ParticipantValueResult(Outcome.UNCHANGED, null);

    public static ParticipantValueResult rejected(ParticipantValueRejection rejection) {
        return new ParticipantValueResult(Outcome.REJECTED, rejection);
    }

    public enum Outcome {
        /** The value was written, as a new value or as a replacement. */
        STORED,
        /** Nothing was written: the stored value is already the published one, or there was nothing to replace. */
        UNCHANGED,
        /** The value was refused; the reason is in {@link #rejection()}. */
        REJECTED
    }
}
