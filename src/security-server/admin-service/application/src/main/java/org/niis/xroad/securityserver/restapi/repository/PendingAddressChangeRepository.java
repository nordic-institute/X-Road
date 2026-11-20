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
package org.niis.xroad.securityserver.restapi.repository;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.niis.xroad.serverconf.impl.dao.PendingAddressChangeDAOImpl;
import org.niis.xroad.serverconf.impl.entity.PendingAddressChangeEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for the {@code pending_address_change} table, shared by all nodes.
 */
@Repository
@Transactional
@RequiredArgsConstructor
public class PendingAddressChangeRepository {

    private final PersistenceUtils persistenceUtils;

    private final PendingAddressChangeDAOImpl pendingAddressChangeDAO = new PendingAddressChangeDAOImpl();

    /**
     * Finds the pending address change.
     *
     * @return the pending address change, if one is stored
     */
    @Transactional(readOnly = true)
    public Optional<PendingAddressChangeEntity> find() {
        return findPendingAddressChange();
    }

    /**
     * Finds the address the administrator requested.
     *
     * @return the requested address, or empty if no address change is pending
     */
    @Transactional(readOnly = true)
    public Optional<String> findRequestedAddress() {
        return findPendingAddressChange().map(PendingAddressChangeEntity::getRequestedAddress);
    }

    private Optional<PendingAddressChangeEntity> findPendingAddressChange() {
        return pendingAddressChangeDAO.find(persistenceUtils.getCurrentSession());
    }

    /**
     * Stores the pending address change and writes it to the database before returning.
     * Fails with a {@link org.springframework.dao.DataIntegrityViolationException} when a pending address change
     * is already stored.
     *
     * @param requestedAddress the address the administrator requested
     * @param requestedAt      when the administrator requested it
     */
    public void save(String requestedAddress, Instant requestedAt) {
        pendingAddressChangeDAO.save(persistenceUtils.getCurrentSession(), requestedAddress, requestedAt);
        persistenceUtils.flush();
    }

    /**
     * Deletes the pending address change. Deleting when none is stored is not an error.
     */
    public void delete() {
        pendingAddressChangeDAO.delete(persistenceUtils.getCurrentSession());
    }

}
