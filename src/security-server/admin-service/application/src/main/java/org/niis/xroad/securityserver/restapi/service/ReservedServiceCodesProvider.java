package org.niis.xroad.securityserver.restapi.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides access to the list of reserved service codes defined via configuration.
 */
@Component
public class ReservedServiceCodesProvider {

    private final Set<String> reservedCodes;

    /**
     * Loads the reserved service codes from configuration (proxy-ui-api.reserved-service-codes)
     *
     * @param reservedCodesList List of reserved codes from application configuration
     */
    public ReservedServiceCodesProvider(
            @Value("${proxy-ui-api.reserved-service-codes:}") List<String> reservedCodesList) {
        if (reservedCodesList != null) {
            this.reservedCodes = new HashSet<>(reservedCodesList);
        } else {
            this.reservedCodes = Collections.emptySet();
        }
    }

    /**
     * Check if the given service code is reserved.
     *
     * @param code Service code to check
     * @return true if code is reserved, false otherwise
     */
    public boolean isReserved(String code) {
        return reservedCodes.contains(code);
    }

    /**
     * Returns an unmodifiable view of the reserved codes.
     *
     * @return reserved service codes
     */
    public Set<String> getReservedCodes() {
        return Collections.unmodifiableSet(reservedCodes);
    }
}}
