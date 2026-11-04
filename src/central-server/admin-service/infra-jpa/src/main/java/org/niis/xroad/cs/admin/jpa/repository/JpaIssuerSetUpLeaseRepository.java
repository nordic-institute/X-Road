/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.jpa.repository;

import org.niis.xroad.cs.admin.core.entity.IssuerSetUpLeaseEntity;
import org.niis.xroad.cs.admin.core.repository.IssuerSetUpLeaseRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface JpaIssuerSetUpLeaseRepository extends JpaRepository<IssuerSetUpLeaseEntity, Short>, IssuerSetUpLeaseRepository {

    @Override
    default boolean tryAcquire(String node, Instant now, Instant startedAt, Instant expiresAt) {
        return tryAcquireRow(node, now, startedAt, expiresAt) > 0;
    }

    /**
     * The conditional upsert that makes acquiring the lease atomic: it inserts the single row when none
     * exists yet, replaces it when the existing row's {@code expires_at} is at or before {@code now}, and
     * otherwise leaves it untouched. The affected-row count is 1 only in the first two cases.
     */
    @Modifying
    @Query(value = "INSERT INTO " + IssuerSetUpLeaseEntity.TABLE_NAME + " (id, owner_node, started_at, expires_at) "
            + "VALUES (" + IssuerSetUpLeaseEntity.LEASE_ID + ", :node, :startedAt, :expiresAt) "
            + "ON CONFLICT (id) DO UPDATE SET owner_node = excluded.owner_node, started_at = excluded.started_at, "
            + "expires_at = excluded.expires_at "
            + "WHERE " + IssuerSetUpLeaseEntity.TABLE_NAME + ".expires_at <= :now",
            nativeQuery = true)
    int tryAcquireRow(String node, Instant now, Instant startedAt, Instant expiresAt);

    @Override
    @Modifying
    @Query(value = "DELETE FROM " + IssuerSetUpLeaseEntity.TABLE_NAME + " WHERE owner_node = :node", nativeQuery = true)
    void release(String node);

    @Override
    @Query(value = "SELECT owner_node FROM " + IssuerSetUpLeaseEntity.TABLE_NAME + " WHERE expires_at > :now",
            nativeQuery = true)
    Optional<String> findHolder(Instant now);
}
