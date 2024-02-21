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
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity_;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMembersViewEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMembersViewEntity_;
import org.niis.xroad.cs.admin.core.entity.XRoadIdEntity_;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupMembersViewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

import static org.niis.xroad.cs.admin.jpa.repository.util.CriteriaBuilderUtil.caseInsensitiveLike;

@Repository
public interface JpaGlobalGroupMembersViewRepository extends JpaRepository<GlobalGroupMembersViewEntity, Integer>,
        JpaSpecificationExecutor<GlobalGroupMembersViewEntity>, GlobalGroupMembersViewRepository {

    default Page<GlobalGroupMembersViewEntity> findAll(GlobalGroupMemberService.Criteria criteria, Pageable pageable) {
        return findAll(findSpecification(criteria), pageable);
    }

    private static Specification<GlobalGroupMembersViewEntity> findSpecification(GlobalGroupMemberService.Criteria criteria) {
        return (root, query, builder) -> findPredicate(root, builder, criteria);
    }

    private static Predicate findPredicate(Root<GlobalGroupMembersViewEntity> root, CriteriaBuilder builder,
                                           GlobalGroupMemberService.Criteria criteria) {
        final Join<GlobalGroupMembersViewEntity, ClientIdEntity> member = root.join(GlobalGroupMembersViewEntity_.IDENTIFIER);
        final Join<GlobalGroupMembersViewEntity, GlobalGroupEntity> group = root.join(GlobalGroupMembersViewEntity_.GLOBAL_GROUP);
        final List<Predicate> predicates = new ArrayList<>();

        predicates.add(builder.equal(group.get(GlobalGroupEntity_.GROUP_CODE), criteria.getGroupCode()));

        if (StringUtils.isNotBlank(criteria.getQuery())) {
            predicates.add(searchPredicate(root, member, builder, criteria));
        }

        if (StringUtils.isNotBlank(criteria.getMemberClass())) {
            predicates.add(builder.equal(member.get(XRoadIdEntity_.MEMBER_CLASS), criteria.getMemberClass()));
        }
        if (StringUtils.isNotBlank(criteria.getInstance())) {
            predicates.add(builder.equal(member.get(XRoadIdEntity_.X_ROAD_INSTANCE), criteria.getInstance()));
        }
        if (!CollectionUtils.isEmpty(criteria.getCodes())) {
            predicates.add(member.get(XRoadIdEntity_.MEMBER_CODE).in(criteria.getCodes()));
        }
        if (!CollectionUtils.isEmpty(criteria.getSubsystems())) {
            predicates.add(member.get(XRoadIdEntity_.SUBSYSTEM_CODE).in(criteria.getSubsystems()));
        }
        if (!CollectionUtils.isEmpty(criteria.getTypes())) {
            predicates.add(member.get(XRoadIdEntity_.OBJECT_TYPE).in(criteria.getTypes()));
        }
        return builder.and(predicates.toArray(new Predicate[0]));
    }

    private static Predicate searchPredicate(Root<GlobalGroupMembersViewEntity> root,
                                             Join<GlobalGroupMembersViewEntity, ClientIdEntity> member,
                                             CriteriaBuilder builder,
                                             GlobalGroupMemberService.Criteria criteria) {
        return builder.or(
                caseInsensitiveLike(root, builder, criteria.getQuery(), root.get(GlobalGroupMembersViewEntity_.MEMBER_NAME)),
                caseInsensitiveLike(root, builder, criteria.getQuery(), member.get(XRoadIdEntity_.MEMBER_CODE)),
                caseInsensitiveLike(root, builder, criteria.getQuery(), member.get(XRoadIdEntity_.MEMBER_CLASS)),
                caseInsensitiveLike(root, builder, criteria.getQuery(), member.get(XRoadIdEntity_.SUBSYSTEM_CODE)),
                caseInsensitiveLike(root, builder, criteria.getQuery(), member.get(XRoadIdEntity_.X_ROAD_INSTANCE)),
                caseInsensitiveLike(root, builder, criteria.getQuery(), member.get(XRoadIdEntity_.OBJECT_TYPE))
        );
    }
}
