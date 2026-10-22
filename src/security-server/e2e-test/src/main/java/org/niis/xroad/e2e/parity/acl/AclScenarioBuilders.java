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

import org.niis.xroad.e2e.parity.acl.AclScenario.AdminGrantExpectation;
import org.niis.xroad.e2e.parity.acl.AclScenario.Check;
import org.niis.xroad.e2e.parity.acl.AclScenario.DescriptionChange;
import org.niis.xroad.e2e.parity.acl.AclScenario.EndpointSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.Expectation;
import org.niis.xroad.e2e.parity.acl.AclScenario.FixtureSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.FollowUp;
import org.niis.xroad.e2e.parity.acl.AclScenario.GeneratedEndpointGrant;
import org.niis.xroad.e2e.parity.acl.AclScenario.GlobalGroupSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.LocalGroupSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.ManagementRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.ManagementService;
import org.niis.xroad.e2e.parity.acl.AclScenario.MetaserviceRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.MonitoringRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiService;
import org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.Phase;
import org.niis.xroad.e2e.parity.acl.AclScenario.PhaseChange;
import org.niis.xroad.e2e.parity.acl.AclScenario.ProviderHost;
import org.niis.xroad.e2e.parity.acl.AclScenario.RequestSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.RestRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.RestService;
import org.niis.xroad.e2e.parity.acl.AclScenario.ServiceSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.SoapRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.WaitClass;
import org.niis.xroad.e2e.parity.acl.AclScenario.WsdlService;
import org.niis.xroad.e2e.parity.acl.AclScenario.WsdlShape;

import java.util.List;

import static org.niis.xroad.e2e.parity.acl.AclParticipants.CONSUMER;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.GLOBAL_GROUP;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.GROUP;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.MANAGEMENT;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.MEMBER_OF_CONSUMER;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.PROVIDER;

/**
 * The shorthand the parity scenario rows are written with. Rows wait out the ACL cache unless a builder says
 * otherwise, and call from the consumer to the first service of the provider unless they name another.
 */
public final class AclScenarioBuilders {

    /** The consumer subsystem as the only grantee. */
    public static final List<String> CONSUMER_ONLY = List.of(CONSUMER);

    private AclScenarioBuilders() {
    }

    /** A row without its description; {@link #as(String)} takes it last so a row reads id, setup, outcome, text. */
    @FunctionalInterface
    public interface Row {
        /** The finished scenario with its description. */
        AclScenario as(String description);
    }

    /** A row with a single request. */
    public static Row row(String id, FixtureSpec fixture, RequestSpec request, Verdict expected) {
        return description -> new AclScenario(id, description, fixture, List.of(new Check(request, expected)),
                WaitClass.ACL_CACHE);
    }

    /** A row whose verdicts do not change over time. */
    public static Row row(String id, FixtureSpec fixture, Check... checks) {
        return description -> new AclScenario(id, description, fixture, List.of(checks), WaitClass.ACL_CACHE);
    }

    /** A row whose verdicts change over time. */
    public static Row phased(String id, FixtureSpec fixture, List<Check> checks, Phase... phases) {
        return description -> new AclScenario(id, description, fixture, checks, List.of(phases),
                WaitClass.ACL_CACHE);
    }

    /** A row whose verdicts wait on global configuration. */
    public static Row globalconfRow(String id, FixtureSpec fixture, Check... checks) {
        return description -> new AclScenario(id, description, fixture, List.of(checks), WaitClass.GLOBALCONF);
    }

    /** A row whose verdicts change over time and wait on global configuration. */
    public static Row globalconfPhased(String id, FixtureSpec fixture, List<Check> checks, Phase... phases) {
        return description -> new AclScenario(id, description, fixture, checks, List.of(phases),
                WaitClass.GLOBALCONF);
    }

    /** A row whose outcome is the admin API status and error code of granting a subject access to a service. */
    public static Row adminGrant(String id, FixtureSpec fixture, String subjectId, int serviceIndex,
                                 int expectedStatus, String expectedErrorCode) {
        return description -> new AclScenario(id, description, fixture,
                new AdminGrantExpectation(subjectId, serviceIndex, expectedStatus, expectedErrorCode),
                WaitClass.ACL_CACHE);
    }

    /** The changes made after a row's checks, and the checks that follow them. */
    public static Phase phase(List<PhaseChange> changes, Check... checks) {
        return new Phase(changes, List.of(checks));
    }

    /** One change made after a row's checks, and the checks that follow it. */
    public static Phase phase(PhaseChange change, Check... checks) {
        return phase(List.of(change), checks);
    }

