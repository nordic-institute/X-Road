/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ServiceClientHelper {

    private final ServiceClientConverter serviceClientConverter;

    @Autowired
    public ServiceClientHelper(ServiceClientConverter serviceClientConverter) {
        this.serviceClientConverter = serviceClientConverter;
    }

//    public List<XRoadId> getXRoadIdsButSkipLocalGroups(ServiceClients serviceClients) {
//        // ServiceClientConverter cannot resolve the correct XRoadId from LocalGroup ServiceClient's numeric id
//        serviceClients.getItems().removeIf(hasNumericIdAndIsLocalGroup);
//        return serviceClientConverter.convertIds(serviceClients.getItems());
//    }

//    public Set<Long> getLocalGroupIds(ServiceClients serviceClients) {
//        return serviceClients.getItems()
//                .stream()
//                .filter(hasNumericIdAndIsLocalGroup)
//                .map(sc -> Long.parseLong(sc.getId()))
//                .collect(Collectors.toSet());
//    }
//
//    /**
//     * The client-provided ServiceClients only contain id and ServiceClientType.
//     * The id of a LocalGroup is numeric so ServiceClientConverter cannot resolve the correct XRoadId from it.
//     * Therefore LocalGroups need to be handled separately from other types of serviceClients.
//     */
//    private Predicate<ServiceClient> hasNumericIdAndIsLocalGroup = sc -> {
//        boolean hasNumericId = StringUtils.isNumeric(sc.getId());
//        boolean isLocalGroup = sc.getServiceClientType() == ServiceClientType.LOCALGROUP;
//        if (!hasNumericId && isLocalGroup) {
//            throw new BadRequestException("LocalGroup id is not numeric: " + sc.getId());
//        }
//        return hasNumericId && isLocalGroup;
//    };

}
