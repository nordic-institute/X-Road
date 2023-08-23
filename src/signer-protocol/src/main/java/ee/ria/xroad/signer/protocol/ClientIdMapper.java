package ee.ria.xroad.signer.protocol;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.ClientIdProto;
import ee.ria.xroad.signer.protocol.dto.XRoadObjectType;

public class ClientIdMapper {

    public static ClientId.Conf fromDto(ClientIdProto input) {

        //TODO:grpc refine this check
        if (input.hasField(ClientIdProto.getDescriptor().findFieldByName("subsystem_code"))) {
            return ClientId.Conf.create(input.getXroadInstance(),
                    input.getMemberClass(),
                    input.getMemberCode(),
                    input.getSubsystemCode());
        } else {
            return ClientId.Conf.create(input.getXroadInstance(),
                    input.getMemberClass(),
                    input.getMemberCode());
        }
    }

    //TODO:grpc move to a separate place.
    public static ClientIdProto toDto(ClientId input) {
        var builder = ClientIdProto.newBuilder()
                .setMemberClass(input.getMemberClass())
                .setMemberCode(input.getMemberCode())
                .setXroadInstance(input.getXRoadInstance())
                .setObjectType(XRoadObjectType.valueOf(input.getObjectType().name()));

        if (input.getSubsystemCode() != null) {
            builder.setSubsystemCode(input.getSubsystemCode());
        }
        return builder.build();
    }
}