    /** A request and the verdict it is expected to get. */
    public static Check check(RequestSpec request, Verdict expected) {
        return new Check(request, expected);
    }

    /** A request and the verdict it is expected to get, with a notice the reply has to carry. */
    public static Check check(RequestSpec request, Verdict expected, String notice) {
        return new Check(request, expected, notice);
    }

    /** A REST call from the consumer to the first service that is expected to pass. */
    public static Check allowed(String method, String path) {
        return allowed(CONSUMER, method, path);
    }

    /** A REST call from the given client to the first service that is expected to pass. */
    public static Check allowed(String consumer, String method, String path) {
        return check(new RestRequest(consumer, 0, method, path), Verdict.ALLOWED);
    }

    /** A REST call from the consumer to the given service that is expected to pass. */
    public static Check allowed(int serviceIndex, String method, String path) {
        return check(restRequest(serviceIndex, method, path), Verdict.ALLOWED);
    }

    /** A call that is expected to pass. */
    public static Check allowed(RequestSpec request) {
        return check(request, Verdict.ALLOWED);
    }

    /** A REST call from the consumer to the first service that is expected to be denied. */
    public static Check denied(String method, String path) {
        return denied(CONSUMER, method, path);
    }

    /** A REST call from the given client to the first service that is expected to be denied. */
    public static Check denied(String consumer, String method, String path) {
        return check(new RestRequest(consumer, 0, method, path), Verdict.ACCESS_DENIED);
    }

    /** The owner of the consumer Security Server registering a client, with the verdict it is expected to get. */
    public static Check registration(Verdict expected) {
        return check(new ManagementRequest(MEMBER_OF_CONSUMER), expected);
    }

    /** A REST call from the consumer to the first service. */
    public static RestRequest restRequest(String method, String path) {
        return restRequest(0, method, path);
    }

    /** A REST call from the given client to the first service. */
    public static RestRequest restRequest(String consumer, String method, String path) {
        return new RestRequest(consumer, 0, method, path);
    }

    /** A REST call from the consumer to the given service. */
    public static RestRequest restRequest(int serviceIndex, String method, String path) {
        return new RestRequest(CONSUMER, serviceIndex, method, path);
    }

    /** A SOAP call from the consumer that names no Security Server in its header. */
    public static SoapRequest soapRequest(int serviceIndex, String version) {
        return soapRequest(serviceIndex, version, null);
    }

    /** A SOAP call from the consumer that names a Security Server in its header. */
    public static SoapRequest soapRequest(int serviceIndex, String version, String securityServerHint) {
        return new SoapRequest(CONSUMER, serviceIndex, version, securityServerHint);
    }

    /** A SOAP metaservice call from the consumer. */
    public static MetaserviceRequest metaSoap(String code, int serviceIndex, Expectation expectation) {
        return MetaserviceRequest.soap(CONSUMER, code, serviceIndex, expectation);
    }

    /** A SOAP metaservice call from the consumer that names a service version. */
    public static MetaserviceRequest metaSoap(String code, int serviceIndex, String targetVersion,
                                              Expectation expectation) {
        return MetaserviceRequest.soap(CONSUMER, code, serviceIndex, targetVersion, expectation);
    }

    /** A REST metaservice call from the consumer. */
    public static MetaserviceRequest metaRest(String code, int serviceIndex, Expectation expectation) {
        return MetaserviceRequest.rest(CONSUMER, code, serviceIndex, expectation);
    }

    /** A SOAP call to a monitoring metaservice of the provider member's Security Server. */
    public static MonitoringRequest monitoring(String consumer, String code, String filterClientId,
                                               Expectation expectation) {
        return new MonitoringRequest(consumer, code, filterClientId, expectation);
    }

    /** An endpoint granted to the consumer. */
    public static EndpointSpec endpoint(String method, String path) {
        return new EndpointSpec(method, path, CONSUMER_ONLY);
    }

    /** An endpoint granted to the given clients. */
    public static EndpointSpec endpoint(String method, String path, List<String> grantedConsumerIds) {
        return new EndpointSpec(method, path, grantedConsumerIds);
    }

    /** An endpoint granted to the consumer and changed after it is created. */
    public static EndpointSpec endpoint(String method, String path, FollowUp followUp) {
        return new EndpointSpec(method, path, CONSUMER_ONLY, followUp);
    }

    /** An endpoint granted to a local group only. */
    public static EndpointSpec groupEndpoint(String method, String path, String groupCode) {
        return new EndpointSpec(method, path, List.of(), List.of(groupCode));
    }

