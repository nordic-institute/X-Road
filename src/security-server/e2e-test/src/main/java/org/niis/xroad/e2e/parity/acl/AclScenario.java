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

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * One row of the parity catalogue: the fixtures to create, the requests to send, and the verdict legacy
 * access rights are expected to give each request.
 *
 * <p>Client ids are written {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM}. A service index is the position of a service
 * code among all service codes the fixture creates, in creation order.
 *
 * @param id         the variation id, for example {@code V01}
 * @param checks     the requests to send from the consumer Security Server, each with its expected verdict
 * @param waitClass  which propagation delay the verdicts have to wait out
 * @param phases     the changes made after the checks, each followed by the checks it leads to; empty for a row
 *                   whose verdicts do not change over time
 * @param adminGrant the admin API grant the row issues once the fixture is in place and the status it expects;
 *                   a row with a grant has no checks and does not poll. {@code null} for a row that sends requests
 */
public record AclScenario(String id, String description, FixtureSpec fixture, List<Check> checks,
                          WaitClass waitClass, List<Phase> phases, AdminGrantExpectation adminGrant) {

    /** A row whose outcome is the admin API status of one grant. */
    public AclScenario(String id, String description, FixtureSpec fixture, AdminGrantExpectation adminGrant,
                       WaitClass waitClass) {
        this(id, description, fixture, List.of(), waitClass, List.of(), adminGrant);
    }

    /** A row whose verdicts do not change over time. */
    public AclScenario(String id, String description, FixtureSpec fixture, List<Check> checks, WaitClass waitClass) {
        this(id, description, fixture, checks, waitClass, List.of(), null);
    }

    /** A row whose verdicts change over time. */
    public AclScenario(String id, String description, FixtureSpec fixture, List<Check> checks, List<Phase> phases,
                       WaitClass waitClass) {
        this(id, description, fixture, checks, waitClass, phases, null);
    }

    /** Changes made while the fixture is in place, applied in order, and the checks that follow them. */
    public record Phase(List<PhaseChange> changes, List<Check> checks) {
    }

    /** A change made between two phases. */
    public sealed interface PhaseChange permits Grant, Revoke, GrantGroup, SwapBackendSpec, Refresh,
            AddGlobalGroupMember, RemoveGlobalGroupMember, DeleteGlobalGroup, RemoveLocalGroupMember,
            DeleteLocalGroup, DeleteDescription {
    }

    /** A member or subsystem is removed from a local group of the provider. */
    public record RemoveLocalGroupMember(String groupCode, String memberId) implements PhaseChange {
    }

    /** A local group of the provider is deleted. */
    public record DeleteLocalGroup(String groupCode) implements PhaseChange {
    }

    /** A service description is deleted, and its access rights with it. */
    public record DeleteDescription(int serviceIndex) implements PhaseChange {
    }

    /** A member or subsystem joins a global group of the fixture at the Central Server. */
    public record AddGlobalGroupMember(String code, String memberId) implements PhaseChange {
    }

    /** A member or subsystem is removed from a global group of the fixture at the Central Server. */
    public record RemoveGlobalGroupMember(String code, String memberId) implements PhaseChange {
    }

    /** A global group of the fixture is deleted at the Central Server. */
    public record DeleteGlobalGroup(String code) implements PhaseChange {
    }

    /** The consumer is granted the base access to a service code. */
    public record Grant(int serviceIndex, String consumerId) implements PhaseChange {
    }

    /** The consumer's access to a service code is revoked, with its endpoint grants of that service. */
    public record Revoke(int serviceIndex, String consumerId) implements PhaseChange {
    }

    /** The provider's local group is granted the base access to a service code. */
    public record GrantGroup(int serviceIndex, String groupCode) implements PhaseChange {
    }

    /**
     * The backend starts serving the second spec of the {@link OpenApiSpec#PETS_THEN_STATUS_ONLY} description at
     * the URL the service description was added from.
     */
    public record SwapBackendSpec(int serviceIndex) implements PhaseChange {
    }

    /** The service description is refreshed from the backend. */
    public record Refresh(int serviceIndex) implements PhaseChange {
    }

    /**
     * A request and the verdict it has to reach. {@link Verdict#ERROR} and {@link Verdict#INTERNAL_ERROR} are not
     * accepted as expectations, since a failed probe or an unmapped fault would satisfy them. A request whose
     * reply carries its own expectation cannot expect {@link Verdict#ALLOWED} without a text the reply has to carry.
     *
     * @param notice text the reply has to carry besides the verdict, or {@code null} for none
     */
    public record Check(RequestSpec request, Verdict expected, String notice) {

        public Check {
            if (expected == Verdict.ERROR || expected == Verdict.INTERNAL_ERROR) {
                throw new IllegalArgumentException("a check cannot expect " + expected);
            }
            if (expected == Verdict.ALLOWED && requiresNoText(request)) {
                throw new IllegalArgumentException(
                        "a check that expects ALLOWED needs a text the reply carries: " + request.describe());
            }
        }

        public Check(RequestSpec request, Verdict expected) {
            this(request, expected, null);
        }

        private static boolean requiresNoText(RequestSpec request) {
            return switch (request) {
                case MetaserviceRequest meta -> !meta.expectation().requiresText();
                case MonitoringRequest monitoring -> !monitoring.expectation().requiresText();
                case ListClientsRequest listClients -> !listClients.expectation().requiresText();
                default -> false;
            };
        }
    }

    /**
     * A grant made through the provider's admin API, and the HTTP status and error code the API is expected to
     * answer with.
     *
     * @param subjectId         the member or subsystem the access is granted to
     * @param expectedErrorCode the {@code error.code} of the answer, or {@code null} for an answer without an error
     */
    public record AdminGrantExpectation(String subjectId, int serviceIndex, int expectedStatus,
                                        String expectedErrorCode) {
    }

    /** The Security Server that hosts the provider client. */
    public enum ProviderHost {
        SS0, SS1
    }

    /** The provider-side delay between a configuration change and the verdict it causes. */
    public enum WaitClass {
        /** The provider caches access decisions per client and service: 60 s of cache plus slack. */
        ACL_CACHE(Duration.ofSeconds(90)),
        /**
         * A Central Server change reaches the provider through global configuration: generation 10 s, configuration
         * client 10 s and proxy refresh 10 s, then the access decision cache of 60 s, plus slack.
         */
        GLOBALCONF(Duration.ofSeconds(120));

        private final Duration timeout;

        WaitClass(Duration timeout) {
            this.timeout = timeout;
        }

        /** How long a verdict may take to settle after the change that causes it. */
        public Duration timeout() {
            return timeout;
        }
    }

    /**
     * @param groups       the local groups to create, before the services whose grants name them
     * @param changes      the changes applied to the created service descriptions before the requests are sent
     * @param globalGroups the global groups to create at the Central Server, before the services whose grants
     *                     name them
     * @param providerHost the Security Server that hosts the provider and receives the admin API calls
     */
    public record FixtureSpec(String providerClientId, List<ServiceSpec> services, List<LocalGroupSpec> groups,
                              List<DescriptionChange> changes, List<GlobalGroupSpec> globalGroups,
                              ProviderHost providerHost) {

        public FixtureSpec(String providerClientId, List<ServiceSpec> services, List<LocalGroupSpec> groups,
                           List<DescriptionChange> changes, List<GlobalGroupSpec> globalGroups) {
            this(providerClientId, services, groups, changes, globalGroups, ProviderHost.SS0);
        }

        public FixtureSpec(String providerClientId, List<ServiceSpec> services, List<LocalGroupSpec> groups,
                           List<DescriptionChange> changes) {
            this(providerClientId, services, groups, changes, List.of());
        }

        public FixtureSpec(String providerClientId, List<ServiceSpec> services, List<LocalGroupSpec> groups) {
            this(providerClientId, services, groups, List.of(), List.of());
        }

        public FixtureSpec(String providerClientId, List<ServiceSpec> services) {
            this(providerClientId, services, List.of(), List.of(), List.of());
        }
    }

    /**
     * A global group kept at the Central Server. Its code is namespaced per run and scenario, except for
     * {@link #SECURITY_SERVER_OWNERS}, which exists on every Central Server and is never created, changed or
     * deleted.
     *
     * @param code      the group code before namespacing
     * @param memberIds the members to add, each a member or subsystem id
     */
    public record GlobalGroupSpec(String code, List<String> memberIds) {

        /** The code of the group the Central Server fills with the owners of the registered Security Servers. */
        public static final String SECURITY_SERVER_OWNERS = "security-server-owners";

        public GlobalGroupSpec(String code) {
            this(code, List.of());
        }

        /** Whether the group is the Central Server's own owners group. */
        public boolean isOwnersGroup() {
            return SECURITY_SERVER_OWNERS.equals(code);
        }
    }

    /** A change applied to a created service description, which is named by its position among the services. */
    public sealed interface DescriptionChange permits Disable, Enable, Delete, RenameService {

        /** The position of the changed service description among the fixture's services. */
        int serviceIndex();
    }

    /**
     * The description is disabled.
     *
     * @param notice the text shown to callers, or {@code null} to disable without one
     */
    public record Disable(int serviceIndex, String notice) implements DescriptionChange {
    }

    /** The description is enabled again. */
    public record Enable(int serviceIndex) implements DescriptionChange {
    }

    /** The description is deleted, and its access rights with it. */
    public record Delete(int serviceIndex) implements DescriptionChange {
    }

    /**
     * The service code of a REST description is renamed. The renamed code is added after the codes the fixture
     * creates, in the order of the changes.
     */
    public record RenameService(int serviceIndex) implements DescriptionChange {
    }

    /**
     * A local group of a client on the provider Security Server. Its code is namespaced per run and scenario,
     * so the same code under two owners stays one code.
     *
     * @param ownerClientId    the client that owns the group
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
    public sealed interface ServiceSpec permits RestService, WsdlService, OpenApiService, UndefinedService,
            ManagementService {
    }

    /**
     * A service the provider has from the start, such as a management service, whose grants the fixture changes.
     * No description is created or deleted, and the service code is not namespaced. Every grant the fixture
     * changes, in setup or in a phase, is changed back when the fixture is closed.
     *
     * @param serviceCode        the service code, as the provider already has it
     * @param revokedConsumerIds consumers whose existing access is revoked in setup
     * @param grantedConsumerIds consumers granted access in setup
     */
    public record ManagementService(String serviceCode, List<String> revokedConsumerIds,
                                    List<String> grantedConsumerIds) implements ServiceSpec {
    }

    /** A service code the fixture reserves without creating any description, so no service by that code exists. */
    public record UndefinedService() implements ServiceSpec {
    }

    /**
     * A REST service with the base endpoint granted to each listed consumer, and further endpoints created
     * on top of it.
     *
     * @param grantedConsumerIds      consumers granted the base endpoint
     * @param endpoints               the endpoints created next to the base endpoint
     * @param grantedGroupCodes       codes of the provider's local groups granted the base endpoint, before
     *                                namespacing
     * @param grantedGlobalGroupCodes codes of the fixture's global groups granted the base endpoint, before
     *                                namespacing
     */
    public record RestService(List<String> grantedConsumerIds, List<EndpointSpec> endpoints,
                              List<String> grantedGroupCodes, List<String> grantedGlobalGroupCodes)
            implements ServiceSpec {

        public RestService(List<String> grantedConsumerIds, List<EndpointSpec> endpoints,
                           List<String> grantedGroupCodes) {
            this(grantedConsumerIds, endpoints, grantedGroupCodes, List.of());
        }

        public RestService(List<String> grantedConsumerIds, List<EndpointSpec> endpoints) {
            this(grantedConsumerIds, endpoints, List.of(), List.of());
        }

        public RestService(List<String> grantedConsumerIds) {
            this(grantedConsumerIds, List.of(), List.of(), List.of());
        }
    }

    /**
     * An endpoint created on a REST service.
     *
     * @param method                  the HTTP method, or {@code *} for any
     * @param path                    the path pattern, starting with {@code /}
     * @param grantedConsumerIds      consumers granted the endpoint
     * @param grantedGroupCodes       codes of the provider's local groups granted the endpoint, before namespacing
     * @param grantedGlobalGroupCodes codes of the fixture's global groups granted the endpoint, before namespacing
     * @param followUp                what happens to the endpoint after it is created and granted
     */
    public record EndpointSpec(String method, String path, List<String> grantedConsumerIds,
                               List<String> grantedGroupCodes, List<String> grantedGlobalGroupCodes,
                               FollowUp followUp) {
        public EndpointSpec(String method, String path, List<String> grantedConsumerIds,
                            List<String> grantedGroupCodes, FollowUp followUp) {
            this(method, path, grantedConsumerIds, grantedGroupCodes, List.of(), followUp);
        }

        public static EndpointSpec forGlobalGroups(String method, String path, List<String> grantedGlobalGroupCodes) {
            return new EndpointSpec(method, path, List.of(), List.of(), grantedGlobalGroupCodes, new Kept());
        }

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
        PETS,
        /** Serves {@link #PETS} until a {@link SwapBackendSpec}, then {@link #STATUS_ONLY}. */
        PETS_THEN_STATUS_ONLY
    }

    /**
     * An endpoint the OpenAPI description generates, granted to consumers.
     *
     * @param method             the HTTP method
     * @param path               the stored path pattern, starting with {@code /}
     * @param grantedConsumerIds consumers granted the endpoint
     */
    public record GeneratedEndpointGrant(String method, String path, List<String> grantedConsumerIds) {
    }

    /**
     * An OpenAPI 3 description. Its service gets a base endpoint that no row grants; further grants go to the
     * endpoints the description generates or to manual endpoints that a later description edit may turn into
     * generated ones. Generated endpoints are never deleted on their own.
     *
     * @param spec            the description the service is added from
     * @param manualEndpoints endpoints created by hand on top of the generated ones
     * @param generatedGrants grants on endpoints the description generates
     * @param editedToSpec    the description the service is edited to after the grants, or {@code null} for none
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
        VERSIONS(1),
        /** One service code in version v1, served at a SOAP 1.2 address. */
        SOAP12(1);

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
    public sealed interface RequestSpec permits RestRequest, SoapRequest, MetaserviceRequest, MonitoringRequest,
            ListClientsRequest, ManagementRequest {

        /** A short text naming the call, for failure messages. */
        String describe();
    }

    /**
     * A REST call to a service of the provider.
     *
     * @param method the HTTP method
     * @param path   the path after the service code, starting with {@code /}, or empty for the bare service root
     */
    public record RestRequest(String consumerClientId, int serviceIndex, String method, String path)
            implements RequestSpec {

        @Override
        public String describe() {
            return method + " " + (path.isEmpty() ? "(service root)" : path);
        }
    }

    /**
     * A SOAP call to a service of the provider.
     *
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

    /** How a metaservice is called. */
    public enum Transport {
        REST, SOAP
    }

    /**
     * What a metaservice reply has to say. Texts match as whole tokens: the characters around a match are none of
     * letters, digits, {@code _}, {@code .} and {@code -}.
     *
     * @param contains the texts the reply has to carry
     * @param without  the texts the reply must not carry
     * @param listed   the positions of the service codes the reply has to carry
     * @param unlisted the positions of the service codes the reply must not carry
     */
    public record Expectation(List<String> contains, List<String> without, List<Integer> listed,
                              List<Integer> unlisted) {
        /** No requirement on the reply beyond the absence of a fault. */
        public static final Expectation NONE = new Expectation(List.of(), List.of(), List.of(), List.of());

        public static Expectation listing(List<Integer> listed, List<Integer> unlisted) {
            return new Expectation(List.of(), List.of(), listed, unlisted);
        }

        public static Expectation carrying(String... texts) {
            return new Expectation(List.of(texts), List.of(), List.of(), List.of());
        }

        public static Expectation masked(String shown, String hidden) {
            return new Expectation(List.of(shown), List.of(hidden), List.of(), List.of());
        }

        /** Whether the reply has to carry any text, as opposed to only lacking some. */
        public boolean requiresText() {
            return !contains.isEmpty() || !listed.isEmpty();
        }

        /** The texts the reply has to carry, with the listed positions resolved to service codes. */
        public List<String> required(List<String> serviceCodes) {
            return Stream.concat(contains.stream(), listed.stream().map(serviceCodes::get)).toList();
        }

        /** The texts the reply must not carry, with the unlisted positions resolved to service codes. */
        public List<String> forbidden(List<String> serviceCodes) {
            return Stream.concat(without.stream(), unlisted.stream().map(serviceCodes::get)).toList();
        }
    }

    /**
     * A call to a metaservice of the provider. The provider's Security Server answers it itself, so the reply
     * never reaches the backend.
     *
     * @param transport     whether the call is REST or SOAP
     * @param code          the metaservice code, for example {@code listMethods}
     * @param method        the HTTP method; SOAP calls always post
     * @param serviceIndex  the service the call asks about, or {@link #NO_SERVICE}
     * @param targetVersion the version of that service a SOAP call names, or {@code null} for none
     * @param expectation   what the reply has to say
     */
    public record MetaserviceRequest(String consumerClientId, Transport transport, String code, String method,
                                     int serviceIndex, String targetVersion, Expectation expectation)
            implements RequestSpec {
        /** The service index of a call that asks about no particular service. */
        public static final int NO_SERVICE = -1;

        public static MetaserviceRequest rest(String consumerClientId, String code, int serviceIndex,
                                              Expectation expectation) {
            return new MetaserviceRequest(consumerClientId, Transport.REST, code, "GET", serviceIndex, null,
                    expectation);
        }

        public static MetaserviceRequest soap(String consumerClientId, String code, int serviceIndex,
                                              Expectation expectation) {
            return soap(consumerClientId, code, serviceIndex, null, expectation);
        }

        public static MetaserviceRequest soap(String consumerClientId, String code, int serviceIndex,
                                              String targetVersion, Expectation expectation) {
            return new MetaserviceRequest(consumerClientId, Transport.SOAP, code, "POST", serviceIndex,
                    targetVersion, expectation);
        }

        @Override
        public String describe() {
            return "%s %s %s%s".formatted(transport, method, code,
                    serviceIndex == NO_SERVICE ? "" : " of service " + serviceIndex);
        }
    }

    /**
     * A SOAP call to a monitoring metaservice of the provider member's Security Server. Both the client and the
     * service are named at member level when the identifier has no subsystem, and the header names the Security
     * Server that answers.
     *
     * @param consumerClientId the calling client, with or without a subsystem
     * @param code             the monitoring metaservice code, for example {@code getSecurityServerHealthData}
     * @param filterClientId   the provider client a health data call filters on, or {@code null} for none
     * @param expectation      what the reply has to say
     */
    public record MonitoringRequest(String consumerClientId, String code, String filterClientId,
                                    Expectation expectation) implements RequestSpec {

        @Override
        public String describe() {
            return "SOAP %s as %s".formatted(code, consumerClientId);
        }
    }

    /**
     * A {@code listClients} call to the consumer Security Server's own proxy. The call carries no client header.
     *
     * @param expectation what the reply has to say
     */
    public record ListClientsRequest(Expectation expectation) implements RequestSpec {

        @Override
        public String describe() {
            return "GET listClients";
        }
    }

    /**
     * A registration of a new subsystem that the consumer Security Server sends to the provider's management
     * service. The sender is always the owner of the consumer Security Server.
     *
     * @param consumerClientId the owner of the consumer Security Server, under which the subsystem is added
     */
    public record ManagementRequest(String consumerClientId) implements RequestSpec {
        @Override
        public String describe() {
            return "client registration sent by " + consumerClientId;
        }
    }
}
