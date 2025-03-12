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

import org.niis.xroad.securityserver.restapi.wsdl.OpenApiParser;
import org.niis.xroad.serverconf.entity.ClientTypeEntity;
import org.niis.xroad.serverconf.entity.EndpointTypeEntity;
import org.niis.xroad.serverconf.entity.ServiceDescriptionTypeEntity;
import org.niis.xroad.serverconf.entity.ServiceTypeEntity;
import org.niis.xroad.serverconf.mapper.EndpointTypeMapper;
import org.niis.xroad.serverconf.model.EndpointType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.niis.xroad.serverconf.model.BaseEndpoint.ANY_METHOD;
import static org.niis.xroad.serverconf.model.BaseEndpoint.ANY_PATH;

@Component
public class EndpointHelper {

    /**
     * Get endpoints from given service description
     * @param serviceDescriptionType service description
     * @return List<EndpointTypeEntity>
     */
    public List<EndpointTypeEntity> getEndpoints(ServiceDescriptionTypeEntity serviceDescriptionType) {
        ClientTypeEntity client = serviceDescriptionType.getClient();
        List<String> allServiceCodes = serviceDescriptionType.getService().stream()
                .map(ServiceTypeEntity::getServiceCode)
                .toList();
        List<EndpointTypeEntity> allEndpoints = client.getEndpoint();
        return allEndpoints.stream()
                .filter(endpointType -> allServiceCodes.contains(endpointType.getServiceCode()))
                .collect(Collectors.toList());
    }

    public List<EndpointTypeEntity> getNewEndpoints(String serviceCode, OpenApiParser.Result result) {
        List<EndpointType> newEndpoints = result.getOperations().stream()
                .map(operation -> mapOperationToEndpoint(serviceCode, operation))
                .collect(Collectors.toList());
        newEndpoints.add(new EndpointType(serviceCode, ANY_METHOD, ANY_PATH, true));
        return EndpointTypeMapper.get().toEntities(newEndpoints);
    }

    private EndpointType mapOperationToEndpoint(String serviceCode, OpenApiParser.Operation operation) {
        return new EndpointType(serviceCode, operation.getMethod(), operation.getPath(), true);
    }
}
