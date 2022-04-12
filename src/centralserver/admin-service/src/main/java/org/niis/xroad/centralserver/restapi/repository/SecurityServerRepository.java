/**
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
package org.niis.xroad.centralserver.restapi.repository;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient_;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer_;
import org.niis.xroad.centralserver.restapi.entity.ServerClient_;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecurityServerRepository extends
        JpaRepository<SecurityServer, Integer>, JpaSpecificationExecutor<SecurityServer> {
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
                    builder.equal(cid.get("type"), clientId.getObjectType()),
                    builder.equal(cid.get("xRoadInstance"), clientId.getXRoadInstance()),
                    builder.equal(cid.get("memberClass"), clientId.getMemberClass()),
                    builder.equal(cid.get("memberCode"), clientId.getMemberCode()));

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

            pred = builder.and(pred, builder.equal(oid.get("type"), XRoadObjectType.MEMBER),
                    builder.equal(oid.get("xRoadInstance"), serverId.getXRoadInstance()),
                    builder.equal(oid.get("memberClass"), serverId.getMemberClass()),
                    builder.equal(oid.get("memberCode"), serverId.getMemberCode()));
            return pred;
        };
    }
}
