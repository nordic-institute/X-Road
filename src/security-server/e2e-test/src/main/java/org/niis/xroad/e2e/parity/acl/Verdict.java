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

package org.niis.xroad.e2e.parity.acl;

/**
 * What a service call ended in, as the parity suite sees it. Anything that is not one of the named
 * outcomes is {@link #ERROR}.
 */
public enum Verdict {
    ALLOWED,
    ACCESS_DENIED,
    UNKNOWN_SERVICE,
    SERVICE_DISABLED,
    INVALID_SERVICE_TYPE,
    UNKNOWN_MEMBER,
    INVALID_SECURITY_SERVER,
    /** The provider failed while handling the request and answered with its {@code internal_error} fault. */
    INTERNAL_ERROR,
    /** The consumer's own proxy refused the request before it reached the provider. */
    CONSUMER_REJECTED,
    /** The provider refused the call because of its agreement token, for example a missing or non-matching one. */
    AGREEMENT_TOKEN_REJECTED,
    /**
     * The consumer's proxy failed to reach or serve the provider and answered with a generic fault, which is what a
     * failed data space catalog fetch, negotiation or transfer looks like to the caller.
     */
    CONSUMER_FAILED,
    ERROR
}
