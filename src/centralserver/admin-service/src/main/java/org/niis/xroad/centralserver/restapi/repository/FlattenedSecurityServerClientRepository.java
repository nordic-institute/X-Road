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

import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.centralserver.restapi.entity.FlattenedSecurityServerClientView;
import org.niis.xroad.centralserver.restapi.entity.FlattenedSecurityServerClientView_;
import org.niis.xroad.centralserver.restapi.entity.FlattenedServerClient_;
import org.niis.xroad.centralserver.restapi.entity.MemberClass_;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.entity.Subsystem;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;

import static org.niis.xroad.centralserver.restapi.repository.CriteriaBuilderUtil.caseInsensitiveLike;

@Repository
public interface FlattenedSecurityServerClientRepository extends
        PagingAndSortingRepository<FlattenedSecurityServerClientView, Long>,
        JpaSpecificationExecutor<FlattenedSecurityServerClientView> {

    Page<FlattenedSecurityServerClientView> findAll(
            Specification<FlattenedSecurityServerClientView> spec,
            Pageable pageable);

    List<FlattenedSecurityServerClientView> findAll();

    List<FlattenedSecurityServerClientView> findAll(Specification<FlattenedSecurityServerClientView> spec);

    List<FlattenedSecurityServerClientView> findAll(Sort sort);

    default Specification<FlattenedSecurityServerClientView> multiParameterSearch(SearchParameters params) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (params.getSecurityServerId() != null) {
                predicates.add(clientOfSecurityServerPredicate(root, builder,
                        params.getSecurityServerId().intValue()));
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

    static Specification<FlattenedSecurityServerClientView> instance(String s) {
        return (root, query, builder) -> {
            return instancePredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClientView> memberClass(String s) {
        return (root, query, builder) -> {
            return memberClassPredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClientView> memberCode(String s) {
        return (root, query, builder) -> {
            return memberCodePredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClientView> memberName(String s) {
        return (root, query, builder) -> {
            return memberNamePredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClientView> subsystemCode(String s) {
        return (root, query, builder) -> {
            return subsystemCodePredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClientView> member() {
        return (root, query, builder) -> {
            return memberPredicate(root, builder);
        };
    }

    static Specification<FlattenedSecurityServerClientView> subsystem() {
        return (root, query, builder) -> {
            return subsystemPredicate(root, builder);
        };
    }

    static Specification<FlattenedSecurityServerClientView> securityServerId(int id) {
        return (root, query, builder) -> {
            return clientOfSecurityServerPredicate(root, builder, id);
        };
    }

    static Specification<FlattenedSecurityServerClientView> multifieldSearch(String q) {
        return (root, query, builder) -> {
            return multifieldTextSearchPredicate(root, builder, q);
        };
    }

    private static Predicate memberPredicate(Root root, CriteriaBuilder builder) {
        return builder.equal(
                root.get(FlattenedSecurityServerClientView_.TYPE).as(String.class), XRoadMember.DISCRIMINATOR_VALUE);
    }
    private static Predicate subsystemPredicate(Root root, CriteriaBuilder builder) {
        return builder.equal(
                root.get(FlattenedSecurityServerClientView_.TYPE).as(String.class), Subsystem.DISCRIMINATOR_VALUE);
    }
    private static Predicate memberNamePredicate(Root root, CriteriaBuilder builder, String s) {
        return caseInsensitiveLike(root, builder, s, root.get(FlattenedSecurityServerClientView_.MEMBER_NAME));
    }
    private static Predicate subsystemCodePredicate(Root root, CriteriaBuilder builder, String s) {
        return caseInsensitiveLike(root, builder, s, root.get(FlattenedSecurityServerClientView_.SUBSYSTEM_CODE));
    }
    private static Predicate memberCodePredicate(Root root, CriteriaBuilder builder, String s) {
        return caseInsensitiveLike(root, builder, s, root.get(FlattenedSecurityServerClientView_.MEMBER_CODE));
    }
    private static Predicate memberClassPredicate(Root root, CriteriaBuilder builder, String s) {
        return caseInsensitiveLike(root, builder, s, root.get(FlattenedSecurityServerClientView_.MEMBER_CLASS)
                .get(MemberClass_.CODE));
    }
    private static Predicate instancePredicate(Root root, CriteriaBuilder builder, String s) {
        return caseInsensitiveLike(root, builder, s, root.get(FlattenedSecurityServerClientView_.XROAD_INSTANCE));
    }

    static Predicate clientOfSecurityServerPredicate(Root root, CriteriaBuilder builder, int id) {
        Join<FlattenedSecurityServerClientView, SecurityServer> securityServer
                = root.join(FlattenedSecurityServerClientView_.FLATTENED_SERVER_CLIENTS)
                      .join(FlattenedServerClient_.SECURITY_SERVER);
        return builder.equal(securityServer.get(FlattenedSecurityServerClientView_.ID), id);
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

    /**
     * Parameters that defined which clients are returned.
     * All given parameters must match (e.g. memberClass = GOV, memberCode = 123 will not return a client
     * with memberClass = GOV, memberCode = 456). Null / undefined parameters are ignored.
     */
    @Getter
    class SearchParameters {
        private String multifieldSearch;
        private String instanceSearch;
        private String memberNameSearch;
        private String memberClassSearch;
        private String memberCodeSearch;
        private String subsystemCodeSearch;
        private XRoadObjectType clientType;
        private Integer securityServerId;

        /**
         * Return clients that contain given parameter in member name. Case insensitive.
         */
        public SearchParameters setMemberNameSearch(String memberNameSearchParam) {
            this.memberNameSearch = memberNameSearchParam;
            return this;
        }

        /**
         * Return clients that contain given parameter in member name, member class, member code or
         * subsystem code. Case insensitive.
         */
        public SearchParameters setMultifieldSearch(String multifieldSearchParam) {
            this.multifieldSearch = multifieldSearchParam;
            return this;
        }

        /**
         * Return clients that contain given parameter in instance identifier. Case insensitive.
         */
        public SearchParameters setInstanceSearch(String instanceSearchParam) {
            this.instanceSearch = instanceSearchParam;
            return this;
        }

        /**
         * Return clients that contain given parameter in member class. Case insensitive.
         */
        public SearchParameters setMemberClassSearch(String memberClassSearchParam) {
            this.memberClassSearch = memberClassSearchParam;
            return this;
        }

        /**
         * Return clients that contain given parameter in member code. Case insensitive.
         */
        public SearchParameters setMemberCodeSearch(String memberCodeSearchParam) {
            this.memberCodeSearch = memberCodeSearchParam;
            return this;
        }

        /**
         * Return clients that contain given parameter in subsystem code. Case insensitive.
         */
        public SearchParameters setSubsystemCodeSearch(String subsystemCodeSearchParam) {
            this.subsystemCodeSearch = subsystemCodeSearchParam;
            return this;
        }

        /**
         * Return clients of given XRoadObjectType (either MEMBER or SUBSYSTEM).
         */
        public SearchParameters setClientType(XRoadObjectType clientTypeParam) {
            this.clientType = clientTypeParam;
            return this;
        }

        /**
         * Return clients that are clients of given security server
         * @param securityServerIdParam security server ID
         */
        public SearchParameters setSecurityServerId(Integer securityServerIdParam) {
            this.securityServerId = securityServerIdParam;
            return this;
        }
    }


}
