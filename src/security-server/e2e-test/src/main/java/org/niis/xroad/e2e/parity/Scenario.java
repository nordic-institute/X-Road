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
 * One row of the parity catalogue: the fixtures to create, the requests to send, and the verdict legacy
 * access rights are expected to give each request.
 *
 * @param id           the variation id, for example {@code V01}
 * @param description  a short human-readable summary of the row
 * @param fixture      what to create on the provider before the requests
 * @param checks       the requests to send from the consumer Security Server, each with its expected verdict
 * @param waitClass    which propagation delay the verdicts have to wait out
 */
public record Scenario(String id, String description, FixtureSpec fixture, List<Check> checks,
                       WaitClass waitClass) {

    /** A row with a single request. */
    public Scenario(String id, String description, FixtureSpec fixture, RequestSpec request, Verdict expected,
                    WaitClass waitClass) {
        this(id, description, fixture, List.of(new Check(request, expected)), waitClass);
    }

    /**
     * @param request  the call that produces the verdict
     * @param expected the verdict legacy access rights give
     */
    public record Check(RequestSpec request, Verdict expected) {
    }

    /** The provider-side delay between a configuration change and the verdict it causes. */
    public enum WaitClass {
        /** The provider caches access decisions per client and service. */
        ACL_CACHE
    }

    /**
     * @param providerClientId the service owner, in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param services         the service descriptions to create under the provider
     * @param groups           the local groups to create, before the services whose grants name them
     */
    public record FixtureSpec(String providerClientId, List<ServiceSpec> services, List<LocalGroupSpec> groups) {

        public FixtureSpec(String providerClientId, List<ServiceSpec> services) {
            this(providerClientId, services, List.of());
        }
    }

    /**
     * A local group of a client on the provider Security Server. Its code is namespaced per run and scenario,
     * so the same code under two owners stays one code.
     *
     * @param ownerClientId    the client that owns the group, in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param code             the group code before namespacing
     * @param memberIds        the members to add, each a member or subsystem id
     * @param removedMemberIds the members removed again after the grants are made
     * @param deleted          whether the group is deleted after the grants are made
     */
    public record LocalGroupSpec(String ownerClientId, String code, List<String> memberIds,
                                 List<String> removedMemberIds, boolean deleted) {

        public LocalGroupSpec(String ownerClientId, String code, List<String> memberIds) {
            this(ownerClientId, code, memberIds, List.of(), false);
        }
    }

    /** A service description created under the provider. */
    public sealed interface ServiceSpec permits RestService, WsdlService, OpenApiService {
    }

    /**
     * A REST service with the base endpoint granted to each listed consumer, and further endpoints created
     * on top of it.
     *
     * @param grantedConsumerIds consumers in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param endpoints          the endpoints created next to the base endpoint
     * @param grantedGroupCodes  codes of the provider's local groups granted the base endpoint, before namespacing
     */
    public record RestService(List<String> grantedConsumerIds, List<EndpointSpec> endpoints,
                              List<String> grantedGroupCodes) implements ServiceSpec {

        public RestService(List<String> grantedConsumerIds, List<EndpointSpec> endpoints) {
            this(grantedConsumerIds, endpoints, List.of());
        }

        public RestService(List<String> grantedConsumerIds) {
            this(grantedConsumerIds, List.of(), List.of());
        }
    }

    /**
     * An endpoint created on a REST service.
     *
     * @param method             the HTTP method, or {@code *} for any
     * @param path               the path pattern, starting with {@code /}
     * @param grantedConsumerIds consumers in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param grantedGroupCodes  codes of the provider's local groups granted the endpoint, before namespacing
     * @param followUp           what happens to the endpoint after it is created and granted
     */
    public record EndpointSpec(String method, String path, List<String> grantedConsumerIds,
                               List<String> grantedGroupCodes, FollowUp followUp) {

        public EndpointSpec(String method, String path, List<String> grantedConsumerIds, FollowUp followUp) {
            this(method, path, grantedConsumerIds, List.of(), followUp);
        }

        public EndpointSpec(String method, String path, List<String> grantedConsumerIds,
                            List<String> grantedGroupCodes) {
            this(method, path, grantedConsumerIds, grantedGroupCodes, new Kept());
        }

        public EndpointSpec(String method, String path, List<String> grantedConsumerIds) {
            this(method, path, grantedConsumerIds, List.of(), new Kept());
        }
    }

    /** A change applied to a created endpoint before the requests are sent. */
    public sealed interface FollowUp permits Kept, Deleted, EditedTo {
    }

    /** The endpoint stays as created. */
    public record Kept() implements FollowUp {
    }

    /** The endpoint is deleted. */
    public record Deleted() implements FollowUp {
    }

    /**
     * The endpoint's method and path are edited.
     *
     * @param method the new HTTP method, or {@code *} for any
     * @param path   the new path pattern, starting with {@code /}
     */
    public record EditedTo(String method, String path) implements FollowUp {
    }

    /** The OpenAPI descriptions the backend stub serves. */
    public enum OpenApiSpec {
        /** Declares only {@code GET /status}. */
        STATUS_ONLY,
        /** Declares {@code GET /pets/{petId}}, stored as {@code /pets/*}. */
        PETS
    }

    /**
     * An endpoint the OpenAPI description generates, granted to consumers.
     *
     * @param method             the HTTP method
     * @param path               the stored path pattern, starting with {@code /}
     * @param grantedConsumerIds consumers in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     */
    public record GeneratedEndpointGrant(String method, String path, List<String> grantedConsumerIds) {
    }

    /**
     * An OpenAPI 3 description. Its service gets a base endpoint that no row grants; further grants go to the
     * endpoints the description generates or to manual endpoints that a later description edit may turn into
     * generated ones. Generated endpoints are never deleted on their own.
     *
     * @param spec              the description the service is added from
     * @param manualEndpoints   endpoints created by hand on top of the generated ones
     * @param generatedGrants   grants on endpoints the description generates
     * @param editedToSpec      the description the service is edited to after the grants, or {@code null} for none
     */
    public record OpenApiService(OpenApiSpec spec, List<EndpointSpec> manualEndpoints,
                                 List<GeneratedEndpointGrant> generatedGrants, OpenApiSpec editedToSpec)
            implements ServiceSpec {
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

        /** A short text naming the call, for failure messages. */
        String describe();
    }

    /**
     * @param consumerClientId the calling client, in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param serviceIndex     the position of the called service code among all service codes the fixture creates
     * @param method           the HTTP method
     * @param path             the path after the service code, starting with {@code /}, or empty for the bare
     *                         service root
     */
    public record RestRequest(String consumerClientId, int serviceIndex, String method, String path)
            implements RequestSpec {

        @Override
        public String describe() {
            return method + " " + (path.isEmpty() ? "(service root)" : path);
        }
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

        @Override
        public String describe() {
            return "SOAP call as " + consumerClientId + " to service " + serviceIndex;
        }
    }
}
