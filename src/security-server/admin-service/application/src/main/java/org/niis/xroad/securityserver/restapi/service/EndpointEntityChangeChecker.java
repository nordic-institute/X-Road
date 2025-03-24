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
import org.niis.xroad.serverconf.entity.AccessRightEntity;
import org.niis.xroad.serverconf.entity.EndpointEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Util class for comparing collections of EndpointEntity
 */
@Component
public class EndpointEntityChangeChecker {

    /**
     * Create lists of full service codes, ones that were added and ones that were removed,
     * by comparing collections of {@link EndpointEntity}s. Also retain the lists as List<EndpointEntity>
     *
     * @param serviceClientEndpoints serviceClientEndpoints
     * @param oldEndpoints oldEndpoints
     * @param newEndpoints newEndpoints
     * @param serviceClientAcls serviceClientAcls
     * @return ServiceChanges
     */
    ServiceChanges check(List<EndpointEntity> serviceClientEndpoints,
                                List<EndpointEntity> oldEndpoints,
                                List<EndpointEntity> newEndpoints,
                                List<AccessRightEntity> serviceClientAcls) {

        List<EndpointEntity> removedEndpoints = oldEndpoints.stream()
                .filter(EndpointEntity::isGenerated)
                .filter(ep -> newEndpoints.stream().noneMatch(parsedEp -> parsedEp.isEquivalent(ep)))
                .toList();

        List<EndpointEntity> addedEndpoints = newEndpoints.stream()
                .filter(parsedEp -> serviceClientEndpoints.stream().noneMatch(ep -> ep.isEquivalent(parsedEp)))
                .toList();

        List<AccessRightEntity> removedAcls = serviceClientAcls.stream()
                .filter(acl -> isAclRemoved(acl, addedEndpoints, removedEndpoints))
                .toList();

        return new ServiceChanges(addedEndpoints, removedEndpoints, removedAcls);
    }

    private boolean isAclRemoved(AccessRightEntity accessRightEntity,
                                 List<EndpointEntity> addedEndpoints,
                                 List<EndpointEntity> removedEndpoints) {
        EndpointEntity endpointEntity = accessRightEntity.getEndpoint();
        return removedEndpoints.contains(endpointEntity)
                && addedEndpoints.stream().noneMatch(parsedEp -> parsedEp.isEquivalent(endpointEntity));
    }

    private List<String> toFullEndpointCodes(List<EndpointEntity> endpoints) {
        return endpoints.stream()
                .map(this::toFullEndpointCode)
                .toList();
    }

    private String toFullEndpointCode(EndpointEntity endpointEntity) {
        return endpointEntity.getMethod() + " " + endpointEntity.getPath();
    }

    /**
     * List of EndpointEntity of added and removed endpoints
     */
    @Data
    public class ServiceChanges {
        private List<EndpointEntity> addedEndpoints;
        private List<EndpointEntity> removedEndpoints;
        private List<AccessRightEntity> removedAcls;
        private List<String> addedEndpointsCodes;
        private List<String> removedEndpointsCodes;

        public ServiceChanges(List<EndpointEntity> addedEndpoints,
                              List<EndpointEntity> removedEndpoints,
                              List<AccessRightEntity> removedAcls) {
            this.addedEndpoints = addedEndpoints;
            this.removedEndpoints = removedEndpoints;
            this.removedAcls = removedAcls;
            this.addedEndpointsCodes = toFullEndpointCodes(addedEndpoints);
            this.removedEndpointsCodes = toFullEndpointCodes(removedEndpoints);
        }

        public boolean isEmpty() {
            return CollectionUtils.isEmpty(addedEndpoints) && CollectionUtils.isEmpty(removedEndpoints);
        }
    }
}