    /** An endpoint granted to a global group only. */
    public static EndpointSpec globalGroupEndpoint(String method, String path, String groupCode) {
        return EndpointSpec.forGlobalGroups(method, path, List.of(groupCode));
    }

    /** A REST service whose base endpoint is granted to the clients, with the endpoints created on top of it. */
    public static RestService rest(List<String> grantedConsumerIds, EndpointSpec... endpoints) {
        return new RestService(grantedConsumerIds, List.of(endpoints));
    }

    /** A REST service whose base endpoint is granted to a local group. */
    public static RestService restToGroup(String groupCode) {
        return new RestService(List.of(), List.of(), List.of(groupCode));
    }

    /** A REST service whose base endpoint is granted to a global group. */
    public static RestService restToGlobalGroup(String groupCode) {
        return new RestService(List.of(), List.of(), List.of(), List.of(groupCode));
    }

    /** A WSDL service whose codes carry no extra text. */
    public static WsdlService wsdlService(WsdlShape shape, List<List<String>> grantedByCode) {
        return new WsdlService(shape, "", grantedByCode);
    }

    /** The provider with the given services. */
    public static FixtureSpec provided(ServiceSpec... services) {
        return provided(PROVIDER, services);
    }

    /** The given client as the provider, with the given services. */
    public static FixtureSpec provided(String providerClientId, ServiceSpec... services) {
        return new FixtureSpec(providerClientId, List.of(services));
    }

    /** The provider with the given services, with the changes applied to their descriptions. */
    public static FixtureSpec providedWith(List<DescriptionChange> changes, ServiceSpec... services) {
        return new FixtureSpec(PROVIDER, List.of(services), List.of(), changes);
    }

    /** The provider with the given services and the groups created. */
    public static FixtureSpec grouped(List<LocalGroupSpec> localGroups, List<GlobalGroupSpec> globalGroups,
                                      ServiceSpec... services) {
        return new FixtureSpec(PROVIDER, List.of(services), localGroups, List.of(), globalGroups);
    }

    /** The consumer's own subsystem as the provider, hosted on the consumer Security Server. */
    public static FixtureSpec onConsumerServer(ServiceSpec... services) {
        return new FixtureSpec(CONSUMER, List.of(services), List.of(), List.of(), List.of(), ProviderHost.SS1);
    }

    /** A REST service whose base endpoint is granted to the clients, with the endpoints created on top of it. */
    public static FixtureSpec restService(List<String> grantedConsumerIds, EndpointSpec... endpoints) {
        return provided(rest(grantedConsumerIds, endpoints));
    }

    /** A REST service whose only grant is the consumer's access to one created endpoint. */
    public static FixtureSpec endpointOnly(String method, String path) {
        return restService(List.of(), endpoint(method, path));
    }

    /** A REST service whose base endpoint is granted to the consumer, with the changes applied to its description. */
    public static FixtureSpec changed(DescriptionChange... changes) {
        return providedWith(List.of(changes), rest(CONSUMER_ONLY));
    }

    /** A REST service whose base endpoint is granted to the provider's local group, with the groups created. */
    public static FixtureSpec groupGranted(LocalGroupSpec... groups) {
        return grouped(List.of(groups), List.of(), restToGroup(GROUP));
    }

    /** A REST service whose base endpoint is granted to the fixture's global group, with the groups created. */
    public static FixtureSpec globalGroupGranted(GlobalGroupSpec... groups) {
        return grouped(List.of(), List.of(groups), restToGlobalGroup(GLOBAL_GROUP));
    }

    /** An OpenAPI description whose generated endpoint is granted to the consumer. */
    public static FixtureSpec openApiGranted(OpenApiSpec spec, String method, String path) {
        return provided(new OpenApiService(spec, List.of(),
                List.of(new GeneratedEndpointGrant(method, path, CONSUMER_ONLY)), null));
    }

    /** A WSDL description whose service codes carry no extra text. */
    public static FixtureSpec wsdl(WsdlShape shape, List<List<String>> grantedByCode) {
        return provided(wsdlService(shape, grantedByCode));
    }

    /** A WSDL description whose service codes end with the given text. */
    public static FixtureSpec wsdl(WsdlShape shape, String codeTail, List<List<String>> grantedByCode) {
        return provided(new WsdlService(shape, codeTail, grantedByCode));
    }

    /** The client registration service on the management Security Server, with the groups created. */
    public static FixtureSpec management(ManagementService service, LocalGroupSpec... groups) {
        return new FixtureSpec(MANAGEMENT, List.of(service), List.of(groups));
    }
}
