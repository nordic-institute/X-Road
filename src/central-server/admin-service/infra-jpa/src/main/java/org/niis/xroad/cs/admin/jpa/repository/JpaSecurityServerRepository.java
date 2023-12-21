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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import io.vavr.control.Option;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity_;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity_;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity_;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity_;
import org.niis.xroad.cs.admin.core.entity.XRoadIdEntity_;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity_;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.jpa.repository.util.CriteriaBuilderUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaSecurityServerRepository extends
        JpaRepository<SecurityServerEntity, Integer>, JpaSpecificationExecutor<SecurityServerEntity>,
        SecurityServerRepository {

    default Page<SecurityServerEntity> findAllByQuery(String query, Pageable pageable) {
        return findAll(multifieldSearch(query), pageable);
    }

    Option<SecurityServerEntity> findByOwnerIdAndServerCode(Integer ownerId, String serverCode);

    default Option<SecurityServerEntity> findBy(SecurityServerId serverId, ClientId clientId) {
        return Option.ofOptional(
                findOne(serverIdSpec(serverId).and(clientIdSpec(clientId)))
        );
    }

    default Option<SecurityServerEntity> findBy(SecurityServerId serverId) {
        return Option.ofOptional(
                findOne(serverIdSpec(serverId))
        );
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

        return builder.or(
                CriteriaBuilderUtil.caseInsensitiveLike(root, builder, q, root.get(SecurityServerEntity_.serverCode)),
                CriteriaBuilderUtil.caseInsensitiveLike(root, builder, q, owner.get(XRoadMemberEntity_.name)),
                CriteriaBuilderUtil.caseInsensitiveLike(root, builder, q, identifier.get(XRoadMemberEntity_.MEMBER_CLASS)),
                CriteriaBuilderUtil.caseInsensitiveLike(root, builder, q, identifier.get(XRoadMemberEntity_.MEMBER_CODE))
        );
    }

}
