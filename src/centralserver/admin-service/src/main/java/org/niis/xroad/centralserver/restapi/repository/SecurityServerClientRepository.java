package org.niis.xroad.centralserver.restapi.repository;

import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.Subsystem;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import java.util.List;

@Repository
public interface SecurityServerClientRepository extends PagingAndSortingRepository<SecurityServerClient, Long>,
        JpaSpecificationExecutor<SecurityServerClient> {
    List<SecurityServerClient> findAll(Specification<SecurityServerClient> spec);

    List<SecurityServerClient> findAll();

    static Specification<SecurityServerClient> clientWithMemberName(String s) {
        return (root, query, builder) -> {
            Predicate pred = builder.or(
                    subsystemWithMemberNamePredicate(root, builder, s),
                    memberWithMemberNamePredicate(root, builder, s)
            );
            return pred;
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

    private static Predicate memberWithMemberNamePredicate(Root root, CriteriaBuilder builder, String name) {
        Predicate isMember = builder.equal(root.type(), XRoadMember.class);
        Predicate memberMemberName = builder
                .equal((builder.treat(root, XRoadMember.class).get("name")), name);
        Predicate pred = builder.and(isMember, memberMemberName);
        return pred;
    }

    private static Predicate subsystemWithMemberNamePredicate(Root root, CriteriaBuilder builder, String name) {
        Predicate isSubsystem = builder.equal(root.type(), Subsystem.class);
        Predicate subsystemMemberName = builder
                .equal((builder.treat(root, Subsystem.class).get("xroadMember")).get("name"), name);
        Predicate pred = builder.and(isSubsystem, subsystemMemberName);
        return pred;
    }



    /**
     * ******************************************************************************
     * Misc experiments....
     * ******************************************************************************
     */

    static Specification<SecurityServerClient> isSubsystemAndCodeIs(String s) {
        return (root, query, builder) -> {
            Predicate isSubsystem = builder.equal(root.type(), Subsystem.class);
            Predicate subsystemCode = builder
                    .equal((builder.treat(root, Subsystem.class).get("subsystemCode")), s);
            Predicate pred = builder.and(isSubsystem, subsystemCode);
            return pred;
        };
    }


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
    static Specification<SecurityServerClient> isSubsystem() {
        return (root, query, builder) -> {
            Predicate pred = builder.equal(root.type(), Subsystem.class);
            return pred;
        };
    }

    static Specification<SecurityServerClient> isSubsystemAndMembernameIs(String s) {
        return (root, query, builder) -> {
            Predicate isSubsystem = builder.equal(root.type(), Subsystem.class);
            Predicate memberNameIs = builder.like(
                    ((Root<Subsystem>) ((Root<?>) root)).get("xroadMember").<String>get("name"),
                    "%" + s + "%");
            Predicate pred = builder.and(isSubsystem, memberNameIs);
            return pred;
        };
    }

//    static Specification<SecurityServerClient> isSubsystemAndMembernameIs(String s) {
//        return (root, query, builder) -> {
//            Predicate isSubsystem = builder.equal(root.type(), Subsystem.class);
//            Predicate memberNameIs = builder.like(
//                    root.get("xroadMember").<String>get("name"),
//                    "%" + s + "%");
//            Predicate pred = builder.and(isSubsystem, memberNameIs);
//            return pred;
//        };
//    }

}
