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

import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.Subsystem;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import java.util.List;

@Repository
public interface SecurityServerClientRepository extends PagingAndSortingRepository<SecurityServerClient, Long>,
        JpaSpecificationExecutor<SecurityServerClient> {
    List<SecurityServerClient> findAll(Specification<SecurityServerClient> spec);

    List<SecurityServerClient> findAll();

    static Specification<SecurityServerClient> clientWithMemberName(String name) {
        return (root, query, builder) -> {
            return builder.or(
                    subsystemWithMemberNamePredicate(root, builder, name),
                    memberWithMemberNamePredicate(root, builder, name)
            );
        };
    }

    static Specification<SecurityServerClient> memberWithMemberName(String s) {
        return (root, query, builder) -> {
            return memberWithMemberNamePredicate(root, builder, s);
        };
    }

    static Specification<SecurityServerClient> subsystemWithMembername(String s) {
        return (root, query, builder) -> {
            return subsystemWithMemberNamePredicate(root, builder, s);
        };
    }

    static Specification<SecurityServerClient> member() {
        return (root, query, builder) -> {
            return memberPredicate(root, builder);
        };
    }

    static Specification<SecurityServerClient> subsystem() {
        return (root, query, builder) -> {
            return subsystemPredicate(root, builder);
        };
    }

    private static Predicate memberPredicate(Root root, CriteriaBuilder builder) {
        return builder.equal(builder.treat(root, XRoadMember.class).type(), XRoadMember.class);
    }

    private static Predicate subsystemPredicate(Root root, CriteriaBuilder builder) {
        return builder.equal(builder.treat(root, Subsystem.class).type(), Subsystem.class);
    }


    private static Predicate memberWithMemberNamePredicate(Root root, CriteriaBuilder builder, String name) {
        return builder.and(
                memberPredicate(root, builder),
                builder.equal(builder.treat(root, XRoadMember.class).get("name"), name)
        );
    }

    private static Predicate subsystemWithMemberNamePredicate(Root root, CriteriaBuilder builder, String name) {
        return builder.and(
                subsystemPredicate(root, builder),
                builder.equal(builder.treat(root, Subsystem.class)
                                     .join("xroadMember", JoinType.LEFT)
                                     .get("name"), name)
        );
    }

}
