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

import ee.ria.xroad.common.identifier.XRoadId;

import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.model.ServiceClient;
import org.niis.xroad.restapi.openapi.model.ServiceClientType;
import org.niis.xroad.restapi.openapi.model.ServiceClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// TO DO: redo
@Component
public class SubjectHelper {

    private final SubjectConverter subjectConverter;

    @Autowired
    public SubjectHelper(SubjectConverter subjectConverter) {
        this.subjectConverter = subjectConverter;
    }

    public List<XRoadId> getXRoadIdsButSkipLocalGroups(ServiceClients serviceClients) {
        // SubjectConverter cannot resolve the correct XRoadId from LocalGroup subject's numeric id
        serviceClients.getItems().removeIf(hasNumericIdAndIsLocalGroupSc);
        return subjectConverter.convertScId(serviceClients.getItems());
    }

    public Set<Long> getLocalGroupIds(ServiceClients serviceClients) {
        return serviceClients.getItems()
                .stream()
                .filter(hasNumericIdAndIsLocalGroupSc)
                .map(subject -> Long.parseLong(subject.getId()))
                .collect(Collectors.toSet());
    }

    private Predicate<ServiceClient> hasNumericIdAndIsLocalGroupSc = subject -> {
        boolean hasNumericId = StringUtils.isNumeric(subject.getId());
        boolean isLocalGroup = subject.getServiceClientType() == ServiceClientType.LOCALGROUP;
        if (!hasNumericId && isLocalGroup) {
            throw new BadRequestException("LocalGroup id is not numeric: " + subject.getId());
        }
        return hasNumericId && isLocalGroup;
    };

}
