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
import ee.ria.xroad.common.identifier.XRoadObjectType;

import io.vavr.control.Option;
import org.niis.xroad.centralserver.restapi.entity.ClientId_;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient_;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;

import java.util.Optional;

public interface SecurityServerClientRepository<T extends SecurityServerClient> extends
        JpaRepository<T, Integer>,
        JpaSpecificationExecutor<T> {

    default Option<T> findOneBy(ClientId id) {
        return findOneBy(id, null);
    }

    default Option<T> findOneBy(ClientId id, XRoadObjectType explicitType) {
        return Option.ofOptional(findOne(clientIdSpec(id, explicitType)));
    }

    default Specification<T> clientIdSpec(ClientId id) {
        return clientIdSpec(id, null);
    }
    default Specification<T> clientIdSpec(ClientId id, XRoadObjectType explicitType) {
        return (root, query, builder) -> {
            Join<T, org.niis.xroad.centralserver.restapi.entity.ClientId> cid =
                    root.join(SecurityServerClient_.identifier);
            XRoadObjectType xroadObjectType = Optional.ofNullable(explicitType).orElseGet(id::getObjectType);
            Predicate predicate = builder.and(
                    builder.equal(cid.get(ClientId_.OBJECT_TYPE), xroadObjectType),
                    builder.equal(cid.get(ClientId_.X_ROAD_INSTANCE), id.getXRoadInstance()),
                    builder.equal(cid.get(ClientId_.MEMBER_CLASS), id.getMemberClass()),
                    builder.equal(cid.get(ClientId_.MEMBER_CODE), id.getMemberCode()));

            boolean expectedToBeSubsystemType = xroadObjectType == XRoadObjectType.SUBSYSTEM;
            if (expectedToBeSubsystemType) {
                boolean hasNoSubsystemCode = id.getSubsystemCode() == null;
                if (hasNoSubsystemCode) {
                    throw new RuntimeException("Subsystem code is null");
                }

                predicate = builder.and(predicate,
                        builder.equal(cid.get(ClientId_.SUBSYSTEM_CODE), id.getSubsystemCode()));
            }

            return predicate;
        };
    }
}
