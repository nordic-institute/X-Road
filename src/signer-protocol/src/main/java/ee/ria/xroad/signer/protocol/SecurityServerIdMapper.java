package ee.ria.xroad.signer.protocol;

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.niis.xroad.signer.protocol.dto.SecurityServerIdProto;
import org.niis.xroad.signer.protocol.dto.XRoadObjectType;

public class SecurityServerIdMapper {

    public static SecurityServerId.Conf fromDto(SecurityServerIdProto input) {
        return SecurityServerId.Conf.create(input.getXroadInstance(), input.getMemberClass(), input.getMemberCode(),
                input.getServerCode());
    }

    public static SecurityServerIdProto toDto(SecurityServerId input) {
        return SecurityServerIdProto.newBuilder()
                .setMemberClass(input.getMemberClass())
                .setMemberCode(input.getMemberCode())
                .setServerCode(input.getServerCode())
                .setXroadInstance(input.getXRoadInstance())
                .setObjectType(XRoadObjectType.valueOf(input.getObjectType().name()))
                .build();
    }
}
