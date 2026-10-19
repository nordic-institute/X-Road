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
import org.niis.xroad.e2e.parity.Scenario.EndpointSpec;
import org.niis.xroad.e2e.parity.Scenario.FixtureSpec;
import org.niis.xroad.e2e.parity.Scenario.RestRequest;
import org.niis.xroad.e2e.parity.Scenario.RestService;
import org.niis.xroad.e2e.parity.Scenario.SoapRequest;
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
    /** A Security Server code that no member of the instance registers. */
    public static final String UNREGISTERED_SECURITY_SERVER = "DEV:COM:1234:acl-parity-unregistered";

    private static final String V1 = "v1";
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
                    Verdict.INVALID_SERVICE_TYPE, WaitClass.ACL_CACHE));

    private ScenarioCatalogue() {
    }

    public static List<Scenario> all() {
        return ALL;
    }

    private static Check allowed(String method, String path) {
        return new Check(new RestRequest(CONSUMER, 0, method, path), Verdict.ALLOWED);
    }

    private static Check denied(String method, String path) {
        return new Check(new RestRequest(CONSUMER, 0, method, path), Verdict.ACCESS_DENIED);
    }

    /** A REST service whose only grant is the consumer's access to one created endpoint. */
    private static FixtureSpec endpointOnly(String method, String path) {
        return new FixtureSpec(PROVIDER, List.of(
                new RestService(List.of(), List.of(new EndpointSpec(method, path, CONSUMER_ONLY)))));
    }

    private static FixtureSpec wsdl(WsdlShape shape, String codeTail, List<List<String>> grantedByCode) {
        return new FixtureSpec(PROVIDER, List.of(new WsdlService(shape, codeTail, grantedByCode)));
    }
}
