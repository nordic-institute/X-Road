package org.niis.xroad.centralserver.restapi.repository;

import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.Predicate;

import java.util.List;

@Repository
public interface XRoadMemberRepository2 extends PagingAndSortingRepository<XRoadMember, Long>,
        JpaSpecificationExecutor<XRoadMember> {

    List<XRoadMember> findAll(Specification<XRoadMember> spec);

    List<XRoadMember> findAll();

    // example Specification
    static Specification<XRoadMember> nameHas(String s) {
        return (root, query, builder) -> {
            // https://stackoverflow.com/a/4591615/1469083
            Predicate pred = builder.like(
                    builder.lower(root.get("name")),
                    "%" + s.toLowerCase() + "%"
            );
            return pred;
        };
    }
}
