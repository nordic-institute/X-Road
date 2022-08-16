/**
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
package org.niis.xroad.centralserver.restapi.repository;

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestType;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.niis.xroad.centralserver.restapi.entity.ManagementRequestView;
import org.niis.xroad.centralserver.restapi.entity.ManagementRequestView_;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static org.niis.xroad.centralserver.restapi.repository.CriteriaBuilderUtil.caseInsensitiveLike;

public interface ManagementRequestViewRepository extends JpaRepository<ManagementRequestView, Integer>,
        JpaSpecificationExecutor<ManagementRequestView> {

    @Builder
    @Getter
    class Criteria {
        private final String query;
        private final Origin origin;
        private final List<ManagementRequestType> types;
        private final ManagementRequestStatus status;
        private final SecurityServerId serverId;
    }

    static Specification<ManagementRequestView> findSpec(final Criteria criteria) {
        final List<String> entityTypes = ofNullable(criteria.getTypes())
                .map(managementRequestTypes -> criteria.getTypes().stream()
                        .map(ManagementRequestType::getRequestDiscriminatorValue)
                        .collect(Collectors.toList()))
                .orElseGet(Collections::emptyList);

        return (root, query, builder) -> {
            var pred = builder.conjunction();
            if (criteria.getOrigin() != null) {
                pred = builder.and(pred, builder.equal(root.get(ManagementRequestView_.origin), criteria.getOrigin()));
            }
            if (!entityTypes.isEmpty()) {
                pred = builder.and(pred, root.get(ManagementRequestView_.type).in(entityTypes));
            }
            if (criteria.getStatus() != null) {
                pred = builder.and(pred, builder.equal(root.get(ManagementRequestView_.requestProcessingStatus),
                        criteria.getStatus()));
            }

            if (criteria.getServerId() != null) {
                var serverId = criteria.getServerId();
                pred = builder.and(pred,
                        builder.equal(root.get(ManagementRequestView_.xroadInstance), serverId.getXRoadInstance()),
                        builder.equal(root.get(ManagementRequestView_.memberClass), serverId.getMemberClass()),
                        builder.equal(root.get(ManagementRequestView_.memberCode), serverId.getMemberCode()),
                        builder.equal(root.get(ManagementRequestView_.serverCode), serverId.getServerCode()));
            }

            if (StringUtils.isNotBlank(criteria.getQuery())) {
                var q = criteria.getQuery();

                pred = builder.and(builder.or(
                        caseInsensitiveLike(root, builder, q,
                                root.get(ManagementRequestView_.id).as(String.class)),
                        caseInsensitiveLike(root, builder, q,
                                root.get(ManagementRequestView_.createdAt).as(String.class)),
                        caseInsensitiveLike(root, builder, q,
                                root.get(ManagementRequestView_.origin).as(String.class)),
                        caseInsensitiveLike(root, builder, q,
                                root.get(ManagementRequestView_.requestProcessingStatus).as(String.class)),
                        caseInsensitiveLike(root, builder, q, root.get(ManagementRequestView_.securityServerOwnerName)),

                        caseInsensitiveLike(root, builder, q, root.get(ManagementRequestView_.xroadInstance)),
                        caseInsensitiveLike(root, builder, q, root.get(ManagementRequestView_.memberClass)),
                        caseInsensitiveLike(root, builder, q, root.get(ManagementRequestView_.memberCode)),
                        caseInsensitiveLike(root, builder, q, root.get(ManagementRequestView_.serverCode))
                ));
            }

            return pred;
        };
    }
}
