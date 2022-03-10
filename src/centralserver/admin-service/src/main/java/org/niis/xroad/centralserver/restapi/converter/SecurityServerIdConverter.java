package org.niis.xroad.centralserver.restapi.converter;

import org.niis.xroad.centralserver.restapi.entity.SecurityServer;


public class SecurityServerIdConverter {
    private SecurityServerIdConverter() {
    }

    public static String entityToIdString(SecurityServer serverEntity) {

        return serverEntity.getOwner().getIdentifier().getXRoadInstance() + ':' +
                serverEntity.getOwner().getMemberClass().getCode() + ':' +
                serverEntity.getOwner().getMemberCode() + ':' +
                serverEntity.getServerCode();
    }
}
