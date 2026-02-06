/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ee.ria.xroad.common.identifier;

/**
 * Adapter that provides unified access to identifier properties regardless of schema version.
 * Uses NEW terminology (participantClass, connectorCode) as the canonical naming.
 * 
 * <p>This adapter bridges the terminology gap between:</p>
 * <ul>
 *   <li>v4 (legacy): memberClass, memberCode, xRoadInstance, serverCode</li>
 *   <li>v5 (new): participantClass, participantCode, dataspaceInstance, connectorCode</li>
 * </ul>
 */
public record IdentifierAdapter(
    String instance,
    String participantClass,
    String participantCode,
    String connectorCode,
    String subsystemCode,
    String groupCode,
    String serviceCode,
    String serviceVersion,
    String objectType
) {

    /**
     * Creates adapter from v4 (legacy) identifier type.
     */
    public static IdentifierAdapter fromLegacy(ee.ria.xroad.common.identifier.v4.XRoadIdentifierType legacy) {
        return new IdentifierAdapter(
            legacy.getXRoadInstance(),
            legacy.getMemberClass(),
            legacy.getMemberCode(),
            legacy.getServerCode(),
            legacy.getSubsystemCode(),
            legacy.getGroupCode(),
            legacy.getServiceCode(),
            legacy.getServiceVersion(),
            legacy.getObjectType() != null ? legacy.getObjectType().name() : null
        );
    }

    /**
     * Creates adapter from v5 (new) identifier type.
     */
    public static IdentifierAdapter fromNew(ee.ria.xroad.common.identifier.v5.XRoadIdentifierType newId) {
        return new IdentifierAdapter(
            newId.getDataspaceInstance(),
            newId.getParticipantClass(),
            newId.getParticipantCode(),
            newId.getConnectorCode(),
            newId.getSubsystemCode(),
            newId.getGroupCode(),
            newId.getServiceCode(),
            newId.getServiceVersion(),
            newId.getObjectType() != null ? newId.getObjectType().name() : null
        );
    }

    /**
     * Converts to v4 (legacy) identifier type for output.
     */
    public ee.ria.xroad.common.identifier.v4.XRoadIdentifierType toLegacy() {
        var legacy = new ee.ria.xroad.common.identifier.v4.XRoadIdentifierType();
        legacy.setXRoadInstance(instance);
        legacy.setMemberClass(participantClass);
        legacy.setMemberCode(participantCode);
        legacy.setServerCode(connectorCode);
        legacy.setSubsystemCode(subsystemCode);
        legacy.setGroupCode(groupCode);
        legacy.setServiceCode(serviceCode);
        legacy.setServiceVersion(serviceVersion);
        if (objectType != null) {
            legacy.setObjectType(ee.ria.xroad.common.identifier.v4.XRoadObjectType.valueOf(objectType));
        }
        return legacy;
    }

    /**
     * Converts to v5 (new) identifier type for output.
     */
    public ee.ria.xroad.common.identifier.v5.XRoadIdentifierType toNew() {
        var newId = new ee.ria.xroad.common.identifier.v5.XRoadIdentifierType();
        newId.setDataspaceInstance(instance);
        newId.setParticipantClass(participantClass);
        newId.setParticipantCode(participantCode);
        newId.setConnectorCode(connectorCode);
        newId.setSubsystemCode(subsystemCode);
        newId.setGroupCode(groupCode);
        newId.setServiceCode(serviceCode);
        newId.setServiceVersion(serviceVersion);
        if (objectType != null) {
            newId.setObjectType(ee.ria.xroad.common.identifier.v5.XRoadObjectType.valueOf(objectType));
        }
        return newId;
    }
}
