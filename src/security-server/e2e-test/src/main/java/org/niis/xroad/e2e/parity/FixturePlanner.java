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

import org.niis.xroad.e2e.parity.AdminCall.AddEndpoint;
import org.niis.xroad.e2e.parity.AdminCall.AddRestDescription;
import org.niis.xroad.e2e.parity.AdminCall.AddWsdlDescription;
import org.niis.xroad.e2e.parity.AdminCall.DeleteDescription;
import org.niis.xroad.e2e.parity.AdminCall.DeleteEndpoint;
import org.niis.xroad.e2e.parity.AdminCall.EnableDescription;
import org.niis.xroad.e2e.parity.AdminCall.GrantAccess;
import org.niis.xroad.e2e.parity.AdminCall.GrantEndpointAccess;
import org.niis.xroad.e2e.parity.Scenario.FixtureSpec;
import org.niis.xroad.e2e.parity.Scenario.RestService;
import org.niis.xroad.e2e.parity.Scenario.WsdlService;
import org.niis.xroad.e2e.parity.Scenario.WsdlShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a scenario's fixture spec into admin calls. Every service code carries the run token and the
 * scenario id, so fixtures of different scenarios and of earlier runs never collide, and no shared
 * service description is ever touched. Deleting a service description also removes its access rights,
 * so the cleanup is one delete per created description, preceded by the delete of each endpoint the scenario
 * created. The base endpoint and generated endpoints are never deleted on their own.
 */
public final class FixturePlanner {

    /** Origin of the backend stub that answers every path under {@link #BACKEND_PATH_PREFIX}. */
    public static final String BACKEND_ORIGIN = "http://isrest:8080";
    public static final String BACKEND_PATH_PREFIX = "/acl-parity/";

    private final String runToken;

    public FixturePlanner(String runToken) {
        this.runToken = runToken.toLowerCase(Locale.ROOT);
    }

    public FixturePlan plan(String scenarioId, FixtureSpec spec) {
        var codes = new ArrayList<String>();
        var descriptionKeys = new ArrayList<String>();
        var endpointDeletes = new ArrayList<List<AdminCall>>();
        var setup = new ArrayList<AdminCall>();
        var services = spec.services();
        for (var index = 0; index < services.size(); index++) {
            var base = serviceCode(scenarioId, index);
            switch (services.get(index)) {
                case RestService rest -> {
                    codes.add(base);
                    descriptionKeys.add(base);
                    setup.add(new AddRestDescription(spec.providerClientId(), backendUrl(base), base));
                    setup.add(new EnableDescription(base));
                    grant(setup, spec.providerClientId(), base, rest.grantedConsumerIds());
                    endpointDeletes.add(endpoints(setup, base, rest));
                }
                case WsdlService wsdl -> {
                    var wsdlCodes = wsdlCodes(base, wsdl);
                    var key = wsdlCodes.get(0);
                    codes.addAll(wsdlCodes);
                    descriptionKeys.add(key);
                    endpointDeletes.add(List.of());
                    setup.add(new AddWsdlDescription(spec.providerClientId(), wsdlUrl(wsdl.shape(), wsdlCodes), key));
                    setup.add(new EnableDescription(key));
                    for (var i = 0; i < wsdlCodes.size(); i++) {
                        grant(setup, spec.providerClientId(), wsdlCodes.get(i), wsdl.grantedConsumerIdsByCode().get(i));
                    }
                }
            }
        }
        var cleanup = new ArrayList<AdminCall>();
        for (var index = descriptionKeys.size() - 1; index >= 0; index--) {
            cleanup.addAll(endpointDeletes.get(index).reversed());
            cleanup.add(new DeleteDescription(descriptionKeys.get(index)));
        }
        return new FixturePlan(List.copyOf(codes), List.copyOf(setup), List.copyOf(cleanup));
    }

    private static List<AdminCall> endpoints(List<AdminCall> setup, String serviceCode, RestService rest) {
        var deletes = new ArrayList<AdminCall>();
        for (var endpoint : rest.endpoints()) {
            setup.add(new AddEndpoint(serviceCode, endpoint.method(), endpoint.path()));
            for (var consumer : endpoint.grantedConsumerIds()) {
                setup.add(new GrantEndpointAccess(serviceCode, endpoint.method(), endpoint.path(), consumer));
            }
            deletes.add(new DeleteEndpoint(serviceCode, endpoint.method(), endpoint.path()));
        }
        return deletes;
    }

    private static void grant(List<AdminCall> setup, String providerClientId, String serviceCode,
                              List<String> consumerIds) {
        for (var consumer : consumerIds) {
            setup.add(new GrantAccess(providerClientId, consumer, serviceCode));
        }
    }

    private static List<String> wsdlCodes(String base, WsdlService wsdl) {
        var codes = new ArrayList<String>();
        for (var i = 0; i < wsdl.shape().codeCount(); i++) {
            codes.add(base + (i == 0 ? "" : "-b") + wsdl.codeTail());
        }
        return codes;
    }

    String serviceCode(String scenarioId, int serviceIndex) {
        var base = "p%s-%s".formatted(runToken, scenarioId.toLowerCase(Locale.ROOT));
        return serviceIndex == 0 ? base : base + "-" + (serviceIndex + 1);
    }

    static String backendUrl(String serviceCode) {
        return BACKEND_ORIGIN + BACKEND_PATH_PREFIX + serviceCode;
    }

    static String wsdlUrl(WsdlShape shape, List<String> codes) {
        var query = shape == WsdlShape.PAIR
                ? "code1=%s&code2=%s".formatted(codes.get(0), codes.get(1))
                : "code=" + codes.get(0);
        return "%s%swsdl/%s.wsdl?%s".formatted(BACKEND_ORIGIN, BACKEND_PATH_PREFIX,
                shape.name().toLowerCase(Locale.ROOT), query);
    }
}
