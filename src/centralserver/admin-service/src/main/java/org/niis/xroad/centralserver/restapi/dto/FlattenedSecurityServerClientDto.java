package org.niis.xroad.centralserver.restapi.dto;

import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.Value;
import org.niis.xroad.centralserver.restapi.entity.FlattenedSecurityServerClient;

@Value
public class FlattenedSecurityServerClientDto {

    int id;
    String xroadInstance;
    String memberClassCode;
    String memberCode;
    String subsystemCode;
    String memberName;
    XRoadObjectType type;

    public static FlattenedSecurityServerClientDto from(FlattenedSecurityServerClient client) {
        XRoadObjectType objectType = null;
        switch (client.getType()) {
            case "XRoadMember":
                objectType = XRoadObjectType.MEMBER;
                break;
            case "Subsystem":
                objectType = XRoadObjectType.SUBSYSTEM;
                break;
            default:
                throw new IllegalArgumentException("Invalid client type " + client.getType());
        }
        FlattenedSecurityServerClientDto dto = new FlattenedSecurityServerClientDto(client.getId(),
                client.getXroadInstance(),
                client.getMemberClass().getCode(),
                client.getMemberCode(),
                client.getSubsystemCode(),
                client.getMemberName(),
                objectType);
        return dto;
    }
}
