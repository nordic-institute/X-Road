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

import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import java.util.Optional;

import static org.niis.xroad.centralserver.restapi.entity.SecurityServer_.OWNER;
import static org.niis.xroad.centralserver.restapi.entity.SecurityServer_.SERVER_CODE;
import static org.niis.xroad.centralserver.restapi.entity.XRoadMember_.MEMBER_CLASS;
import static org.niis.xroad.centralserver.restapi.entity.XRoadMember_.MEMBER_CODE;
import static org.niis.xroad.centralserver.restapi.entity.XRoadMember_.NAME;


@Repository
public interface SecurityServerRepository extends
        PagingAndSortingRepository<SecurityServer, Integer>,
        JpaSpecificationExecutor<SecurityServer> {
    Optional<SecurityServer> findByOwnerAndServerCode(XRoadMember owner, String serverCode);

    static Specification<SecurityServer> multifieldSearch(String q) {
        if (q == null || q.isEmpty()) {
            return null;
        }
        return (root, query, builder) ->
                multifieldTextSearch(root, builder, q);
    }


    private static Predicate serverCodePredicate(Root<SecurityServer> root, CriteriaBuilder builder, String s) {
        return builder.like(
                builder.lower(root.get(SERVER_CODE)),
                builder.lower(builder.literal("%" + s + "%"))
        );
    }

    private static Predicate memberCodePredicate(Root<SecurityServer> root, CriteriaBuilder builder, String s) {
        return builder.like(
                builder.lower(root.join(OWNER).get(MEMBER_CODE)),
                builder.lower(builder.literal("%" + s + "%"))
        );
    }

    private static Predicate memberClassPredicate(Root<SecurityServer> root, CriteriaBuilder builder, String s) {
        return builder.like(
                builder.lower(root.join(OWNER).get(MEMBER_CLASS).get("code")),
                builder.lower(builder.literal("%" + s + "%"))
        );
    }

    private static Predicate memberNamePredicate(Root<SecurityServer> root, CriteriaBuilder builder, String s) {
        return builder.like(
                builder.lower(root.join(OWNER).get(NAME)),
                builder.lower(builder.literal("%" + s + "%"))
        );
    }

    private static Predicate multifieldTextSearch(Root<SecurityServer> root, CriteriaBuilder builder, String q) {
        return builder.or(
                memberNamePredicate(root, builder, q),
                memberClassPredicate(root, builder, q),
                memberCodePredicate(root, builder, q),
                serverCodePredicate(root, builder, q)

        );
    }

}
