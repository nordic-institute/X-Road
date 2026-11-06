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
package org.niis.xroad.cs.admin.jpa.repository;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.XRoadIdEntity_;
import org.niis.xroad.cs.admin.core.entity.MemberRow;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity_;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity_;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity_;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity_;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity_;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.jpa.repository.util.CriteriaBuilderUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaSecurityServerRepository extends
        JpaRepository<SecurityServerEntity, Integer>, JpaSpecificationExecutor<SecurityServerEntity>,
        SecurityServerRepository {

    default Page<SecurityServerEntity> findAllByQuery(String query, Pageable pageable) {
        return findAll(multifieldSearch(query), pageable);
    }

    Optional<SecurityServerEntity> findByOwnerIdAndServerCode(Integer ownerId, String serverCode);

    @Override
    @Query(value = "SELECT id FROM " + SecurityServerEntity.TABLE_NAME + " WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Integer> lockForUpdateById(Integer id);

    @Query(value = "SELECT s.id FROM " + SecurityServerEntity.TABLE_NAME + " s "
            + "JOIN " + SecurityServerClientEntity.TABLE_NAME + " o ON o.id = s.owner_id "
            + "JOIN identifier i ON i.id = o.server_client_id "
            + "WHERE i.object_type = 'MEMBER' AND i.xroad_instance = :xRoadInstance AND i.member_class = :memberClass "
            + "AND i.member_code = :memberCode AND s.server_code = :serverCode FOR UPDATE OF s", nativeQuery = true)
    Optional<Integer> lockForUpdateByOwnerAndServerCode(String xRoadInstance, String memberClass, String memberCode,
                                                        String serverCode);

    @Override
    default Optional<Integer> lockForUpdateBy(SecurityServerId serverId) {
        return lockForUpdateByOwnerAndServerCode(serverId.getXRoadInstance(), serverId.getMemberClass(),
                serverId.getMemberCode(), serverId.getServerCode());
    }

    @Override
    @Query("select distinct new org.niis.xroad.cs.admin.core.entity.MemberRow("
            + "m.id, mi.xRoadInstance, mi.memberClass, mi.memberCode) "
            + "from ServerClientEntity sc join sc.securityServerClient c join c.identifier ci, "
            + "XRoadMemberEntity m join m.identifier mi "
            + "where sc.securityServer.id = :securityServerId "
            + "and mi.xRoadInstance = ci.xRoadInstance and mi.memberClass = ci.memberClass and mi.memberCode = ci.memberCode")
    List<MemberRow> findServedMembers(Integer securityServerId);

    default Optional<SecurityServerEntity> findBy(SecurityServerId serverId, ClientId clientId) {
        return findOne(serverIdSpec(serverId).and(clientIdSpec(clientId)));
    }

    default Optional<SecurityServerEntity> findBy(SecurityServerId serverId) {
        return findOne(serverIdSpec(serverId));
    }

    default boolean existsBy(SecurityServerId serverId) {
        return exists(serverIdSpec(serverId));
    }

    default long count(SecurityServerId id) {
        return count(serverIdSpec(id));
    }

    static Specification<SecurityServerEntity> clientIdSpec(ClientId clientId) {

        return (root, query, builder) -> {
            Join<SecurityServerClientEntity, ClientIdEntity> cid = root
                    .join(SecurityServerEntity_.serverClients)
                    .join(ServerClientEntity_.securityServerClient)
                    .join(SecurityServerClientEntity_.identifier);

            Predicate pred = builder.and(
                    builder.equal(cid.get(XRoadIdEntity_.OBJECT_TYPE), clientId.getObjectType()),
                    builder.equal(cid.get(XRoadIdEntity_.X_ROAD_INSTANCE), clientId.getXRoadInstance()),
                    builder.equal(cid.get(XRoadIdEntity_.MEMBER_CLASS), clientId.getMemberClass()),
                    builder.equal(cid.get(XRoadIdEntity_.MEMBER_CODE), clientId.getMemberCode()));

            if (clientId.getSubsystemCode() != null) {
                pred = builder.and(pred,
                        builder.equal(cid.get(SubsystemEntity_.SUBSYSTEM_CODE), clientId.getSubsystemCode()));
            }

            return pred;
        };
    }

    static Specification<SecurityServerEntity> serverIdSpec(SecurityServerId serverId) {

        return (root, query, builder) -> {
            Predicate pred = builder.and(builder.equal(root.get(SecurityServerEntity_.serverCode), serverId.getServerCode()));

            Join<XRoadMemberEntity, ClientIdEntity> oid =
                    root.join(SecurityServerEntity_.owner).join(SecurityServerClientEntity_.identifier);

            pred = builder.and(pred,
                    builder.equal(oid.get(XRoadIdEntity_.OBJECT_TYPE), XRoadObjectType.MEMBER),
                    builder.equal(oid.get(XRoadIdEntity_.X_ROAD_INSTANCE), serverId.getXRoadInstance()),
                    builder.equal(oid.get(XRoadIdEntity_.MEMBER_CLASS), serverId.getMemberClass()),
                    builder.equal(oid.get(XRoadIdEntity_.MEMBER_CODE), serverId.getMemberCode()));
            return pred;
        };
    }

    static Specification<SecurityServerEntity> multifieldSearch(String q) {
        if (q == null || q.isEmpty()) {
            return null;
        }
        return (root, query, builder) -> multifieldSearchPredicate(root, builder, q);
    }

    private static Predicate multifieldSearchPredicate(Root<SecurityServerEntity> root, CriteriaBuilder builder, String q) {
        final Join<SecurityServerEntity, XRoadMemberEntity> owner = root.join(SecurityServerEntity_.owner);
        final Join<XRoadMemberEntity, ClientIdEntity> identifier =
                owner.join(SecurityServerClientEntity_.identifier);
        // XRoadMemberEntity is a SINGLE_TABLE subtype of SecurityServerClientEntity; an explicit treat() is
        // required to reach a subtype-only attribute such as "name" via this association.
        final Join<SecurityServerEntity, XRoadMemberEntity> ownerAsMember = builder.treat(owner, XRoadMemberEntity.class);

        return builder.or(
                CriteriaBuilderUtil.caseInsensitiveLike(builder, q, root.get(SecurityServerEntity_.serverCode)),
                CriteriaBuilderUtil.caseInsensitiveLike(builder, q, ownerAsMember.get(XRoadMemberEntity_.NAME)),
                CriteriaBuilderUtil.caseInsensitiveLike(builder, q, identifier.get(XRoadMemberEntity_.MEMBER_CLASS)),
                CriteriaBuilderUtil.caseInsensitiveLike(builder, q, identifier.get(XRoadMemberEntity_.MEMBER_CODE))
        );
    }

}
