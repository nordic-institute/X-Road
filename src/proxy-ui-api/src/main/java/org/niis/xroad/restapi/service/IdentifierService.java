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

import ee.ria.xroad.common.identifier.XRoadId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.repository.IdentifierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * service class for handling identifiers
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class IdentifierService {
    private final IdentifierRepository identifierRepository;

    @Autowired
    public IdentifierService(IdentifierRepository identifierRepository) {
        this.identifierRepository = identifierRepository;
    }

    /**
     * Get the existing {@link XRoadId xRoadIds} from the local db and persist the not-existing ones
     * Useful method when changing identifier relations (such as adding access rights to services)
     * @param xRoadIds
     * @return List of XRoadIds
     */
    public Set<XRoadId> getOrPersistXroadIds(Set<XRoadId> xRoadIds) {
        Set<XRoadId> idsToPersist = new HashSet<>(xRoadIds);
        Set<XRoadId> managedEntities = getXroadIds(idsToPersist);
        idsToPersist.removeAll(managedEntities); // remove the persistent ones
        identifierRepository.saveOrUpdate(idsToPersist); // persist the non-persisted
        managedEntities.addAll(idsToPersist); // add the newly persisted ids into the collection of already existing ids
        return managedEntities;
    }

    /**
     * Get the existing {@link XRoadId xRoadId} from the local db or persist it if it did not exist in db yet.
     * @param xRoadId
     * @return managed XRoadId which exists in IDENTIFIER table
     */
    public XRoadId getOrPersistXroadId(XRoadId xRoadId) {
        return getOrPersistXroadIds(new HashSet<>(Arrays.asList(xRoadId))).iterator().next();
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
}
