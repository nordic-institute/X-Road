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

import ee.ria.xroad.common.identifier.XRoadId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.identifiers.jpa.entity.XRoadIdEntity;
import org.niis.xroad.securityserver.restapi.repository.IdentifierRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@RequiredArgsConstructor
public class IdentifierService {

    private final IdentifierRepository identifierRepository;

    /**
     * Get the existing {@link XRoadId xRoadIds} from the local db and persist the not-existing ones
     * Useful method when changing identifier relations (such as adding access rights to services)
     *
     * @param xRoadIds
     * @return Set of XRoadIds
     */
    <T extends XRoadIdEntity> Set<T> getOrPersistXroadIdEntities(Set<T> xRoadIds) {
        Set<T> idsToPersist = new HashSet<>(xRoadIds);
        Set<T> managedEntities = getXroadIdEntities(idsToPersist);
        idsToPersist.removeAll(managedEntities); // remove the persistent ones
        identifierRepository.persist(idsToPersist); // persist the non-persisted
        managedEntities.addAll(idsToPersist); // add the newly persisted ids into the collection of already existing ids
        return managedEntities;
    }

    /**
     * Get the existing {@link XRoadId xRoadId} from the local db or persist it if it did not exist in db yet.
     *
     * @param xRoadId
     * @return managed XRoadId which exists in IDENTIFIER table
     */
    <T extends XRoadIdEntity> T getOrPersistXroadIdEntity(T xRoadId) {
        return getOrPersistXroadIdEntities(Set.of(xRoadId)).iterator().next();
    }

    /**
     * Get the existing {@link XRoadId xRoadIds} from the local db
     *
     * @param xRoadIds
     * @return Set of XRoadIds
     */
    @SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
    <T extends XRoadIdEntity> Set<T> getXroadIdEntities(Set<T> xRoadIds) {
        Collection<? extends XRoadIdEntity> allIdsFromDb = identifierRepository.getIdentifiers();
        return allIdsFromDb.stream()
                .filter(xRoadIds::contains) // this works because of the XRoadId equals and hashCode overrides
                .map(xRoadId -> (T) xRoadId)
                .collect(Collectors.toSet());
    }

}
