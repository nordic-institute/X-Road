package org.niis.xroad.restapi.dto;

import ee.ria.xroad.common.identifier.XRoadId;

import lombok.Data;

@Data
public class ServiceClientIdentifierDto {
    private Long localGroupId;
    private XRoadId xRoadId;
    private ServiceClientIdentifierType serviceClientIdentifierType;
}
