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

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.centralserver.restapi.entity.FlattenedSecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
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

@Repository
public interface FlattenedSecurityServerClientRepository extends
        PagingAndSortingRepository<FlattenedSecurityServerClient, Long>,
        JpaSpecificationExecutor<FlattenedSecurityServerClient> {

    Page<FlattenedSecurityServerClient> findAll(
            Specification<FlattenedSecurityServerClient> spec,
            Pageable pageable);

    List<FlattenedSecurityServerClient> findAll();

    List<FlattenedSecurityServerClient> findAll(Specification<FlattenedSecurityServerClient> spec);

    List<FlattenedSecurityServerClient> findAll(Sort sort);


    /**
     * TO DO: document params well
     */
    static Specification multiParameterSearch(SearchParameters params) {
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
            if (!StringUtils.isBlank(params.getClientType())) {
                switch (params.getClientType()) {
                    case "XRoadMember":
                        predicates.add(memberPredicate(root, builder));
                        break;
                    case "Subsystem":
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
    
    static Specification<FlattenedSecurityServerClient> instance(String s) {
        return (root, query, builder) -> {
            return instancePredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClient> memberClass(String s) {
        return (root, query, builder) -> {
            return memberClassPredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClient> memberCode(String s) {
        return (root, query, builder) -> {
            return memberCodePredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClient> subsystemCode(String s) {
        return (root, query, builder) -> {
            return subsystemCodePredicate(root, builder, s);
        };
    }

    static Specification<FlattenedSecurityServerClient> clientWithMemberName(String name) {
        return (root, query, builder) -> {
            return memberNamePredicate(root, builder, name);
        };
    }

    static Specification<FlattenedSecurityServerClient> memberWithMemberName(String s) {
        return (root, query, builder) -> {
            return builder.and(
                    memberPredicate(root, builder),
                    memberNamePredicate(root, builder, s)
            );
        };
    }

    static Specification<FlattenedSecurityServerClient> subsystemWithMembername(String s) {
        return (root, query, builder) -> {
            return builder.and(
                    subsystemPredicate(root, builder),
                    memberNamePredicate(root, builder, s)
            );
        };
    }

    static Specification<FlattenedSecurityServerClient> member() {
        return (root, query, builder) -> {
            return memberPredicate(root, builder);
        };
    }

    static Specification<FlattenedSecurityServerClient> subsystem() {
        return (root, query, builder) -> {
            return subsystemPredicate(root, builder);
        };
    }

    static Specification<FlattenedSecurityServerClient> securityServerId(int id) {
        return (root, query, builder) -> {
            return clientOfSecurityServerPredicate(root, builder, id);
        };
    }

    static Specification<FlattenedSecurityServerClient> multifieldSearch(String q) {
        return (root, query, builder) -> {
            return multifieldTextSearchPredicate(root, builder, q);
        };
    }

    private static Predicate memberPredicate(Root root, CriteriaBuilder builder) {
        return builder.equal(root.get("type"), "XRoadMember");
    }
    private static Predicate subsystemPredicate(Root root, CriteriaBuilder builder) {
        return builder.equal(root.get("type"), "Subsystem");
    }
    private static Predicate memberNamePredicate(Root root, CriteriaBuilder builder, String s) {
        return builder.like(
                builder.lower(root.get("memberName")),
                builder.lower(builder.literal("%" + s + "%"))
        );
    }
    private static Predicate subsystemCodePredicate(Root root, CriteriaBuilder builder, String s) {
        return builder.like(
                builder.lower(root.get("subsystemCode")),
                builder.lower(builder.literal("%" + s + "%"))
        );
    }
    private static Predicate memberCodePredicate(Root root, CriteriaBuilder builder, String s) {
        return builder.like(
                builder.lower(root.get("memberCode")),
                builder.lower(builder.literal("%" + s + "%"))
        );
    }
    private static Predicate memberClassPredicate(Root root, CriteriaBuilder builder, String s) {
        return builder.like(
                builder.lower(root.get("memberClass").get("code")),
                builder.lower(builder.literal("%" + s + "%"))
        );
    }
    private static Predicate instancePredicate(Root root, CriteriaBuilder builder, String s) {
        return builder.like(
                builder.lower(root.get("xroadInstance")),
                builder.lower(builder.literal("%" + s + "%"))
        );
    }

    static Predicate clientOfSecurityServerPredicate(Root root, CriteriaBuilder builder, int id) {
        Join<FlattenedSecurityServerClient, SecurityServer> securityServer
                = root.join("flattenedServerClients").join("securityServer");
        return builder.equal(securityServer.get("id"), id);
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

    @Getter
    class SearchParameters {
        private String multifieldSearch;
        private String instanceSearch;
        private String memberClassSearch;
        private String memberCodeSearch;
        private String subsystemCodeSearch;
        private String clientType;
        private Integer securityServerId;

        public SearchParameters setMultifieldSearch(String multifieldSearchParam) {
            this.multifieldSearch = multifieldSearchParam;
            return this;
        }

        public SearchParameters setInstanceSearch(String instanceSearchParam) {
            this.instanceSearch = instanceSearchParam;
            return this;
        }

        public SearchParameters setMemberClassSearch(String memberClassSearchParam) {
            this.memberClassSearch = memberClassSearchParam;
            return this;
        }

        public SearchParameters setMemberCodeSearch(String memberCodeSearchParam) {
            this.memberCodeSearch = memberCodeSearchParam;
            return this;
        }

        public SearchParameters setSubsystemCodeSearch(String subsystemCodeSearchParam) {
            this.subsystemCodeSearch = subsystemCodeSearchParam;
            return this;
        }

        public SearchParameters setClientType(String clientTypeParam) {
            this.clientType = clientTypeParam;
            return this;
        }

        public SearchParameters setSecurityServerId(Integer securityServerIdParam) {
            this.securityServerId = securityServerIdParam;
            return this;
        }
    }


}
