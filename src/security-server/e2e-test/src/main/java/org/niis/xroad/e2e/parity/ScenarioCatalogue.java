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

import org.niis.xroad.e2e.parity.Scenario.Check;
import org.niis.xroad.e2e.parity.Scenario.Delete;
import org.niis.xroad.e2e.parity.Scenario.Deleted;
import org.niis.xroad.e2e.parity.Scenario.DescriptionChange;
import org.niis.xroad.e2e.parity.Scenario.Disable;
import org.niis.xroad.e2e.parity.Scenario.EditedTo;
import org.niis.xroad.e2e.parity.Scenario.Enable;
import org.niis.xroad.e2e.parity.Scenario.EndpointSpec;
import org.niis.xroad.e2e.parity.Scenario.FixtureSpec;
import org.niis.xroad.e2e.parity.Scenario.GeneratedEndpointGrant;
import org.niis.xroad.e2e.parity.Scenario.LocalGroupSpec;
import org.niis.xroad.e2e.parity.Scenario.OpenApiService;
import org.niis.xroad.e2e.parity.Scenario.OpenApiSpec;
import org.niis.xroad.e2e.parity.Scenario.RenameService;
import org.niis.xroad.e2e.parity.Scenario.RestRequest;
import org.niis.xroad.e2e.parity.Scenario.RestService;
import org.niis.xroad.e2e.parity.Scenario.SoapRequest;
import org.niis.xroad.e2e.parity.Scenario.UndefinedService;
import org.niis.xroad.e2e.parity.Scenario.WaitClass;
import org.niis.xroad.e2e.parity.Scenario.WsdlService;
import org.niis.xroad.e2e.parity.Scenario.WsdlShape;

import java.util.List;

/**
 * The scenarios the parity suite runs. Every scenario calls from the consumer Security Server (ss1) to the
 * provider (ss0).
 */
public final class ScenarioCatalogue {

    public static final String PROVIDER = "DEV:COM:1234:TestService";
    public static final String CONSUMER = "DEV:COM:4321:TestClient";
    public static final String OTHER_CONSUMER = "DEV:COM:1234:test-consumer";
    /** The member of the consumer subsystem, calling without a subsystem code. */
    public static final String MEMBER_OF_CONSUMER = "DEV:COM:4321";
    /** A Security Server code that no member of the instance registers. */
    public static final String UNREGISTERED_SECURITY_SERVER = "DEV:COM:1234:acl-parity-unregistered";
    /** The text the rows that disable a service with a notice expect back. */
    public static final String DISABLED_NOTICE = "Scheduled maintenance, please retry later";

    private static final String V1 = "v1";
    private static final String GROUP = "partners";
    private static final List<String> CONSUMER_ONLY = List.of(CONSUMER);

