package org.niis.xroad.cs.admin.jpa.repository;

import org.niis.xroad.cs.admin.core.entity.TrustedAnchorEntity;
import org.niis.xroad.cs.admin.core.repository.TrustedAnchorRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaTrustedAnchorRepository extends JpaRepository<TrustedAnchorEntity, Integer>, TrustedAnchorRepository {
}
