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
 * Interface for X-Road identifier types with convenience default methods
 * for accessing JAXBElement values.
 * 
 * <p>This interface is implemented by JAXB-generated classes via xjc:superInterface
 * binding customization, providing simplified access to choice element values.</p>
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * XRoadIdentifierType id = ...;
 * 
 * // Use default methods directly
 * String value = id.getParticipantClassValue();
 * boolean isNewTerminology = id.usesNewParticipantClassTerminology();
 * }</pre>
 */
public interface XRoadIdentifier {

    // ============================================================
    // Abstract methods (implemented by generated classes)
    // ============================================================
    
    JAXBElement<String> getInstance();
    JAXBElement<String> getParticipantClass();
    JAXBElement<String> getParticipantCode();
    JAXBElement<String> getConnectorCode();
    String getSubsystemCode();
    String getGroupCode();
    String getServiceCode();
    String getServiceVersion();
    XRoadObjectType getObjectType();

    // ============================================================
    // Default convenience methods for simplified value access
    // ============================================================
    
    /**
     * Gets the instance identifier value (from either xRoadInstance or dataspaceInstance).
     * @return the instance value, or null if not set
     */
    default String getInstanceValue() {
        JAXBElement<String> element = getInstance();
        return element != null ? element.getValue() : null;
    }
    
    /**
     * Gets the participant/member class value.
     * @return the participant class value, or null if not set
     */
    default String getParticipantClassValue() {
        JAXBElement<String> element = getParticipantClass();
        return element != null ? element.getValue() : null;
    }
    
    /**
     * Gets the participant/member code value.
     * @return the participant code value, or null if not set
     */
    default String getParticipantCodeValue() {
        JAXBElement<String> element = getParticipantCode();
        return element != null ? element.getValue() : null;
    }
    
    /**
     * Gets the connector/server code value.
     * @return the connector code value, or null if not set
     */
    default String getConnectorCodeValue() {
        JAXBElement<String> element = getConnectorCode();
        return element != null ? element.getValue() : null;
    }

    // ============================================================
    // Default methods for detecting which terminology was used
    // ============================================================
    
    /**
     * Checks if new terminology (dataspaceInstance) was used for the instance.
     * @return true if new terminology was used
     */
    default boolean usesNewInstanceTerminology() {
        JAXBElement<String> element = getInstance();
        return element != null && "dataspaceInstance".equals(element.getName().getLocalPart());
    }
    
    /**
     * Checks if new terminology (participantClass) was used.
     * @return true if new terminology was used
     */
    default boolean usesNewParticipantClassTerminology() {
        JAXBElement<String> element = getParticipantClass();
        return element != null && "participantClass".equals(element.getName().getLocalPart());
    }
    
    /**
     * Checks if new terminology (participantCode) was used.
     * @return true if new terminology was used
     */
    default boolean usesNewParticipantCodeTerminology() {
        JAXBElement<String> element = getParticipantCode();
        return element != null && "participantCode".equals(element.getName().getLocalPart());
    }
    
    /**
     * Checks if new terminology (connectorCode) was used.
     * @return true if new terminology was used
     */
    default boolean usesNewConnectorTerminology() {
        JAXBElement<String> element = getConnectorCode();
        return element != null && "connectorCode".equals(element.getName().getLocalPart());
    }
    
    /**
     * Checks if the identifier uses all new terminology.
     * @return true if all fields use new terminology
     */
    default boolean usesAllNewTerminology() {
        return usesNewInstanceTerminology()
            && usesNewParticipantClassTerminology()
            && usesNewParticipantCodeTerminology();
    }
}
