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
package org.niis.xroad.securityserver.restapi.util;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;

import org.niis.xroad.securityserver.restapi.wsdl.OpenApiParser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class EndpointHelper {

    /**
     * Get endpoints from client, for given service
     * @param service
     * @param client
     * @return
     */
    public List<EndpointType> getEndpoints(ServiceType service, ClientType client) {
        List<EndpointType> allEndpoints = client.getEndpoint();
        return allEndpoints.stream()
                .filter(endpointType -> endpointType.getServiceCode().equals(service.getServiceCode()))
                .collect(Collectors.toList());
    }

    /**
     * Get endpoints from given service description
     * @param serviceDescriptionType
     * @return
     */
    public List<EndpointType> getEndpoints(ServiceDescriptionType serviceDescriptionType) {
        ClientType client = serviceDescriptionType.getClient();
        List<String> allServiceCodes = serviceDescriptionType.getService().stream()
                .map(ServiceType::getServiceCode)
                .collect(Collectors.toList());
        List<EndpointType> allEndpoints = client.getEndpoint();
        return allEndpoints.stream()
                .filter(endpointType -> allServiceCodes.contains(endpointType.getServiceCode()))
                .collect(Collectors.toList());
    }

    public List<EndpointType> getNewEndpoints(String serviceCode, OpenApiParser.Result result) {
        List<EndpointType> newEndpoints = result.getOperations().stream()
                .map(operation -> mapOperationToEndpoint(serviceCode, operation))
                .collect(Collectors.toList());
        newEndpoints.add(new EndpointType(serviceCode, EndpointType.ANY_METHOD, EndpointType.ANY_PATH, true));
        return newEndpoints;
    }

    private EndpointType mapOperationToEndpoint(String serviceCode, OpenApiParser.Operation operation) {
        return new EndpointType(serviceCode, operation.getMethod(), operation.getPath(), true);
    }
}
