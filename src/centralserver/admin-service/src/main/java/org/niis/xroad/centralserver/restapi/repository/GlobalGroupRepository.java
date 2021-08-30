package org.niis.xroad.centralserver.restapi.repository;

import org.niis.xroad.centralserver.restapi.entity.GlobalGroup;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository("GlobalGroupRepository")
@Transactional
public interface GlobalGroupRepository extends CrudRepository<GlobalGroup, Integer> {

    Optional<GlobalGroup> getGlobalGroupByGroupCode(String code);
}
