package org.niis.xroad.centralserver.restapi.dto;

import lombok.Value;

import java.util.List;

@Value
public class FoundSecurityServersWithTotalsDto {
    List<SecurityServerDto> serverDtoList;
    Integer totalCount;
}
