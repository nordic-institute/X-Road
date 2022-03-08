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

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestType;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.niis.xroad.centralserver.restapi.entity.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.centralserver.restapi.entity.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.centralserver.restapi.entity.ClientDeletionRequest;
import org.niis.xroad.centralserver.restapi.entity.ClientRegistrationRequest;
import org.niis.xroad.centralserver.restapi.entity.OwnerChangeRequest;
import org.niis.xroad.centralserver.restapi.entity.Request;
import org.niis.xroad.centralserver.restapi.entity.RequestProcessing_;
import org.niis.xroad.centralserver.restapi.entity.RequestWithProcessing;
import org.niis.xroad.centralserver.restapi.entity.RequestWithProcessing_;
import org.niis.xroad.centralserver.restapi.entity.Request_;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestRepository<T extends Request>
        extends JpaRepository<T, Integer>, JpaSpecificationExecutor<T> {

    static Specification<Request> findSpec(Origin origin, ManagementRequestType type,
            ManagementRequestStatus status, SecurityServerId serverId) {

        final Class<? extends Request> entityType;
        if (type != null) {
            switch (type) {
                case AUTH_CERT_REGISTRATION_REQUEST:
                    entityType = AuthenticationCertificateRegistrationRequest.class;
                    break;
                case AUTH_CERT_DELETION_REQUEST:
                    entityType = AuthenticationCertificateDeletionRequest.class;
                    break;
                case CLIENT_REGISTRATION_REQUEST:
                    entityType = ClientRegistrationRequest.class;
                    break;
                case CLIENT_DELETION_REQUEST:
                    entityType = ClientDeletionRequest.class;
                    break;
                case OWNER_CHANGE_REQUEST:
                    entityType = OwnerChangeRequest.class;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid type " + type);
            }
        } else {
            entityType = null;
        }

        return (root, query, builder) -> {
            var pred = builder.conjunction();
            if (origin != null) {
                pred = builder.and(pred, builder.equal(root.get(Request_.origin), origin));
            }
            if (entityType != null) {
                pred = builder.and(pred, builder.equal(root.type(), entityType));
            }
            if (status != null) {
                var processing = builder
                        .treat(root, RequestWithProcessing.class)
                        .join(RequestWithProcessing_.requestProcessing);
                pred = builder.and(pred, builder.equal(processing.get(RequestProcessing_.status), status));
            }
            if (serverId != null) {
                var sid = root.join(Request_.securityServerId);
                pred = builder.and(pred,
                        builder.equal(sid.get("type"), serverId.getObjectType()),
                        builder.equal(sid.get("xRoadInstance"), serverId.getXRoadInstance()),
                        builder.equal(sid.get("memberClass"), serverId.getMemberClass()),
                        builder.equal(sid.get("memberCode"), serverId.getMemberCode()),
                        builder.equal(sid.get("serverCode"), serverId.getServerCode()));
            }
            return pred;
        };
    }
}
