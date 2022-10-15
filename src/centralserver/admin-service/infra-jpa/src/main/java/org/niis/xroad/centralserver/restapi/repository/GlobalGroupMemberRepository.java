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

import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.centralserver.restapi.entity.ClientId;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroup;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroupMember;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroupMember_;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroup_;
import org.niis.xroad.centralserver.restapi.entity.XRoadId_;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;

import static org.niis.xroad.centralserver.restapi.repository.CriteriaBuilderUtil.caseInsensitiveLike;

@Repository
public interface GlobalGroupMemberRepository
        extends JpaRepository<GlobalGroupMember, Integer>, JpaSpecificationExecutor<GlobalGroupMember> {

    @Builder
    @Getter
    class Criteria {
        private final Integer groupId;
        private final String query;
        private final String memberClass;
        private final String instance;
        private final List<String> codes;
        private final List<String> subsystems;
        private final List<XRoadObjectType> types;
    }

    static Specification<GlobalGroupMember> findSpecification(Criteria criteria) {
        return (root, query, builder) -> findPredicate(root, builder, criteria);
    }

    private static Predicate findPredicate(Root<GlobalGroupMember> root, CriteriaBuilder builder, Criteria criteria) {
        final Join<GlobalGroupMember, ClientId> member = root.join(GlobalGroupMember_.identifier);
        final Join<GlobalGroupMember, GlobalGroup> globalGroup = root.join(GlobalGroupMember_.globalGroup);

        final List<Predicate> predicates = new ArrayList<>();

        if (criteria.getGroupId() != null) {
            predicates.add(builder.equal(globalGroup.get(GlobalGroup_.id), criteria.getGroupId()));
        }
        if (StringUtils.isNotBlank(criteria.getQuery())) {
            predicates.add(searchPredicate(root, member, builder, criteria));
        }
        if (StringUtils.isNotBlank(criteria.getMemberClass())) {
            predicates.add(builder.equal(member.get(XRoadId_.memberClass), criteria.getMemberClass()));
        }
        if (StringUtils.isNotBlank(criteria.getInstance())) {
            predicates.add(builder.equal(member.get(XRoadId_.xRoadInstance), criteria.getInstance()));
        }
        if (!CollectionUtils.isEmpty(criteria.getCodes())) {
            predicates.add(member.get(XRoadId_.memberCode).in(criteria.getCodes()));
        }
        if (!CollectionUtils.isEmpty(criteria.getSubsystems())) {
            predicates.add(member.get(XRoadId_.subsystemCode).in(criteria.getSubsystems()));
        }
        if (!CollectionUtils.isEmpty(criteria.getTypes())) {
            predicates.add(member.get(XRoadId_.objectType).in(criteria.getTypes()));
        }
        return builder.and(predicates.toArray(new Predicate[0]));
    }

    private static Predicate searchPredicate(Root<GlobalGroupMember> root, Join<GlobalGroupMember, ClientId> member,
                                             CriteriaBuilder builder,
                                             Criteria criteria) {
        return builder.or(
                caseInsensitiveLike(root, builder, criteria.getQuery(), member.get(XRoadId_.MEMBER_CODE)),
                caseInsensitiveLike(root, builder, criteria.getQuery(), member.get(XRoadId_.MEMBER_CLASS)),
                caseInsensitiveLike(root, builder, criteria.getQuery(), member.get(XRoadId_.SUBSYSTEM_CODE)),
                caseInsensitiveLike(root, builder, criteria.getQuery(), member.get(XRoadId_.X_ROAD_INSTANCE)),
                caseInsensitiveLike(root, builder, criteria.getQuery(), member.get(XRoadId_.OBJECT_TYPE))
        );
    }

    List<GlobalGroupMember> findByGlobalGroupId(Integer groupId);

    default List<GlobalGroupMember> findMemberGroups(ee.ria.xroad.common.identifier.ClientId memberId) {
        return findAll(findSpecification(Criteria.builder()
                .instance(memberId.getXRoadInstance())
                .memberClass(memberId.getMemberClass())
                .codes(List.of(memberId.getMemberCode()))
                .build()));
    }
}
