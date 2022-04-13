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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient_;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer_;
import org.niis.xroad.centralserver.restapi.entity.ServerClient_;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember_;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import java.util.Optional;

import static org.niis.xroad.centralserver.restapi.repository.CriteriaBuilderUtil.caseInsensitiveLike;

@Repository
public interface SecurityServerRepository extends
        JpaRepository<SecurityServer, Integer>, JpaSpecificationExecutor<SecurityServer> {

    String X_ROAD_INSTANCE = "xRoadInstance";
    String TYPE = "type";

    Optional<SecurityServer> findByOwnerAndServerCode(XRoadMember owner, String serverCode);

    default Optional<SecurityServer> findBy(SecurityServerId serverId, ClientId clientId) {
        return findOne(serverIdSpec(serverId).and(clientIdSpec(clientId)));
    }

    default Optional<SecurityServer> findBy(SecurityServerId serverId) {
        return findOne(serverIdSpec(serverId));
    }

    static Specification<SecurityServer> clientIdSpec(ClientId clientId) {

        return (root, query, builder) -> {
            var cid = root
                    .join(SecurityServer_.serverClients)
                    .join(ServerClient_.securityServerClient)
                    .join(SecurityServerClient_.identifier);

            var pred = builder.and(
                    builder.equal(cid.get(TYPE), clientId.getObjectType()),
                    builder.equal(cid.get(X_ROAD_INSTANCE), clientId.getXRoadInstance()),
                    builder.equal(cid.get(XRoadMember_.MEMBER_CLASS), clientId.getMemberClass()),
                    builder.equal(cid.get(XRoadMember_.MEMBER_CODE), clientId.getMemberCode()));

            if (clientId.getSubsystemCode() != null) {
                pred = builder.and(pred, builder.equal(cid.get("subsystemCode"), clientId.getSubsystemCode()));
            }

            return pred;
        };
    }

    static Specification<SecurityServer> serverIdSpec(SecurityServerId serverId) {

        return (root, query, builder) -> {
            var pred = builder.and(builder.equal(root.get(SecurityServer_.serverCode), serverId.getServerCode()));

            var oid = root.join(SecurityServer_.owner).join(SecurityServerClient_.identifier);

            pred = builder.and(pred, builder.equal(oid.get(TYPE), XRoadObjectType.MEMBER),
                    builder.equal(oid.get(X_ROAD_INSTANCE), serverId.getXRoadInstance()),
                    builder.equal(oid.get(XRoadMember_.MEMBER_CLASS), serverId.getMemberClass()),
                    builder.equal(oid.get(XRoadMember_.MEMBER_CODE), serverId.getMemberCode()));
            return pred;
        };
    }

    static Specification<SecurityServer> multifieldSearch(String q) {
        if (q == null || q.isEmpty()) {
            return null;
        }
        return (root, query, builder) -> multifieldSearchPredicate(root, builder, q);
    }

    private static Predicate multifieldSearchPredicate(Root<SecurityServer> root, CriteriaBuilder builder, String q) {
        final var owner = root.join(SecurityServer_.owner);
        final var identifier = owner.join(SecurityServerClient_.identifier);

        return builder.or(
                caseInsensitiveLike(root, builder, q, root.get(SecurityServer_.serverCode)),
                caseInsensitiveLike(root, builder, q, owner.get(XRoadMember_.name)),
                caseInsensitiveLike(root, builder, q, identifier.get(XRoadMember_.MEMBER_CLASS)),
                caseInsensitiveLike(root, builder, q, identifier.get(XRoadMember_.MEMBER_CODE))
        );
    }

}
