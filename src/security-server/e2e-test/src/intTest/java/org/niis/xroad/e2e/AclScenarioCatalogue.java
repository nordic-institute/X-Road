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

package org.niis.xroad.e2e;

import org.niis.xroad.e2e.parity.acl.AclScenario;
import org.niis.xroad.e2e.parity.acl.AclScenario.AddGlobalGroupMember;
import org.niis.xroad.e2e.parity.acl.AclScenario.DeleteDescription;
import org.niis.xroad.e2e.parity.acl.AclScenario.DeleteGlobalGroup;
import org.niis.xroad.e2e.parity.acl.AclScenario.DeleteLocalGroup;
import org.niis.xroad.e2e.parity.acl.AclScenario.Deleted;
import org.niis.xroad.e2e.parity.acl.AclScenario.Disable;
import org.niis.xroad.e2e.parity.acl.AclScenario.EditedTo;
import org.niis.xroad.e2e.parity.acl.AclScenario.Enable;
import org.niis.xroad.e2e.parity.acl.AclScenario.Expectation;
import org.niis.xroad.e2e.parity.acl.AclScenario.GlobalGroupSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.Grant;
import org.niis.xroad.e2e.parity.acl.AclScenario.GrantGroup;
import org.niis.xroad.e2e.parity.acl.AclScenario.ListClientsRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.LocalGroupSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.ManagementService;
import org.niis.xroad.e2e.parity.acl.AclScenario.MetaserviceRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiService;
import org.niis.xroad.e2e.parity.acl.AclScenario.Refresh;
import org.niis.xroad.e2e.parity.acl.AclScenario.RemoveGlobalGroupMember;
import org.niis.xroad.e2e.parity.acl.AclScenario.RemoveLocalGroupMember;
import org.niis.xroad.e2e.parity.acl.AclScenario.RenameService;
import org.niis.xroad.e2e.parity.acl.AclScenario.Revoke;
import org.niis.xroad.e2e.parity.acl.AclScenario.SwapBackendSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.Transport;
import org.niis.xroad.e2e.parity.acl.AclScenario.UndefinedService;
import org.niis.xroad.e2e.parity.acl.SoapRequests;

import java.util.List;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.CLIENT_REG;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.CONSUMER;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.DISABLED_NOTICE;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.GLOBAL_GROUP;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.GROUP;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.MANAGEMENT;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.MEMBER_OF_CONSUMER;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.OTHER_CONSUMER;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.OWNERS_GROUP;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.OWNER_MEMBER;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.PROVIDER;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.UNREGISTERED_SECURITY_SERVER;
import static org.niis.xroad.e2e.parity.acl.AclScenario.MetaserviceRequest.NO_SERVICE;
import static org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiSpec.PETS;
import static org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiSpec.PETS_THEN_STATUS_ONLY;
import static org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiSpec.STATUS_ONLY;
import static org.niis.xroad.e2e.parity.acl.AclScenario.WsdlShape.PAIR;
import static org.niis.xroad.e2e.parity.acl.AclScenario.WsdlShape.SINGLE;
import static org.niis.xroad.e2e.parity.acl.AclScenario.WsdlShape.SINGLE_UNVERSIONED;
import static org.niis.xroad.e2e.parity.acl.AclScenario.WsdlShape.SOAP12;
import static org.niis.xroad.e2e.parity.acl.AclScenario.WsdlShape.VERSIONS;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.CONSUMER_ONLY;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.adminGrant;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.allowed;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.changed;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.check;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.denied;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.endpoint;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.endpointOnly;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.globalGroupEndpoint;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.globalGroupGranted;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.globalconfPhased;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.globalconfRow;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.groupEndpoint;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.groupGranted;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.grouped;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.management;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.metaRest;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.metaSoap;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.monitoring;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.onConsumerServer;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.openApiGranted;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.phase;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.phased;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.provided;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.providedWith;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.registration;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.rest;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.restRequest;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.restService;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.restToGlobalGroup;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.restToGroup;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.row;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.soapRequest;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.wsdl;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.wsdlService;
import static org.niis.xroad.e2e.parity.acl.SubsystemPool.SECOND_CONSUMER;
import static org.niis.xroad.e2e.parity.acl.SubsystemPool.SECOND_OWNER;
import static org.niis.xroad.e2e.parity.acl.SubsystemPool.SIBLING_OF_CONSUMER;
import static org.niis.xroad.e2e.parity.acl.SubsystemPool.UNREGISTERED_PROVIDER;
import static org.niis.xroad.e2e.parity.acl.Verdict.ACCESS_DENIED;
import static org.niis.xroad.e2e.parity.acl.Verdict.ALLOWED;
import static org.niis.xroad.e2e.parity.acl.Verdict.CONSUMER_REJECTED;
import static org.niis.xroad.e2e.parity.acl.Verdict.INVALID_SECURITY_SERVER;
import static org.niis.xroad.e2e.parity.acl.Verdict.INVALID_SERVICE_TYPE;
import static org.niis.xroad.e2e.parity.acl.Verdict.SERVICE_DISABLED;
import static org.niis.xroad.e2e.parity.acl.Verdict.UNKNOWN_MEMBER;
import static org.niis.xroad.e2e.parity.acl.Verdict.UNKNOWN_SERVICE;

