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
 * @param request      the request to send from the consumer Security Server
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
     * @param services         the service descriptions to create under the provider
     */
    public record FixtureSpec(String providerClientId, List<ServiceSpec> services) {
    }

    /** A service description created under the provider. */
    public sealed interface ServiceSpec permits RestService, WsdlService {
    }

    /**
     * A REST service with the base endpoint granted to each listed consumer.
     *
     * @param grantedConsumerIds consumers in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     */
    public record RestService(List<String> grantedConsumerIds) implements ServiceSpec {
    }

    /** The service layout of a WSDL description. */
    public enum WsdlShape {
        /** One service code in version v1. */
        SINGLE(1),
        /** One service code whose operation names no version, so the service is stored without one. */
        SINGLE_UNVERSIONED(1),
        /** Two service codes in version v1. */
        PAIR(2),
        /** One service code offered in versions v1 and v2. */
        VERSIONS(1);

        private final int codeCount;

        WsdlShape(int codeCount) {
            this.codeCount = codeCount;
        }

        public int codeCount() {
            return codeCount;
        }
    }

    /**
     * A WSDL description whose services get base grants.
     *
     * @param shape                    the service layout
     * @param codeTail                 text appended to every service code of the description, for example {@code .x}
     * @param grantedConsumerIdsByCode the consumers granted each service code, in the order of the shape's codes
     */
    public record WsdlService(WsdlShape shape, String codeTail, List<List<String>> grantedConsumerIdsByCode)
            implements ServiceSpec {

        public WsdlService {
            if (grantedConsumerIdsByCode.size() != shape.codeCount()) {
                throw new IllegalArgumentException("%s has %d service codes but %d grant lists were given"
                        .formatted(shape, shape.codeCount(), grantedConsumerIdsByCode.size()));
            }
        }
    }

    /** The call that produces the verdict. */
    public sealed interface RequestSpec permits RestRequest, SoapRequest {

        /** The calling client, in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form. */
        String consumerClientId();

        /** The position of the called service code among all service codes the fixture creates. */
        int serviceIndex();
    }

    /**
     * @param consumerClientId the calling client, in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param serviceIndex     the position of the called service code among all service codes the fixture creates
     * @param method           the HTTP method
     * @param path             the path after the service code, starting with {@code /}
     */
    public record RestRequest(String consumerClientId, int serviceIndex, String method, String path)
            implements RequestSpec {
    }

    /**
     * @param consumerClientId   the calling client, in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param serviceIndex       the position of the called service code among all service codes the fixture creates
     * @param serviceVersion     the version named in the request header, or {@code null} for none
     * @param securityServerHint the {@code INSTANCE:CLASS:MEMBER:SERVERCODE} security server named in the request
     *                           header, or {@code null} for none
     */
    public record SoapRequest(String consumerClientId, int serviceIndex, String serviceVersion,
                              String securityServerHint) implements RequestSpec {
    }
}
