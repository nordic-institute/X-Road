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

package org.niis.xroad.e2e.parity;

import java.util.List;

/**
 * One row of the parity catalogue: the fixtures to create, the request to send, and the verdict legacy
 * access rights are expected to give.
 *
 * @param id           the variation id, for example {@code V01}
 * @param description  a short human-readable summary of the row
 * @param fixture      what to create on the provider before the request
 * @param request      the request to send from the consumer Security Server; it targets the fixture's first service
 * @param expected     the verdict legacy access rights give
 * @param waitClass    which propagation delay the verdict has to wait out
 */
public record Scenario(String id, String description, FixtureSpec fixture, RequestSpec request, Verdict expected,
                       WaitClass waitClass) {

    /** The provider-side delay between a configuration change and the verdict it causes. */
    public enum WaitClass {
        /** The provider caches access decisions per client and service. */
        ACL_CACHE
    }

    /**
     * @param providerClientId the service owner, in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param services         the REST services to create under the provider
     */
    public record FixtureSpec(String providerClientId, List<ServiceSpec> services) {
    }

    /**
     * A REST service with the base endpoint granted to each listed consumer.
     *
     * @param grantedConsumerIds consumers in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     */
    public record ServiceSpec(List<String> grantedConsumerIds) {
    }

    /**
     * @param consumerClientId the calling client, in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param method           the HTTP method
     * @param path             the path after the service code, starting with {@code /}
     */
    public record RequestSpec(String consumerClientId, String method, String path) {
    }
}
