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
package org.niis.xroad.cs.admin.core.repository;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.niis.xroad.cs.admin.core.entity.MemberRow;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SecurityServerRepository extends
        GenericRepository<SecurityServerEntity, Integer> {

    Page<SecurityServerEntity> findAllByQuery(String query, Pageable pageable);

    Optional<SecurityServerEntity> findByOwnerIdAndServerCode(Long ownerId, String serverCode);

    Optional<SecurityServerEntity> findBy(SecurityServerId serverId, ClientId clientId);

    Optional<SecurityServerEntity> findBy(SecurityServerId serverId);

    long count(SecurityServerId id);

    boolean existsBy(SecurityServerId serverId);

    /**
     * Locks the server row for the rest of the transaction, so writers and removers of the server's published
     * values serialise. The query touches the server row alone: no entity is loaded and nothing is joined.
     * Call it before reading or writing any row that belongs to the server; with several servers, in id order.
     *
     * @param id the id of the server row
     * @return the id when the row exists, locked; empty when there is no such row
     */
    Optional<Integer> lockForUpdateById(Integer id);

    /**
     * Locks the server row like {@link #lockForUpdateById}, for the server named by its identifier. Call it
     * before loading the server entity, so the loaded state is the state under the lock.
     *
     * @param serverId the identifier of the server
     * @return the id of the row when it exists, locked; empty when there is no such row
     */
    Optional<Integer> lockForUpdateBy(SecurityServerId serverId);

    /**
     * Selects the row id and identifier of every member that has a client on the server, disabled clients
     * included, in one SQL statement and without loading any entity. A member with several clients on the
     * server appears once.
     */
    List<MemberRow> findServedMembers(Integer securityServerId);

    /**
     * Tells whether the SYSTEM DID is stored for a server other than the given one.
     */
    boolean existsBySystemDidAndIdNot(String systemDid, Integer id);

}
