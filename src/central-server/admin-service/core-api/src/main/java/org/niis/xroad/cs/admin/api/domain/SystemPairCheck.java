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
 * What a read-only check of a SYSTEM pair found.
 *
 * @param verdict   whether the pair is valid for the server and how it compares with the stored pair
 * @param rejection the reason when the pair is not valid for the server, otherwise {@code null}
 */
public record SystemPairCheck(Verdict verdict, PublishedValueRejection rejection) {

    public static final SystemPairCheck EQUAL = new SystemPairCheck(Verdict.EQUAL, null);
    public static final SystemPairCheck NOT_EQUAL = new SystemPairCheck(Verdict.NOT_EQUAL, null);

    public static SystemPairCheck rejected(PublishedValueRejection rejection) {
        return new SystemPairCheck(Verdict.REJECTED, rejection);
    }

    public enum Verdict {
        /** The pair is valid and equal to the stored pair. */
        EQUAL,
        /** The pair is valid and the server has no equal pair stored. */
        NOT_EQUAL,
        /** The pair is not valid for the server; the reason is in {@link #rejection()}. */
        REJECTED
    }
}
