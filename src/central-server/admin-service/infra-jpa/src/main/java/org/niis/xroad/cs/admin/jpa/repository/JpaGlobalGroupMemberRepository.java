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
package org.niis.xroad.cs.admin.jpa.repository;

import org.niis.xroad.cs.admin.api.service.GlobalGroupService;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMemberEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMemberEntity_;
import org.niis.xroad.cs.admin.core.entity.XRoadIdEntity_;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupMemberRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface JpaGlobalGroupMemberRepository
        extends JpaRepository<GlobalGroupMemberEntity, Integer>, JpaSpecificationExecutor<GlobalGroupMemberEntity>,
        GlobalGroupMemberRepository {

    List<GlobalGroupMemberEntity> findByGlobalGroupGroupCode(String groupCode);

    default List<GlobalGroupMemberEntity> findMemberGroups(ee.ria.xroad.common.identifier.ClientId clientId) {
        return findAll(findSpecification(GlobalGroupService.Criteria.builder()
                .instance(clientId.getXRoadInstance())
                .memberClass(clientId.getMemberClass())
                .code(clientId.getMemberCode())
                .subsystemCode(clientId.getSubsystemCode())
                .build()));
    }

    private static Specification<GlobalGroupMemberEntity> findSpecification(GlobalGroupService.Criteria criteria) {
        return (root, query, builder) -> findPredicate(root, builder, criteria);
    }

    private static Predicate findPredicate(Root<GlobalGroupMemberEntity> root, CriteriaBuilder builder,
                                           GlobalGroupService.Criteria criteria) {
        final Join<GlobalGroupMemberEntity, ClientIdEntity> member = root.join(GlobalGroupMemberEntity_.IDENTIFIER);
        final List<Predicate> predicates = new ArrayList<>();

        predicates.add(builder.equal(member.get(XRoadIdEntity_.MEMBER_CLASS), criteria.getMemberClass()));
        predicates.add(builder.equal(member.get(XRoadIdEntity_.X_ROAD_INSTANCE), criteria.getInstance()));
        predicates.add(builder.equal(member.get(XRoadIdEntity_.MEMBER_CODE), criteria.getCode()));
        if (criteria.getSubsystemCode() != null) {
            predicates.add(builder.equal(member.get(XRoadIdEntity_.SUBSYSTEM_CODE), criteria.getSubsystemCode()));
        }

        return builder.and(predicates.toArray(new Predicate[0]));
    }
}
