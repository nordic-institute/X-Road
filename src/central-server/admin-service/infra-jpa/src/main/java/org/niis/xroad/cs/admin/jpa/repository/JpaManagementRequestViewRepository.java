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

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.core.entity.ManagementRequestViewEntity;
import org.niis.xroad.cs.admin.core.entity.ManagementRequestViewEntity_;
import org.niis.xroad.cs.admin.core.repository.ManagementRequestViewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.jpa.repository.util.CriteriaBuilderUtil.caseInsensitiveLike;

@Repository
public interface JpaManagementRequestViewRepository extends JpaRepository<ManagementRequestViewEntity, Integer>,
        JpaSpecificationExecutor<ManagementRequestViewEntity>,
        ManagementRequestViewRepository {

    default Page<ManagementRequestViewEntity> findAll(ManagementRequestService.Criteria criteria, Pageable pageable) {
        return findAll(findSpec(criteria), pageable);
    }

    static Specification<ManagementRequestViewEntity> findSpec(final ManagementRequestService.Criteria criteria) {
        final List<String> entityTypes = ofNullable(criteria.getTypes())
                .map(managementRequestTypes -> criteria.getTypes().stream()
                        .map(ManagementRequestViewEntity.ManagementRequestTypeDiscriminatorMapping::getDiscriminator)
                        .collect(toList()))
                .orElseGet(Collections::emptyList);

        return (root, query, builder) -> {
            var pred = builder.conjunction();
            if (criteria.getOrigin() != null) {
                pred = builder.and(pred, builder.equal(root.get(ManagementRequestViewEntity_.origin), criteria.getOrigin()));
            }
            if (!entityTypes.isEmpty()) {
                pred = builder.and(pred, root.get(ManagementRequestViewEntity_.type).in(entityTypes));
            }
            if (criteria.getStatus() != null) {
                pred = builder.and(pred, builder.equal(root.get(ManagementRequestViewEntity_.requestProcessingStatus),
                        criteria.getStatus()));
            }

            if (criteria.getServerId() != null) {
                var serverId = criteria.getServerId();
                pred = builder.and(pred,
                        builder.equal(root.get(ManagementRequestViewEntity_.xroadInstance), serverId.getXRoadInstance()),
                        builder.equal(root.get(ManagementRequestViewEntity_.memberClass), serverId.getMemberClass()),
                        builder.equal(root.get(ManagementRequestViewEntity_.memberCode), serverId.getMemberCode()),
                        builder.equal(root.get(ManagementRequestViewEntity_.serverCode), serverId.getServerCode()));
            }

            if (criteria.getClientId() != null) {
                var clientId = criteria.getClientId();
                pred = builder.and(pred,
                        builder.equal(root.get(ManagementRequestViewEntity_.clientXroadInstance), clientId.getXRoadInstance()),
                        builder.equal(root.get(ManagementRequestViewEntity_.clientMemberClass), clientId.getMemberClass()),
                        builder.equal(root.get(ManagementRequestViewEntity_.clientMemberCode), clientId.getMemberCode()),
                        builder.equal(root.get(ManagementRequestViewEntity_.clientSubsystemCode), clientId.getSubsystemCode()));
            }

            if (StringUtils.isNotBlank(criteria.getQuery())) {
                pred = setQueryPredicates(criteria, builder, root, pred);
            }

            return pred;
        };
    }

    private static Predicate setQueryPredicates(ManagementRequestService.Criteria criteria, CriteriaBuilder builder,
                                                Root<ManagementRequestViewEntity> root,
                                                Predicate pred) {
        final var q = criteria.getQuery();
        final List<Predicate> predicates = new ArrayList<>();

        predicates.add(caseInsensitiveLike(root, builder, q, root.get(ManagementRequestViewEntity_.id).as(String.class)));
        predicates.add(caseInsensitiveLike(root, builder, q, root.get(ManagementRequestViewEntity_.createdAt).as(String.class)));

        predicates.add(caseInsensitiveLike(root, builder, q, root.get(ManagementRequestViewEntity_.securityServerOwnerName)));
        predicates.add(caseInsensitiveLike(root, builder, q, root.get(ManagementRequestViewEntity_.xroadInstance)));
        predicates.add(caseInsensitiveLike(root, builder, q, root.get(ManagementRequestViewEntity_.memberClass)));
        predicates.add(caseInsensitiveLike(root, builder, q, root.get(ManagementRequestViewEntity_.memberCode)));
        predicates.add(caseInsensitiveLike(root, builder, q, root.get(ManagementRequestViewEntity_.serverCode)));

        return builder.and(pred, builder.or(predicates.toArray(new Predicate[0])));
    }
}
