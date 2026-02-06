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
package ee.ria.xroad.common.identifier.v2;

import jakarta.xml.bind.JAXBElement;

/**
 * Utility class providing helper methods for working with JAXB-generated
 * identifier types that use xs:choice for dual terminology support.
 * 
 * <p>DEMO: This utility simplifies access to JAXBElement values in the
 * generated XRoadIdentifierType class.</p>
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * XRoadIdentifierType id = ...;
 * 
 * // Instead of:
 * String value = id.getParticipantClass() != null 
 *     ? id.getParticipantClass().getValue() : null;
 * 
 * // Use:
 * String value = XRoadIdentifiers.getParticipantClassValue(id);
 * }</pre>
 */
public final class XRoadIdentifiers {

    private XRoadIdentifiers() {
        // Utility class - no instantiation
    }

    // ============================================================
    // Value extraction methods
    // ============================================================
    
    /**
     * Gets the instance identifier value (from either xRoadInstance or dataspaceInstance).
     * @param identifier the identifier object
     * @return the instance value, or null if not set
     */
    public static String getInstanceValue(XRoadIdentifierType identifier) {
        return getValue(identifier.getInstance());
    }
    
    /**
     * Gets the participant/member class value.
     * @param identifier the identifier object
     * @return the participant class value, or null if not set
     */
    public static String getParticipantClassValue(XRoadIdentifierType identifier) {
        return getValue(identifier.getParticipantClass());
    }
    
    /**
     * Gets the participant/member code value.
     * @param identifier the identifier object
     * @return the participant code value, or null if not set
     */
    public static String getParticipantCodeValue(XRoadIdentifierType identifier) {
        return getValue(identifier.getParticipantCode());
    }
    
    /**
     * Gets the connector/server code value.
     * @param identifier the identifier object
     * @return the connector code value, or null if not set
     */
    public static String getConnectorCodeValue(XRoadIdentifierType identifier) {
        return getValue(identifier.getConnectorCode());
    }

    // ============================================================
    // Terminology detection methods
    // ============================================================
    
    /**
     * Checks if new terminology (dataspaceInstance) was used for the instance.
     * @param identifier the identifier object
     * @return true if new terminology was used
     */
    public static boolean usesNewInstanceTerminology(XRoadIdentifierType identifier) {
        return isElementNamed(identifier.getInstance(), "dataspaceInstance");
    }
    
    /**
     * Checks if new terminology (participantClass) was used.
     * @param identifier the identifier object
     * @return true if new terminology was used
     */
    public static boolean usesNewParticipantClassTerminology(XRoadIdentifierType identifier) {
        return isElementNamed(identifier.getParticipantClass(), "participantClass");
    }
    
    /**
     * Checks if new terminology (participantCode) was used.
     * @param identifier the identifier object
     * @return true if new terminology was used
     */
    public static boolean usesNewParticipantCodeTerminology(XRoadIdentifierType identifier) {
        return isElementNamed(identifier.getParticipantCode(), "participantCode");
    }
    
    /**
     * Checks if new terminology (connectorCode) was used.
     * @param identifier the identifier object
     * @return true if new terminology was used
     */
    public static boolean usesNewConnectorTerminology(XRoadIdentifierType identifier) {
        return isElementNamed(identifier.getConnectorCode(), "connectorCode");
    }
    
    /**
     * Checks if the identifier uses all new terminology.
     * @param identifier the identifier object
     * @return true if all fields use new terminology
     */
    public static boolean usesAllNewTerminology(XRoadIdentifierType identifier) {
        return usesNewInstanceTerminology(identifier)
            && usesNewParticipantClassTerminology(identifier)
            && usesNewParticipantCodeTerminology(identifier);
    }

    // ============================================================
    // Private helper methods
    // ============================================================
    
    private static String getValue(JAXBElement<String> element) {
        return element != null ? element.getValue() : null;
    }
    
    private static boolean isElementNamed(JAXBElement<String> element, String localName) {
        return element != null && localName.equals(element.getName().getLocalPart());
    }
}
