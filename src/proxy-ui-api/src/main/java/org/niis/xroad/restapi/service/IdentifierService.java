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

package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.repository.IdentifierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/**
 * service class for handling identifiers
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class IdentifierService {

    private final IdentifierRepository identifierRepository;
    private final LocalGroupService localGroupService;
    private final GlobalConfService globalConfService;

    @Autowired
    public IdentifierService(IdentifierRepository identifierRepository,
            LocalGroupService localGroupService,
            GlobalConfService globalConfService) {
        this.identifierRepository = identifierRepository;
        this.localGroupService = localGroupService;
        this.globalConfService = globalConfService;
    }

    /**
     * Get the existing {@link XRoadId xRoadIds} from the local db and persist the not-existing ones
     * Useful method when changing identifier relations (such as adding access rights to services)
     * @param xRoadIds
     * @return List of XRoadIds
     */
    public Set<XRoadId> getOrPersistXroadIds(Set<XRoadId> xRoadIds) {
        Set<XRoadId> txEntities = getXroadIds(xRoadIds);
        xRoadIds.removeAll(txEntities); // remove the persistent ones
        identifierRepository.saveOrUpdate(xRoadIds); // persist the non-persisted
        txEntities.addAll(xRoadIds); // add the newly persisted ids into the collection of already existing ids
        return txEntities;
    }

    /**
     * Get the existing {@link XRoadId xRoadIds} from the local db
     * @param xRoadIds
     * @return List of XRoadIds
     */
    public Set<XRoadId> getXroadIds(Set<XRoadId> xRoadIds) {
        Collection<XRoadId> allIdsFromDb = identifierRepository.getIdentifiers();
        return allIdsFromDb.stream()
                .filter(xRoadIds::contains) // this works because of the XRoadId equals and hashCode overrides
                .collect(Collectors.toSet());
    }

    /**
     * Verify that service client XRoadIds do exist
     * @param clientType owner of (possible) local groups
     * @param serviceClientIds service client ids to check
     * @return
     * @throws IdentifierNotFoundException if there were identifiers that could not be found
     */
    public void verifyServiceClientIdentifiersExist(ClientType clientType, Set<XRoadId> serviceClientIds)
            throws IdentifierNotFoundException {
        Map<XRoadObjectType, List<XRoadId>> idsPerType = serviceClientIds.stream()
                .collect(groupingBy(XRoadId::getObjectType));
        for (XRoadObjectType type: idsPerType.keySet()) {
            if (!isValidServiceClientType(type)) {
                throw new IllegalArgumentException("Invalid service client subject object type " + type);
            }
        }
        if (idsPerType.containsKey(XRoadObjectType.GLOBALGROUP)) {
            if (!globalConfService.globalGroupsExist(idsPerType.get(XRoadObjectType.GLOBALGROUP))) {
                throw new IdentifierNotFoundException();
            }
        }
        if (idsPerType.containsKey(XRoadObjectType.SUBSYSTEM)) {
            if (!globalConfService.clientsExist(idsPerType.get(XRoadObjectType.SUBSYSTEM))) {
                throw new IdentifierNotFoundException();
            }
        }
        if (idsPerType.containsKey(XRoadObjectType.LOCALGROUP)) {
            if (!localGroupService.localGroupsExist(clientType, idsPerType.get(XRoadObjectType.LOCALGROUP))) {
                throw new IdentifierNotFoundException();
            }
        }
    }

    private boolean isValidServiceClientType(XRoadObjectType objectType) {
        return objectType == XRoadObjectType.SUBSYSTEM
                || objectType == XRoadObjectType.GLOBALGROUP
                || objectType == XRoadObjectType.LOCALGROUP;
    }


}
