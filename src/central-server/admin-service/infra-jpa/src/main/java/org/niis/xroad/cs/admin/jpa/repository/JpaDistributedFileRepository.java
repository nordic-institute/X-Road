/**
 * The MIT License
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

import org.niis.xroad.cs.admin.core.entity.DistributedFileEntity;
import org.niis.xroad.cs.admin.core.repository.DistributedFileRepository;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

import static org.niis.xroad.cs.admin.core.entity.DistributedFileEntity_.HA_NODE_NAME;
import static org.niis.xroad.cs.admin.core.entity.DistributedFileEntity_.ID;

@Repository
public interface JpaDistributedFileRepository extends JpaRepository<DistributedFileEntity, Integer>, DistributedFileRepository {
    Set<DistributedFileEntity> findAllByHaNodeName(String haNodeName);

    @Query("FROM DistributedFileEntity WHERE version in (:version, 0)")
    Set<DistributedFileEntity> findAllByVersion(int version);

    default Optional<DistributedFileEntity> findByContentIdAndVersion(String contentIdentifier, int version, String haNodeName) {
        var exampleDistributedFile = new DistributedFileEntity(contentIdentifier, version, haNodeName);
        return findBy(Example.of(exampleDistributedFile, ExampleMatcher.matching().withIgnorePaths(ID)),
                q -> q.sortBy(Sort.by(HA_NODE_NAME, ID))
                        .first());
    }
}