    private static final List<Scenario> ALL = List.of(
            new Scenario("V01", "REST service, consumer subsystem granted the base endpoint",
                    new FixtureSpec(PROVIDER, List.of(new RestService(CONSUMER_ONLY))),
                    new RestRequest(CONSUMER, 0, "GET", "/a/b"),
                    Verdict.ALLOWED, WaitClass.ACL_CACHE),
            new Scenario("V02", "REST service, another subsystem with no grant",
                    new FixtureSpec(PROVIDER, List.of(new RestService(List.of()))),
                    new RestRequest(OTHER_CONSUMER, 0, "GET", "/a"),
                    Verdict.ACCESS_DENIED, WaitClass.ACL_CACHE),
            new Scenario("V03", "SOAP service, consumer subsystem granted",
                    wsdl(WsdlShape.SINGLE, "", List.of(CONSUMER_ONLY)),
                    new SoapRequest(CONSUMER, 0, V1, null),
                    Verdict.ALLOWED, WaitClass.ACL_CACHE),
            new Scenario("V04", "WSDL with two services, consumer granted the first and calls the second",
                    wsdl(WsdlShape.PAIR, "", List.of(CONSUMER_ONLY, List.of())),
                    new SoapRequest(CONSUMER, 1, V1, null),
                    Verdict.ACCESS_DENIED, WaitClass.ACL_CACHE),
            new Scenario("V20", "Base endpoint granted, any method and any path",
                    new FixtureSpec(PROVIDER, List.of(new RestService(CONSUMER_ONLY))),
                    List.of(allowed("GET", "/a/b/c"), allowed("POST", "/a/b/c")), WaitClass.ACL_CACHE),
            new Scenario("V21", "Base endpoint granted, bare service root",
                    new FixtureSpec(PROVIDER, List.of(new RestService(CONSUMER_ONLY))),
                    List.of(allowed("GET", "")), WaitClass.ACL_CACHE),
            new Scenario("V22", "GET /foo endpoint granted, exact method and path",
                    endpointOnly("GET", "/foo"),
                    List.of(allowed("GET", "/foo")), WaitClass.ACL_CACHE),
            new Scenario("V23", "GET /foo endpoint granted, another method",
                    endpointOnly("GET", "/foo"),
                    List.of(denied("POST", "/foo")), WaitClass.ACL_CACHE),
            new Scenario("V24", "GET /foo endpoint granted, trailing slash",
                    endpointOnly("GET", "/foo"),
                    List.of(denied("GET", "/foo/")), WaitClass.ACL_CACHE),
            new Scenario("V25", "GET /foo endpoint granted, sub-path",
                    endpointOnly("GET", "/foo"),
                    List.of(denied("GET", "/foo/bar")), WaitClass.ACL_CACHE),
            new Scenario("V26", "GET /foo/* endpoint granted, one segment",
                    endpointOnly("GET", "/foo/*"),
                    List.of(allowed("GET", "/foo/x"), allowed("GET", "/foo/"), denied("GET", "/foo/x/y"),
                            denied("GET", "/foo")), WaitClass.ACL_CACHE),
            new Scenario("V27", "GET /foo/** endpoint granted, any depth below foo",
                    endpointOnly("GET", "/foo/**"),
                    List.of(allowed("GET", "/foo/x/y"), denied("GET", "/foo"), denied("GET", "")),
                    WaitClass.ACL_CACHE),
            new Scenario("V28", "Any method on /** endpoint granted, bare service root",
                    endpointOnly("*", "/**"),
                    List.of(denied("GET", ""), allowed("GET", "/a/b"), allowed("POST", "/a/b")),
                    WaitClass.ACL_CACHE),
            new Scenario("V29", "GET /*/bar endpoint granted, one segment before bar",
                    endpointOnly("GET", "/*/bar"),
                    List.of(allowed("GET", "/a/bar"), denied("GET", "/a/b/bar")), WaitClass.ACL_CACHE),
            new Scenario("V30", "GET /a/**/c endpoint granted, mid-path double wildcard",
                    endpointOnly("GET", "/a/**/c"),
                    List.of(allowed("GET", "/a/b/c"), allowed("GET", "/a/x/y/c"), denied("GET", "/a/c")),
                    WaitClass.ACL_CACHE),
            new Scenario("V31", "Any method on /foo endpoint granted",
                    endpointOnly("*", "/foo"),
                    List.of(allowed("GET", "/foo"), allowed("DELETE", "/foo"), denied("GET", "/bar")),
                    WaitClass.ACL_CACHE),
            new Scenario("V32", "GET ** endpoint granted, any path",
                    endpointOnly("GET", "**"),
                    List.of(allowed("GET", "/a"), allowed("GET", "/a/b/c"), denied("POST", "/a")),
                    WaitClass.ACL_CACHE),
            new Scenario("V33", "Base endpoint and GET /foo endpoint granted to the same consumer",
                    new FixtureSpec(PROVIDER, List.of(new RestService(CONSUMER_ONLY,
                            List.of(new EndpointSpec("GET", "/foo", CONSUMER_ONLY))))),
                    List.of(allowed("GET", "/foo"), allowed("POST", "/bar")), WaitClass.ACL_CACHE),
            new Scenario("V34", "Each consumer granted its own endpoint, the other consumer's path",
                    new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(
                            new EndpointSpec("GET", "/a", CONSUMER_ONLY),
                            new EndpointSpec("GET", "/b", List.of(OTHER_CONSUMER)))))),
                    List.of(allowed(CONSUMER, "GET", "/a"), allowed(OTHER_CONSUMER, "GET", "/b"),
                            denied(CONSUMER, "GET", "/b"), denied(OTHER_CONSUMER, "GET", "/a")),
                    WaitClass.ACL_CACHE),
            new Scenario("V35", "Endpoint without a grant and no base grant",
                    new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(
                            new EndpointSpec("GET", "/foo", List.of()),
                            new EndpointSpec("GET", "/bar", CONSUMER_ONLY))))),
                    List.of(allowed("GET", "/bar"), denied("GET", "/foo")), WaitClass.ACL_CACHE),
            new Scenario("V36", "Granted manual endpoint deleted",
                    new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(
                            new EndpointSpec("GET", "/foo", CONSUMER_ONLY, new Deleted()),
                            new EndpointSpec("GET", "/bar", CONSUMER_ONLY))))),
                    List.of(allowed("GET", "/bar"), denied("GET", "/foo")), WaitClass.ACL_CACHE),
            new Scenario("V37", "Granted manual endpoint edited from /foo to /bar",
                    new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(
                            new EndpointSpec("GET", "/foo", CONSUMER_ONLY, new EditedTo("GET", "/bar")))))),
                    List.of(allowed("GET", "/bar"), denied("GET", "/foo")), WaitClass.ACL_CACHE),
            new Scenario("V38", "OpenAPI GET /pets/{petId} granted, stored as /pets/*",
                    new FixtureSpec(PROVIDER, List.of(new OpenApiService(OpenApiSpec.PETS, List.of(),
                            List.of(new GeneratedEndpointGrant("GET", "/pets/*", CONSUMER_ONLY)), null))),
                    List.of(allowed("GET", "/pets/123"), denied("GET", "/pets/1/x")), WaitClass.ACL_CACHE),
            new Scenario("V40", "Granted manual endpoint equal to one a description edit then generates",
                    new FixtureSpec(PROVIDER, List.of(new OpenApiService(OpenApiSpec.STATUS_ONLY,
                            List.of(new EndpointSpec("GET", "/pets/*", CONSUMER_ONLY)), List.of(),
                            OpenApiSpec.PETS))),
                    List.of(allowed("GET", "/pets/123")), WaitClass.ACL_CACHE),
            new Scenario("V41", "Endpoint with a dot in its path, a different character in the dot's place",
                    endpointOnly("*", "/v1.0/x"),
                    List.of(allowed("GET", "/v1.0/x"), denied("GET", "/v1x0/x")), WaitClass.ACL_CACHE),
            new Scenario("V42", "GET /foo endpoint granted, request with a query string",
                    endpointOnly("GET", "/foo"),
                    List.of(allowed("GET", "/foo?x=1")), WaitClass.ACL_CACHE),
            new Scenario("V43", "Endpoint /foo granted, request path in another letter case",
                    endpointOnly("*", "/foo"),
                    List.of(allowed("GET", "/foo"), denied("GET", "/Foo")), WaitClass.ACL_CACHE),
            new Scenario("V44", "Endpoint /api/test/** granted, a letter of the request path percent-encoded",
                    endpointOnly("*", "/api/test/**"),
                    List.of(allowed("GET", "/api/t%65st/foo")), WaitClass.ACL_CACHE),
            new Scenario("V45", "Endpoint /api/** granted, request paths climbing out with dot segments",
                    endpointOnly("*", "/api/**"),
                    List.of(allowed("GET", "/api/x"),
                            new Check(new RestRequest(CONSUMER, 0, "GET", "/api/%2e%2e/secret"),
                                    Verdict.CONSUMER_REJECTED),
                            denied("GET", "/../x")), WaitClass.ACL_CACHE),
            new Scenario("V46", "Endpoint /api/test/** granted, request path with a dot segment inside the grant",
                    endpointOnly("*", "/api/test/**"),
                    List.of(allowed("GET", "/api/test/foo/../bar")), WaitClass.ACL_CACHE),
            new Scenario("V47", "Endpoint /api/test-bar granted, the hyphen percent-encoded",
                    endpointOnly("*", "/api/test-bar"),
                    List.of(allowed("GET", "/api/test%2Dbar")), WaitClass.ACL_CACHE),
            new Scenario("V48", "Endpoint /foo/* granted, an encoded slash inside the segment",
                    endpointOnly("*", "/foo/*"),
                    List.of(allowed("GET", "/foo/x"), allowed("GET", "/foo%2Fbar")), WaitClass.ACL_CACHE),
            new Scenario("V49", "Endpoint /public granted, request path with a percent-encoded character",
                    endpointOnly("*", "/public"),
                    List.of(denied("GET", "/public%3Fx")), WaitClass.ACL_CACHE),
            new Scenario("V50", "Any grant on the base endpoint, request path with an encoded space",
                    new FixtureSpec(PROVIDER, List.of(new RestService(CONSUMER_ONLY))),
                    List.of(allowed("GET", "/files/my-file.pdf"),
                            new Check(new RestRequest(CONSUMER, 0, "GET", "/files/my%20file.pdf"),
                                    Verdict.ACCESS_DENIED)),
                    WaitClass.ACL_CACHE),
            new Scenario("V51", "Endpoint /foo? granted, the question mark read as an optional character",
                    endpointOnly("*", "/foo?"),
                    List.of(allowed("GET", "/foo"), allowed("GET", "/fo")), WaitClass.ACL_CACHE),
            new Scenario("V52", "Methods HEAD, OPTIONS, TRACE and PATCH against any-method and specific endpoints",
                    new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(
                            new EndpointSpec("*", "/any", CONSUMER_ONLY),
                            new EndpointSpec("HEAD", "/head", CONSUMER_ONLY),
                            new EndpointSpec("OPTIONS", "/options", CONSUMER_ONLY),
                            new EndpointSpec("TRACE", "/trace", CONSUMER_ONLY),
                            new EndpointSpec("PATCH", "/patch", CONSUMER_ONLY))))),
                    List.of(allowed("HEAD", "/any"), allowed("OPTIONS", "/any"), allowed("TRACE", "/any"),
                            allowed("PATCH", "/any"), allowed("HEAD", "/head"), allowed("OPTIONS", "/options"),
                            allowed("TRACE", "/trace"), allowed("PATCH", "/patch"), denied("PATCH", "/head")),
                    WaitClass.ACL_CACHE),
            new Scenario("V53", "GET /api/items/* endpoint granted, request path with an encoded character and "
                    + "dot segments",
                    endpointOnly("GET", "/api/items/*"),
                    List.of(denied("GET", "/api/items/1%3F/../../other")), WaitClass.ACL_CACHE),
            new Scenario("V54", "Any grant on the base endpoint, request path with an encoded character and a "
                    + "dot segment",
                    new FixtureSpec(PROVIDER, List.of(new RestService(CONSUMER_ONLY))),
                    List.of(denied("GET", "/%2F../other")), WaitClass.ACL_CACHE),
            new Scenario("V55", "GET /files/*.json endpoint granted, text after the wildcard in one segment",
                    new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(
                            new EndpointSpec("GET", "/files/*.json", CONSUMER_ONLY),
                            new EndpointSpec("GET", "/files/ok", CONSUMER_ONLY))))),
                    List.of(allowed("GET", "/files/ok"), denied("GET", "/files/a.json")), WaitClass.ACL_CACHE),
            new Scenario("V60", "Local group granted the base endpoint, consumer is a member",
                    groupGranted(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY)),
                    List.of(allowed("GET", "/a")), WaitClass.ACL_CACHE),
            new Scenario("V61", "Local group granted the base endpoint, consumer is not a member",
                    groupGranted(new LocalGroupSpec(PROVIDER, GROUP, List.of(OTHER_CONSUMER))),
                    List.of(denied("GET", "/a")), WaitClass.ACL_CACHE),
            new Scenario("V62", "Local group granted the base endpoint, consumer removed from the group",
                    groupGranted(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY, CONSUMER_ONLY, false)),
                    List.of(denied("GET", "/a")), WaitClass.ACL_CACHE),
            new Scenario("V63", "Local group granted the base endpoint, group deleted",
                    groupGranted(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY, List.of(), true)),
                    List.of(denied("GET", "/a")), WaitClass.ACL_CACHE),
            new Scenario("V64", "Local group with a member id, request sent as that member",
                    groupGranted(new LocalGroupSpec(PROVIDER, GROUP, List.of(MEMBER_OF_CONSUMER))),
                    List.of(allowed(MEMBER_OF_CONSUMER, "GET", "/a")), WaitClass.ACL_CACHE),
            new Scenario("V65", "Two owners each have group partners, consumer only in the other owner's",
                    groupGranted(new LocalGroupSpec(PROVIDER, GROUP, List.of(OTHER_CONSUMER)),
                            new LocalGroupSpec(SubsystemPool.SECOND_OWNER, GROUP, CONSUMER_ONLY)),
                    List.of(denied("GET", "/a")), WaitClass.ACL_CACHE),
            new Scenario("V66", "Direct grant on GET /a plus local group grant on GET /b",
                    new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(
                            new EndpointSpec("GET", "/a", CONSUMER_ONLY),
                            new EndpointSpec("GET", "/b", List.of(), List.of(GROUP))))),
                            List.of(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY))),
                    List.of(allowed("GET", "/a"), allowed("GET", "/b")), WaitClass.ACL_CACHE),
            new Scenario("V67", "Local group granted on the first service only, second service called",
                    new FixtureSpec(PROVIDER, List.of(
                            new RestService(List.of(), List.of(), List.of(GROUP)),
                            new RestService(List.of())),
                            List.of(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY))),
                    List.of(allowed("GET", "/a"),
                            new Check(new RestRequest(CONSUMER, 1, "GET", "/a"), Verdict.ACCESS_DENIED)),
                    WaitClass.ACL_CACHE),
            new Scenario("V120", "Granted service disabled with a notice",
                    changed(new Disable(0, DISABLED_NOTICE)),
                    List.of(new Check(new RestRequest(CONSUMER, 0, "GET", "/a"), Verdict.SERVICE_DISABLED,
                            DISABLED_NOTICE)),
                    WaitClass.ACL_CACHE),
            new Scenario("V121", "Service disabled, caller without a grant",
                    changed(new Disable(0, DISABLED_NOTICE)),
                    List.of(denied(OTHER_CONSUMER, "GET", "/a")), WaitClass.ACL_CACHE),
            new Scenario("V122", "WSDL with two services, both granted, the description disabled",
                    new FixtureSpec(PROVIDER, List.of(new WsdlService(WsdlShape.PAIR, "",
                            List.of(CONSUMER_ONLY, CONSUMER_ONLY))), List.of(),
                            List.of(new Disable(0, DISABLED_NOTICE))),
                    List.of(new Check(new SoapRequest(CONSUMER, 0, V1, null), Verdict.SERVICE_DISABLED),
                            new Check(new SoapRequest(CONSUMER, 1, V1, null), Verdict.SERVICE_DISABLED)),
                    WaitClass.ACL_CACHE),
            new Scenario("V123", "Granted service disabled, notice not supplied",
                    changed(new Disable(0, null)),
                    List.of(new Check(new RestRequest(CONSUMER, 0, "GET", "/a"), Verdict.SERVICE_DISABLED)),
                    WaitClass.ACL_CACHE),
            new Scenario("V124", "Service disabled and enabled again",
                    changed(new Disable(0, DISABLED_NOTICE), new Enable(0)),
                    List.of(allowed("GET", "/a")), WaitClass.ACL_CACHE),
            new Scenario("V125", "Service description deleted, its grants removed with it",
                    changed(new Delete(0)),
                    List.of(new Check(new RestRequest(CONSUMER, 0, "GET", "/a"), Verdict.UNKNOWN_SERVICE)),
                    WaitClass.ACL_CACHE),
            new Scenario("V127", "REST service code renamed, the old code and the new code called",
                    changed(new RenameService(0)),
                    List.of(new Check(new RestRequest(CONSUMER, 0, "GET", "/a"), Verdict.UNKNOWN_SERVICE),
                            allowed(1, "GET", "/a")),
                    WaitClass.ACL_CACHE),
            new Scenario("V128", "SOAP service in two versions, the second version called",
                    wsdl(WsdlShape.VERSIONS, "", List.of(CONSUMER_ONLY)),
                    new SoapRequest(CONSUMER, 0, "v2", null),
                    Verdict.ALLOWED, WaitClass.ACL_CACHE),
            new Scenario("V129", "SOAP service code containing a dot",
                    wsdl(WsdlShape.SINGLE, ".x", List.of(CONSUMER_ONLY)),
                    new SoapRequest(CONSUMER, 0, V1, null),
                    Verdict.ALLOWED, WaitClass.ACL_CACHE),
            new Scenario("V131", "SOAP header naming a Security Server that does not provide the service",
                    wsdl(WsdlShape.SINGLE, "", List.of(CONSUMER_ONLY)),
                    new SoapRequest(CONSUMER, 0, V1, UNREGISTERED_SECURITY_SERVER),
                    Verdict.INVALID_SECURITY_SERVER, WaitClass.ACL_CACHE),
            new Scenario("V132a", "REST call to a SOAP service",
                    wsdl(WsdlShape.SINGLE_UNVERSIONED, "", List.of(CONSUMER_ONLY)),
                    new RestRequest(CONSUMER, 0, "GET", "/a"),
                    Verdict.INVALID_SERVICE_TYPE, WaitClass.ACL_CACHE),
            new Scenario("V132b", "SOAP call to a REST service",
                    new FixtureSpec(PROVIDER, List.of(new RestService(CONSUMER_ONLY))),
                    new SoapRequest(CONSUMER, 0, null, null),
                    Verdict.INVALID_SERVICE_TYPE, WaitClass.ACL_CACHE),
            new Scenario("V133", "Service code that no description defines, called over REST and SOAP",
                    new FixtureSpec(PROVIDER, List.of(new UndefinedService())),
                    List.of(new Check(new RestRequest(CONSUMER, 0, "GET", "/a"), Verdict.UNKNOWN_SERVICE),
                            new Check(new SoapRequest(CONSUMER, 0, V1, null), Verdict.UNKNOWN_SERVICE)),
                    WaitClass.ACL_CACHE));

    private ScenarioCatalogue() {
    }

    public static List<Scenario> all() {
        return ALL;
    }

    private static Check allowed(String method, String path) {
        return allowed(CONSUMER, method, path);
    }

    private static Check allowed(String consumer, String method, String path) {
        return new Check(new RestRequest(consumer, 0, method, path), Verdict.ALLOWED);
    }

    private static Check allowed(int serviceIndex, String method, String path) {
        return new Check(new RestRequest(CONSUMER, serviceIndex, method, path), Verdict.ALLOWED);
    }

    private static Check denied(String method, String path) {
        return denied(CONSUMER, method, path);
    }

    private static Check denied(String consumer, String method, String path) {
        return new Check(new RestRequest(consumer, 0, method, path), Verdict.ACCESS_DENIED);
    }

    /** A REST service whose base endpoint is granted to the consumer, with the changes applied to its description. */
    private static FixtureSpec changed(DescriptionChange... changes) {
        return new FixtureSpec(PROVIDER, List.of(new RestService(CONSUMER_ONLY)), List.of(), List.of(changes));
    }

    /** A REST service whose only grant is the consumer's access to one created endpoint. */
    private static FixtureSpec endpointOnly(String method, String path) {
        return new FixtureSpec(PROVIDER, List.of(
                new RestService(List.of(), List.of(new EndpointSpec(method, path, CONSUMER_ONLY)))));
    }

    /** A REST service whose base endpoint is granted to the provider's local group, with the groups created. */
    private static FixtureSpec groupGranted(LocalGroupSpec... groups) {
        return new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(), List.of(GROUP))),
                List.of(groups));
    }

    private static FixtureSpec wsdl(WsdlShape shape, String codeTail, List<List<String>> grantedByCode) {
        return new FixtureSpec(PROVIDER, List.of(new WsdlService(shape, codeTail, grantedByCode)));
    }
}
