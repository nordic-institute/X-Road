package org.niis.xroad.restapi.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.niis.xroad.restapi.domain.AdminUser;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

@Mapper(componentModel = SPRING)
public interface AdminUserDtoMapper {
    @Mapping(target = "password", ignore = true)
    org.niis.xroad.restapi.openapi.model.AdminUser toDto(AdminUser user);

    AdminUser toDomainObject(org.niis.xroad.restapi.openapi.model.AdminUser adminuser);
}