/**
 * The scenarios the parity suite runs. A scenario calls from the consumer Security Server (ss1) to the
 * provider (ss0), except one whose fixture names ss1 as the provider host.
 */
@SuppressWarnings({"checkstyle:linelength", "checkstyle:magicnumber"})
public final class AclScenarioCatalogue {

    private static final List<AclScenario> ALL = List.of(
            row("V01", restService(CONSUMER_ONLY), restRequest("GET", "/a/b"), ALLOWED).as("REST service, consumer subsystem granted the base endpoint"),
            row("V02", restService(List.of()), restRequest(OTHER_CONSUMER, "GET", "/a"), ACCESS_DENIED).as("REST service, another subsystem with no grant"),
            row("V03", wsdl(SINGLE, List.of(CONSUMER_ONLY)), soapRequest(0, "v1"), ALLOWED).as("SOAP service, consumer subsystem granted"),
            row("V04", wsdl(PAIR, List.of(CONSUMER_ONLY, List.of())), soapRequest(1, "v1"), ACCESS_DENIED).as("WSDL with two services, consumer granted the first and calls the second"),
            row("V05", restService(CONSUMER_ONLY), restRequest(SIBLING_OF_CONSUMER, "GET", "/a"), ACCESS_DENIED).as("Sibling subsystem of the granted consumer, no grant"),
            row("V06", restService(CONSUMER_ONLY), restRequest(MEMBER_OF_CONSUMER, "GET", "/a"), ACCESS_DENIED).as("Request sent as the owning member of the granted consumer"),
            row("V07", onConsumerServer(rest(List.of())), restRequest("GET", "/a"), ACCESS_DENIED).as("Provider's own subsystem calls its own service without a grant, both on ss1"),
            row("V08", restService(List.of(CONSUMER, PROVIDER)), restRequest("GET", "/a"), ALLOWED).as("Consumer and provider's own subsystem both granted, consumer calls"),
            row("V09", restService(List.of(SECOND_CONSUMER)), restRequest(SECOND_CONSUMER, "GET", "/a"), ALLOWED).as("Subsystem of the provider's member on ss1, granted"),
            phased("V10", restService(List.of()), List.of(denied("GET", "/a")), phase(new Grant(0, CONSUMER), allowed("GET", "/a"))).as("Grant added after an earlier denial"),
            phased("V11", restService(CONSUMER_ONLY, endpoint("GET", "/foo")), List.of(allowed("GET", "/a"), allowed("GET", "/foo")), phase(new Revoke(0, CONSUMER), denied("GET", "/a"), denied("GET", "/foo"))).as("Grant revoked after an earlier allow, an endpoint grant revoked with it"),
            adminGrant("V12", restService(CONSUMER_ONLY), CONSUMER, 0, HTTP_CONFLICT, "duplicate_accessright").as("Same subsystem granted twice through the admin API"),
            adminGrant("V13", restService(List.of()), MEMBER_OF_CONSUMER, 0, HTTP_BAD_REQUEST, "invalid_service_client_id").as("Member granted through the admin API"),
            row("V20", restService(CONSUMER_ONLY), allowed("GET", "/a/b/c"), allowed("POST", "/a/b/c")).as("Base endpoint granted, any method and any path"),
            row("V21", restService(CONSUMER_ONLY), allowed("GET", "")).as("Base endpoint granted, bare service root"),
            row("V22", endpointOnly("GET", "/foo"), allowed("GET", "/foo")).as("GET /foo endpoint granted, exact method and path"),
            row("V23", endpointOnly("GET", "/foo"), denied("POST", "/foo")).as("GET /foo endpoint granted, another method"),
            row("V24", endpointOnly("GET", "/foo"), denied("GET", "/foo/")).as("GET /foo endpoint granted, trailing slash"),
            row("V25", endpointOnly("GET", "/foo"), denied("GET", "/foo/bar")).as("GET /foo endpoint granted, sub-path"),
            row("V26", endpointOnly("GET", "/foo/*"), allowed("GET", "/foo/x"), allowed("GET", "/foo/"), denied("GET", "/foo/x/y"), denied("GET", "/foo")).as("GET /foo/* endpoint granted, one segment"),
            row("V27", endpointOnly("GET", "/foo/**"), allowed("GET", "/foo/x/y"), denied("GET", "/foo"), denied("GET", "")).as("GET /foo/** endpoint granted, any depth below foo"),
            row("V28", endpointOnly("*", "/**"), denied("GET", ""), allowed("GET", "/a/b"), allowed("POST", "/a/b")).as("Any method on /** endpoint granted, bare service root"),
            row("V29", endpointOnly("GET", "/*/bar"), allowed("GET", "/a/bar"), denied("GET", "/a/b/bar")).as("GET /*/bar endpoint granted, one segment before bar"),
            row("V30", endpointOnly("GET", "/a/**/c"), allowed("GET", "/a/b/c"), allowed("GET", "/a/x/y/c"), denied("GET", "/a/c")).as("GET /a/**/c endpoint granted, mid-path double wildcard"),
            row("V31", endpointOnly("*", "/foo"), allowed("GET", "/foo"), allowed("DELETE", "/foo"), denied("GET", "/bar")).as("Any method on /foo endpoint granted"),
            row("V32", endpointOnly("GET", "**"), allowed("GET", "/a"), allowed("GET", "/a/b/c"), denied("POST", "/a")).as("GET ** endpoint granted, any path"),
            row("V33", restService(CONSUMER_ONLY, endpoint("GET", "/foo")), allowed("GET", "/foo"), allowed("POST", "/bar")).as("Base endpoint and GET /foo endpoint granted to the same consumer"),
            row("V34", restService(List.of(), endpoint("GET", "/a"), endpoint("GET", "/b", List.of(OTHER_CONSUMER))), allowed(CONSUMER, "GET", "/a"), allowed(OTHER_CONSUMER, "GET", "/b"), denied(CONSUMER, "GET", "/b"), denied(OTHER_CONSUMER, "GET", "/a")).as("Each consumer granted its own endpoint, the other consumer's path"),
            row("V35", restService(List.of(), endpoint("GET", "/foo", List.of()), endpoint("GET", "/bar")), allowed("GET", "/bar"), denied("GET", "/foo")).as("Endpoint without a grant and no base grant"),
            row("V36", restService(List.of(), endpoint("GET", "/foo", new Deleted()), endpoint("GET", "/bar")), allowed("GET", "/bar"), denied("GET", "/foo")).as("Granted manual endpoint deleted"),
            row("V37", restService(List.of(), endpoint("GET", "/foo", new EditedTo("GET", "/bar"))), allowed("GET", "/bar"), denied("GET", "/foo")).as("Granted manual endpoint edited from /foo to /bar"),
            row("V38", openApiGranted(PETS, "GET", "/pets/*"), allowed("GET", "/pets/123"), denied("GET", "/pets/1/x")).as("OpenAPI GET /pets/{petId} granted, stored as /pets/*"),
            phased("V39", openApiGranted(PETS_THEN_STATUS_ONLY, "GET", "/pets/*"), List.of(allowed("GET", "/pets/123")), phase(List.of(new SwapBackendSpec(0), new Refresh(0)), denied("GET", "/pets/123"))).as("OpenAPI refresh removes a granted endpoint"),
            row("V40", provided(new OpenApiService(STATUS_ONLY, List.of(endpoint("GET", "/pets/*")), List.of(), PETS)), allowed("GET", "/pets/123")).as("Granted manual endpoint equal to one a description edit then generates"),
            row("V41", endpointOnly("*", "/v1.0/x"), allowed("GET", "/v1.0/x"), denied("GET", "/v1x0/x")).as("Endpoint with a dot in its path, a different character in the dot's place"),
            row("V42", endpointOnly("GET", "/foo"), allowed("GET", "/foo?x=1")).as("GET /foo endpoint granted, request with a query string"),
            row("V43", endpointOnly("*", "/foo"), allowed("GET", "/foo"), denied("GET", "/Foo")).as("Endpoint /foo granted, request path in another letter case"),
            row("V44", endpointOnly("*", "/api/test/**"), allowed("GET", "/api/t%65st/foo")).as("Endpoint /api/test/** granted, a letter of the request path percent-encoded"),
            row("V45", endpointOnly("*", "/api/**"), allowed("GET", "/api/x"), check(restRequest("GET", "/api/%2e%2e/secret"), CONSUMER_REJECTED), denied("GET", "/../x")).as("Endpoint /api/** granted, request paths climbing out with dot segments"),
            row("V46", endpointOnly("*", "/api/test/**"), allowed("GET", "/api/test/foo/../bar")).as("Endpoint /api/test/** granted, request path with a dot segment inside the grant"),
            row("V47", endpointOnly("*", "/api/test-bar"), allowed("GET", "/api/test%2Dbar")).as("Endpoint /api/test-bar granted, the hyphen percent-encoded"),
            row("V48", endpointOnly("*", "/foo/*"), allowed("GET", "/foo/x"), allowed("GET", "/foo%2Fbar")).as("Endpoint /foo/* granted, an encoded slash inside the segment"),
            row("V49", endpointOnly("*", "/public"), denied("GET", "/public%3Fx")).as("Endpoint /public granted, request path with a percent-encoded character"),
            row("V50", restService(CONSUMER_ONLY), allowed("GET", "/files/my-file.pdf"), check(restRequest("GET", "/files/my%20file.pdf"), ACCESS_DENIED)).as("Any grant on the base endpoint, request path with an encoded space"),
            row("V51", endpointOnly("*", "/foo?"), allowed("GET", "/foo"), allowed("GET", "/fo")).as("Endpoint /foo? granted, the question mark read as an optional character"),
            row("V52", restService(List.of(), endpoint("*", "/any"), endpoint("HEAD", "/head"), endpoint("OPTIONS", "/options"), endpoint("TRACE", "/trace"), endpoint("PATCH", "/patch")), allowed("HEAD", "/any"), allowed("OPTIONS", "/any"), allowed("TRACE", "/any"), allowed("PATCH", "/any"), allowed("HEAD", "/head"), allowed("OPTIONS", "/options"), allowed("TRACE", "/trace"), allowed("PATCH", "/patch"), denied("PATCH", "/head")).as("Methods HEAD, OPTIONS, TRACE and PATCH against any-method and specific endpoints"),
            row("V53", endpointOnly("GET", "/api/items/*"), denied("GET", "/api/items/1%3F/../../other")).as("GET /api/items/* endpoint granted, request path with an encoded character and dot segments"),
            row("V54", restService(CONSUMER_ONLY), denied("GET", "/%2F../other")).as("Any grant on the base endpoint, request path with an encoded character and a dot segment"),
            row("V55", restService(List.of(), endpoint("GET", "/files/*.json"), endpoint("GET", "/files/ok")), allowed("GET", "/files/ok"), denied("GET", "/files/a.json")).as("GET /files/*.json endpoint granted, text after the wildcard in one segment"),
            row("V60", groupGranted(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY)), allowed("GET", "/a")).as("Local group granted the base endpoint, consumer is a member"),
            row("V61", groupGranted(new LocalGroupSpec(PROVIDER, GROUP, List.of(OTHER_CONSUMER))), denied("GET", "/a")).as("Local group granted the base endpoint, consumer is not a member"),
            phased("V62", groupGranted(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY)), List.of(allowed("GET", "/a")), phase(new RemoveLocalGroupMember(GROUP, CONSUMER), denied("GET", "/a"))).as("Local group granted the base endpoint, consumer removed from the group"),
            phased("V63", groupGranted(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY)), List.of(allowed("GET", "/a")), phase(new DeleteLocalGroup(GROUP), denied("GET", "/a"))).as("Local group granted the base endpoint, group deleted"),
            row("V64", groupGranted(new LocalGroupSpec(PROVIDER, GROUP, List.of(MEMBER_OF_CONSUMER))), allowed(MEMBER_OF_CONSUMER, "GET", "/a")).as("Local group with a member id, request sent as that member"),
            row("V65", groupGranted(new LocalGroupSpec(PROVIDER, GROUP, List.of(OTHER_CONSUMER)), new LocalGroupSpec(SECOND_OWNER, GROUP, CONSUMER_ONLY)), denied("GET", "/a")).as("Two owners each have group partners, consumer only in the other owner's"),
            row("V66", grouped(List.of(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY)), List.of(), rest(List.of(), endpoint("GET", "/a"), groupEndpoint("GET", "/b", GROUP))), allowed("GET", "/a"), allowed("GET", "/b")).as("Direct grant on GET /a plus local group grant on GET /b"),
            row("V67", grouped(List.of(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY)), List.of(), restToGroup(GROUP), rest(List.of())), allowed("GET", "/a"), check(restRequest(1, "GET", "/a"), ACCESS_DENIED)).as("Local group granted on the first service only, second service called"),
            globalconfRow("V68", globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP, CONSUMER_ONLY)), allowed("GET", "/a")).as("Global group granted the base endpoint, consumer subsystem is a member"),
            globalconfRow("V69", globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP, List.of(MEMBER_OF_CONSUMER))), allowed(MEMBER_OF_CONSUMER, "GET", "/a"), denied("GET", "/a")).as("Global group with the member only, the member's subsystem calls"),
            globalconfRow("V71", globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP, List.of(MEMBER_OF_CONSUMER, CONSUMER))), allowed("GET", "/a"), allowed(MEMBER_OF_CONSUMER, "GET", "/a"), denied(SIBLING_OF_CONSUMER, "GET", "/a")).as("Global group with a member and a subsystem, a sibling subsystem calls"),
            globalconfRow("V72", provided(restToGlobalGroup(GlobalGroupSpec.SECURITY_SERVER_OWNERS)), allowed(MEMBER_OF_CONSUMER, "GET", "/a"), denied(OTHER_CONSUMER, "GET", "/a")).as("Group security-server-owners granted, an owner and a non-owner call"),
            globalconfRow("V73", globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP)), denied("GET", "/a")).as("Empty global group granted the base endpoint"),
            globalconfPhased("V74", globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP, CONSUMER_ONLY)), List.of(allowed("GET", "/a")), phase(new DeleteGlobalGroup(GLOBAL_GROUP), denied("GET", "/a"))).as("Global group deleted at the Central Server after the grant"),
            globalconfPhased("V75", globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP)), List.of(denied("GET", "/a")), phase(new AddGlobalGroupMember(GLOBAL_GROUP, CONSUMER), allowed("GET", "/a")), phase(new RemoveGlobalGroupMember(GLOBAL_GROUP, CONSUMER), denied("GET", "/a"))).as("Member added to and removed from a global group at the Central Server"),
            globalconfRow("V77", grouped(List.of(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY)), List.of(new GlobalGroupSpec(GLOBAL_GROUP, CONSUMER_ONLY)), rest(List.of(), groupEndpoint("GET", "/a", GROUP), globalGroupEndpoint("GET", "/b", GLOBAL_GROUP))), allowed("GET", "/a"), allowed("GET", "/b")).as("Local group grant on GET /a plus global group grant on GET /b"),
            row("V90", providedWith(List.of(new Disable(0, null), new Disable(2, null)), wsdlService(SINGLE, List.of(List.of())), rest(List.of()), rest(List.of())), allowed(metaSoap("listMethods", NO_SERVICE, Expectation.listing(List.of(0), List.of(1, 2)))), allowed(metaRest("listMethods", NO_SERVICE, Expectation.listing(List.of(1, 2), List.of(0))))).as("listMethods needs no grant and lists disabled services too"),
            row("V91", provided(wsdlService(SINGLE, List.of(CONSUMER_ONLY)), wsdlService(SINGLE, List.of(List.of())), rest(List.of(), endpoint("GET", "/foo")), rest(List.of())), allowed(metaSoap("allowedMethods", NO_SERVICE, Expectation.listing(List.of(0), List.of(1)))), allowed(metaRest("allowedMethods", NO_SERVICE, new Expectation(List.of("/foo"), List.of("**"), List.of(2), List.of(3))))).as("allowedMethods lists only the services and endpoints the caller is granted"),
            row("V92", provided(wsdlService(SINGLE, List.of(List.of())), rest(List.of()), new UndefinedService()), allowed(metaSoap("getWsdl", 0, "v1", Expectation.listing(List.of(0), List.of()))), check(metaSoap("getWsdl", 1, Expectation.NONE), INVALID_SERVICE_TYPE), check(metaSoap("getWsdl", 2, Expectation.NONE), UNKNOWN_SERVICE)).as("getWsdl of a WSDL service, a REST service and a service no description defines"),
            row("V93", providedWith(List.of(new Disable(0, null)), new OpenApiService(STATUS_ONLY, List.of(), List.of(), null)), allowed(metaRest("getOpenAPI", 0, Expectation.carrying("/status")))).as("getOpenAPI needs no grant and ignores a disabled description"),
            row("V94", restService(List.of()), check(new MetaserviceRequest(CONSUMER, Transport.REST, "listMethods", "POST", NO_SERVICE, null, Expectation.NONE), UNKNOWN_SERVICE)).as("REST metaservice code called with POST is an unknown service"),
            row("V96", provided(OWNER_MEMBER), check(monitoring(MEMBER_OF_CONSUMER, SoapRequests.METRICS, null, Expectation.NONE), ACCESS_DENIED), check(monitoring(CONSUMER, SoapRequests.METRICS, null, Expectation.NONE), ACCESS_DENIED)).as("getSecurityServerMetrics is refused to a caller that is neither owner nor monitoring client"),
            row("V97", provided(OWNER_MEMBER), allowed(monitoring(CONSUMER, SoapRequests.HEALTH_DATA, CONSUMER, new Expectation(List.of("getSecurityServerHealthDataResponse", "statisticsPeriodSeconds"), List.of("serviceEvents"), List.of(), List.of()))), allowed(monitoring(CONSUMER, SoapRequests.OPERATIONAL_DATA, null, Expectation.carrying("getSecurityServerOperationalDataResponse")))).as("Health data and operational data of the owner's Security Server need no grant"),
            row("V98", provided(OWNER_MEMBER), allowed(new ListClientsRequest(Expectation.carrying("1234", "4321", "Test member", "Test client")))).as("listClients answers without a client header and lists the members of the instance"),
            row("V99", wsdl(SOAP12, List.of(List.of())), allowed(metaSoap("getWsdl", 0, "v1", Expectation.masked("example.org/xroad-endpoint", "isrest:8080")))).as("getWsdl of a WSDL with a SOAP 1.2 address shows the Security Server's address"),
            row("V100", management(new ManagementService(CLIENT_REG, List.of(), List.of())), registration(ALLOWED)).as("Management service granted to the Security Server owners, owner registers a client"),
            row("V101", management(new ManagementService(CLIENT_REG, List.of(OWNERS_GROUP), List.of())), registration(ACCESS_DENIED)).as("Management service not granted to the Security Server owners"),
            phased("V106a", management(new ManagementService(CLIENT_REG, List.of(), List.of())), List.of(registration(ALLOWED)), phase(List.of(new Revoke(0, OWNERS_GROUP), new Grant(0, CONSUMER)), registration(ACCESS_DENIED))).as("Management service moved from the owners group to the owner's subsystem"),
            phased("V106b", management(new ManagementService(CLIENT_REG, List.of(OWNERS_GROUP), List.of()), new LocalGroupSpec(MANAGEMENT, GROUP, List.of(MEMBER_OF_CONSUMER))), List.of(registration(ACCESS_DENIED)), phase(new GrantGroup(0, GROUP), registration(ALLOWED))).as("Management service granted to a local group that lists the owner"),
            row("V120", changed(new Disable(0, DISABLED_NOTICE)), check(restRequest("GET", "/a"), SERVICE_DISABLED, DISABLED_NOTICE)).as("Granted service disabled with a notice"),
            row("V121", changed(new Disable(0, DISABLED_NOTICE)), denied(OTHER_CONSUMER, "GET", "/a")).as("Service disabled, caller without a grant"),
            row("V122", providedWith(List.of(new Disable(0, DISABLED_NOTICE)), wsdlService(PAIR, List.of(CONSUMER_ONLY, CONSUMER_ONLY))), check(soapRequest(0, "v1"), SERVICE_DISABLED), check(soapRequest(1, "v1"), SERVICE_DISABLED)).as("WSDL with two services, both granted, the description disabled"),
            row("V123", changed(new Disable(0, null)), check(restRequest("GET", "/a"), SERVICE_DISABLED)).as("Granted service disabled, notice not supplied"),
            row("V124", changed(new Disable(0, DISABLED_NOTICE), new Enable(0)), allowed("GET", "/a")).as("Service disabled and enabled again"),
            phased("V125", restService(CONSUMER_ONLY), List.of(allowed("GET", "/a")), phase(new DeleteDescription(0), check(restRequest("GET", "/a"), UNKNOWN_SERVICE))).as("Service description deleted, its grants removed with it"),
            row("V127", changed(new RenameService(0)), check(restRequest("GET", "/a"), UNKNOWN_SERVICE).inDsp(ACCESS_DENIED), allowed(1, "GET", "/a")).as("REST service code renamed, the old code and the new code called"),
            row("V128", wsdl(VERSIONS, List.of(CONSUMER_ONLY)), soapRequest(0, "v2"), ALLOWED).as("SOAP service in two versions, the second version called"),
            row("V129", wsdl(SINGLE, ".x", List.of(CONSUMER_ONLY)), soapRequest(0, "v1"), ALLOWED).as("SOAP service code containing a dot"),
            row("V130", provided(UNREGISTERED_PROVIDER, rest(CONSUMER_ONLY)), restRequest("GET", "/a"), UNKNOWN_MEMBER).as("Provider client registered on its Security Server only as saved"),
            row("V131", wsdl(SINGLE, List.of(CONSUMER_ONLY)), soapRequest(0, "v1", UNREGISTERED_SECURITY_SERVER), INVALID_SECURITY_SERVER).as("SOAP header naming a Security Server that does not provide the service"),
            row("V132a", wsdl(SINGLE_UNVERSIONED, List.of(CONSUMER_ONLY)), restRequest("GET", "/a"), INVALID_SERVICE_TYPE).as("REST call to a SOAP service"),
            row("V132b", restService(CONSUMER_ONLY), soapRequest(0, null), INVALID_SERVICE_TYPE).as("SOAP call to a REST service"),
            row("V133", provided(new UndefinedService()), check(restRequest("GET", "/a"), UNKNOWN_SERVICE).inDsp(ACCESS_DENIED), check(soapRequest(0, "v1"), UNKNOWN_SERVICE).inDsp(ACCESS_DENIED)).as("Service code that no description defines, called over REST and SOAP"));

    private AclScenarioCatalogue() {
    }

    public static List<AclScenario> all() {
        return ALL;
    }
}
