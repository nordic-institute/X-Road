/*
 * The MIT License
 *
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
package org.niis.xroad.securityserver.restapi.service;

import lombok.Data;
import org.niis.xroad.securityserver.restapi.util.ServiceFormatter;
import org.niis.xroad.serverconf.impl.entity.ServiceEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Util class for comparing collections of ServiceTypes
 */
@Component
public class ServiceChangeChecker {
    /**
     * Create lists of full service codes, ones that were added and ones that were removed,
     * by comparing collections of ServiceTypes. Also retain the lists as List<ServiceType>
     * @param oldServices
     * @param newServices
     * @return
     */
    public ServiceChanges check(List<ServiceEntity> oldServices, List<ServiceEntity> newServices) {
        List<String> oldFullServiceCodes = toFullServiceCodes(oldServices);
        List<String> newFullServiceCodes = toFullServiceCodes(newServices);

        List<ServiceEntity> addedServices = new ArrayList<>(newServices);
        addedServices.removeIf(serviceType -> oldFullServiceCodes
                .contains(ServiceFormatter.getServiceFullName(serviceType)));

        List<ServiceEntity> removedServices = new ArrayList<>(oldServices);
        removedServices.removeIf(serviceType -> newFullServiceCodes
                .contains(ServiceFormatter.getServiceFullName(serviceType)));

        return new ServiceChanges(addedServices, removedServices);
    }

    private List<String> toFullServiceCodes(List<ServiceEntity> newServices) {
        return newServices.stream()
                .map(ServiceFormatter::getServiceFullName)
                .collect(Collectors.toList());
    }

    /**
     * List of ServiceTypes and servicecodes as String (full servicecode + version combination) of
     * added and removed services
     */
    @Data
    public class ServiceChanges {
        private List<String> addedFullServiceCodes;
        private List<String> removedFullServiceCodes;
        private List<ServiceEntity> addedServices;
        private List<ServiceEntity> removedServices;

        public ServiceChanges(List<ServiceEntity> addedServices, List<ServiceEntity> removedServices) {
            this.addedServices = addedServices;
            this.removedServices = removedServices;
            this.addedFullServiceCodes = toFullServiceCodes(addedServices);
            this.removedFullServiceCodes = toFullServiceCodes(removedServices);
        }

        public boolean isEmpty() {
            return CollectionUtils.isEmpty(addedFullServiceCodes) && CollectionUtils.isEmpty(removedFullServiceCodes)
                    && CollectionUtils.isEmpty(addedServices) && CollectionUtils.isEmpty(removedServices);
        }
    }
}
