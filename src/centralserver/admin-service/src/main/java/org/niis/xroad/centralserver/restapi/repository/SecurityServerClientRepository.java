package org.niis.xroad.centralserver.restapi.repository;

import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import java.util.List;

@Repository
public interface SecurityServerClientRepository extends PagingAndSortingRepository<SecurityServerClient, Long>,
        JpaSpecificationExecutor<SecurityServerClient> {
    List<SecurityServerClient> findAll(Specification<SecurityServerClient> spec);

    List<SecurityServerClient> findAll();

    // example Specification
    static Specification<SecurityServerClient> nameHas(String s) {
        return (root, query, builder) -> {
            // https://stackoverflow.com/a/4591615/1469083
            Predicate pred = builder.like(
                    builder.lower(root.get("name")),
                    "%" + s.toLowerCase() + "%"
            );
            return pred;
        };
    }

    // example Specification with subclass matching
    // https://stackoverflow.com/a/34391498/1469083
    static Specification<SecurityServerClient> nameIs(String s) {
        return (root, query, builder) -> {
            Predicate pred = builder.like(
                    ((Root<XRoadMember>) (Root<?>) root).get("name"), "%" + s + "%"
            );
            return pred;
        };
    }

}
