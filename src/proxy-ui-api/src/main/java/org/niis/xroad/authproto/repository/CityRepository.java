package org.niis.xroad.authproto.repository;

import org.niis.xroad.authproto.domain.City;
import org.springframework.data.repository.CrudRepository;

/**
 * read cities from db
 */
public interface CityRepository extends CrudRepository<City, Long> {
}
