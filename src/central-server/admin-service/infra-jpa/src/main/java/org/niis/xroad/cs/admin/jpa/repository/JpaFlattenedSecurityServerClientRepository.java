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
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.core.entity.FlattenedSecurityServerClientViewEntity;
import org.niis.xroad.cs.admin.core.entity.FlattenedSecurityServerClientViewEntity_;
import org.niis.xroad.cs.admin.core.entity.FlattenedServerClientEntity_;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity_;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMemberEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMemberEntity_;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity_;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity_;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadIdEntity_;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.repository.FlattenedSecurityServerClientRepository;
import org.niis.xroad.cs.admin.jpa.repository.util.CriteriaBuilderUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface JpaFlattenedSecurityServerClientRepository extends
        PagingAndSortingRepository<FlattenedSecurityServerClientViewEntity, Long>,
        JpaSpecificationExecutor<FlattenedSecurityServerClientViewEntity>,
        FlattenedSecurityServerClientRepository {

    default Page<FlattenedSecurityServerClientViewEntity> findAll(
            ClientService.SearchParameters params,
            Pageable pageable) {
        return findAll(multiParameterSearch(params), pageable);
    }

    default List<FlattenedSecurityServerClientViewEntity> findAll(ClientService.SearchParameters params) {
        return findAll(multiParameterSearch(params));
    }

    Page<FlattenedSecurityServerClientViewEntity> findAll(
            Specification<FlattenedSecurityServerClientViewEntity> spec,
            Pageable pageable);

    List<FlattenedSecurityServerClientViewEntity> findAll();

    List<FlattenedSecurityServerClientViewEntity> findAll(Specification<FlattenedSecurityServerClientViewEntity> spec);

    List<FlattenedSecurityServerClientViewEntity> findAll(Sort sort);

    @SuppressWarnings("java:S3776")
    default Specification<FlattenedSecurityServerClientViewEntity> multiParameterSearch(ClientService.SearchParameters params) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (params.getSecurityServerId() != null) {
                predicates.add(clientOfSecurityServerPredicate(root, builder,
                        params.getSecurityServerId(), params.getSecurityServerEnabled()));
            }
            if (!StringUtils.isBlank(params.getMultifieldSearch())) {
                predicates.add(multifieldTextSearchPredicate(root, builder,
                        params.getMultifieldSearch()));
            }
            if (!StringUtils.isBlank(params.getMemberCodeSearch())) {
                predicates.add(memberCodePredicate(root, builder,
                        params.getMemberCodeSearch()));
            }
            if (!StringUtils.isBlank(params.getMemberClassSearch())) {
                predicates.add(memberClassPredicate(root, builder,
                        params.getMemberClassSearch()));
            }
            if (!StringUtils.isBlank(params.getInstanceSearch())) {
                predicates.add(instancePredicate(root, builder,
                        params.getInstanceSearch()));
            }
            if (!StringUtils.isBlank(params.getSubsystemCodeSearch())) {
                predicates.add(subsystemCodePredicate(root, builder,
                        params.getSubsystemCodeSearch()));
            }
            if (!StringUtils.isBlank(params.getMemberNameSearch())) {
                predicates.add(memberNamePredicate(root, builder,
                        params.getMemberNameSearch()));
            }
            if (params.getExcludingGroup() != null) {
                predicates.add(clientNotPartOfGroupPredicate(root, builder,
                        params.getExcludingGroup()));
            }
            if (params.getClientType() != null) {
                switch (params.getClientType()) {
                    case MEMBER:
                        predicates.add(memberPredicate(root, builder));
                        break;
                    case SUBSYSTEM:
                        predicates.add(subsystemPredicate(root, builder));
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid client type " + params.getClientType());
                }
            }
            if (predicates.isEmpty()) {
                predicates.add(idIsNotNull(root, builder));
            }
            return builder.and(predicates.toArray(new Predicate[predicates.size()]));
        };
    }

    static Specification<FlattenedSecurityServerClientViewEntity> instance(String s) {
        return (root, query, builder) -> {
            return instancePredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClientViewEntity> memberClass(String s) {
        return (root, query, builder) -> {
            return memberClassPredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClientViewEntity> memberCode(String s) {
        return (root, query, builder) -> {
            return memberCodePredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClientViewEntity> memberName(String s) {
        return (root, query, builder) -> {
            return memberNamePredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClientViewEntity> subsystemCode(String s) {
        return (root, query, builder) -> {
            return subsystemCodePredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClientViewEntity> member() {
        return (root, query, builder) -> {
            return memberPredicate(root, builder);
        };
    }

    static Specification<FlattenedSecurityServerClientViewEntity> subsystem() {
        return (root, query, builder) -> {
            return subsystemPredicate(root, builder);
        };
    }

    static Specification<FlattenedSecurityServerClientViewEntity> securityServerId(int id) {
        return (root, query, builder) -> {
            return clientOfSecurityServerPredicate(root, builder, id, null);
        };
    }

    static Specification<FlattenedSecurityServerClientViewEntity> multifieldSearch(String q) {
        return (root, query, builder) -> {
            return multifieldTextSearchPredicate(root, builder, q);
        };
    }

    private static Predicate memberPredicate(Root root, CriteriaBuilder builder) {
        return builder.equal(
                root.get(FlattenedSecurityServerClientViewEntity_.TYPE).as(String.class), XRoadMemberEntity.DISCRIMINATOR_VALUE);
    }

    private static Predicate subsystemPredicate(Root root, CriteriaBuilder builder) {
        return builder.equal(
                root.get(FlattenedSecurityServerClientViewEntity_.TYPE).as(String.class), SubsystemEntity.DISCRIMINATOR_VALUE);
    }

    private static Predicate memberNamePredicate(Root root, CriteriaBuilder builder, String s) {
        return CriteriaBuilderUtil.caseInsensitiveLike(root, builder, s, root.get(FlattenedSecurityServerClientViewEntity_.MEMBER_NAME));
    }

    private static Predicate subsystemCodePredicate(Root root, CriteriaBuilder builder, String s) {
        return CriteriaBuilderUtil.caseInsensitiveLike(root, builder, s, root.get(FlattenedSecurityServerClientViewEntity_.SUBSYSTEM_CODE));
    }

    private static Predicate memberCodePredicate(Root root, CriteriaBuilder builder, String s) {
        return CriteriaBuilderUtil.caseInsensitiveLike(root, builder, s, root.get(FlattenedSecurityServerClientViewEntity_.MEMBER_CODE));
    }

    private static Predicate memberClassPredicate(Root root, CriteriaBuilder builder, String s) {
        return CriteriaBuilderUtil.caseInsensitiveLike(root, builder, s, root.get(FlattenedSecurityServerClientViewEntity_.MEMBER_CLASS)
                .get(MemberClassEntity_.CODE));
    }

    private static Predicate instancePredicate(Root root, CriteriaBuilder builder, String s) {
        return CriteriaBuilderUtil.caseInsensitiveLike(root, builder, s, root.get(FlattenedSecurityServerClientViewEntity_.XROAD_INSTANCE));
    }

    static Predicate clientOfSecurityServerPredicate(
            Root<FlattenedSecurityServerClientViewEntity> root, CriteriaBuilder builder, int id, Boolean enabled) {
        var serverClients = root
                .join(FlattenedSecurityServerClientViewEntity_.flattenedServerClients);
        var securityServer = serverClients
                .join(FlattenedServerClientEntity_.securityServer);

        var securityServerIdEquals = builder.equal(securityServer.get(SecurityServerEntity_.id), id);
        if (enabled != null) {
            return builder.and(securityServerIdEquals, builder.equal(serverClients.get(FlattenedServerClientEntity_.enabled), enabled));
        }
        return securityServerIdEquals;
    }

    static Predicate clientNotPartOfGroupPredicate(Root root, CriteriaBuilder builder, String groupCode) {
        var criteriaQuery = builder.createQuery();

        var memberClass = root.join(FlattenedSecurityServerClientViewEntity_.MEMBER_CLASS);

        var subquery = criteriaQuery.subquery(Integer.class);
        var globalGroupMember = subquery.from(GlobalGroupMemberEntity.class);
        var identifier = globalGroupMember.join(GlobalGroupMemberEntity_.IDENTIFIER);
        var globalGroup = globalGroupMember.join(GlobalGroupMemberEntity_.globalGroup);
        subquery
                .select(identifier.get(XRoadIdEntity_.ID))
                .where(
                        builder.equal(globalGroup.get(GlobalGroupEntity_.GROUP_CODE), groupCode),
                        builder.equal(identifier.get(XRoadIdEntity_.X_ROAD_INSTANCE),
                                root.get(FlattenedSecurityServerClientViewEntity_.XROAD_INSTANCE)),
                        builder.equal(identifier.get(XRoadIdEntity_.MEMBER_CLASS),
                                memberClass.get(MemberClassEntity_.CODE)),
                        builder.equal(identifier.get(XRoadIdEntity_.MEMBER_CODE),
                                root.get(FlattenedSecurityServerClientViewEntity_.MEMBER_CODE)),
                        builder.or(
                                builder.and(
                                        builder.isNull(identifier.get(XRoadIdEntity_.SUBSYSTEM_CODE)),
                                        builder.isNull(root.get(FlattenedSecurityServerClientViewEntity_.SUBSYSTEM_CODE))
                                ),
                                builder.equal(identifier.get(XRoadIdEntity_.SUBSYSTEM_CODE),
                                        root.get(FlattenedSecurityServerClientViewEntity_.SUBSYSTEM_CODE))
                        )
                );

        return builder.not(builder.exists(subquery));
    }

    static Predicate multifieldTextSearchPredicate(Root root, CriteriaBuilder builder, String q) {
        return builder.or(
                memberNamePredicate(root, builder, q),
                memberClassPredicate(root, builder, q),
                memberCodePredicate(root, builder, q),
                subsystemCodePredicate(root, builder, q)
        );
    }

    /**
     * For "find all" when no search parameters are defined
     */
    private static Predicate idIsNotNull(Root root, CriteriaBuilder builder) {
        return builder.isNotNull(root.get("id"));
    }
}
