/*
 * The MIT License
 *
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
package ee.ria.xroad.common.message;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * POC Feature toggle for terminology translation.
 * <p>
 * Controls how the Provider SS translates messages between V4 and V5 terminology:
 * <ul>
 *   <li>{@link #outputToProviderIsInV4} - When true, always output V4 to IS regardless of request version</li>
 *   <li>Response terminology matches the original request's protocolVersion</li>
 * </ul>
 * </p>
 * <p>
 * In production, this would be configured per-service via GlobalConf or service metadata.
 * </p>
 */
@Slf4j
public class TerminologyTranslationConfig {

    // Singleton instance for POC (in production, inject via dependency injection)
    private static final TerminologyTranslationConfig INSTANCE = new TerminologyTranslationConfig();

    @Getter
    @Setter
    private boolean serverSoapDecoderV5Enabled = true;
    @Getter
    @Setter
    private boolean clientSoapDecoderV5Enabled = true;
    /**
     * When true, output to Provider IS uses V4 terminology regardless of request version.
     * When false, pass through the request terminology as-is.
     * Default: true (translate V5 requests to V4 for legacy IS compatibility)
     */
    @Getter
    @Setter
    private boolean outputToProviderIsInV4 = true;

    /**
     * When true, Client Proxy converts V5 requests to V4 before sending to Server Proxy.
     * Use this when the destination Security Server is legacy (V7).
     */
    @Getter
    @Setter
    private boolean outputToServerIsInV4 = false;

    private TerminologyTranslationConfig() {
        log.info("TerminologyTranslationConfig initialized: outputToProviderIsInV4={}",
                outputToProviderIsInV4);
    }

    /**
     * Returns the singleton instance.
     */
    public static TerminologyTranslationConfig getInstance() {
        return INSTANCE;
    }

    /**
     * Determines the output terminology based on request and configuration.
     *
     * @param requestProtocolVersion the protocol version from the request (e.g., "4.0" or "5.0")
     * @param isProviderSide true if outputting to Provider IS, false if responding to Consumer
     * @return the output terminology to use
     */
    public TerminologyTranslatingParser.OutputTerminology getOutputTerminology(
            String requestProtocolVersion, boolean isProviderSide) {

        boolean requestIsV5 = requestProtocolVersion != null && requestProtocolVersion.startsWith("5");

        if (isProviderSide) {
            // Output to Provider IS
            if (outputToProviderIsInV4) {
                log.debug("Provider IS output: forcing V4 terminology (toggle enabled)");
                return TerminologyTranslatingParser.OutputTerminology.V4_LEGACY;
            } else {
                // Pass through request terminology
                return requestIsV5
                        ? TerminologyTranslatingParser.OutputTerminology.V5_NEW
                        : TerminologyTranslatingParser.OutputTerminology.V4_LEGACY;
            }
        } else {
            // Response to Consumer SS
            // Always separate response terminology to match request terminology (mirroring)
            return requestIsV5
                    ? TerminologyTranslatingParser.OutputTerminology.V5_NEW
                    : TerminologyTranslatingParser.OutputTerminology.V4_LEGACY;
        }
    }

    /**
     * Reset to defaults (for testing).
     */
    public void reset() {
        outputToProviderIsInV4 = true;
        serverSoapDecoderV5Enabled = true;
        clientSoapDecoderV5Enabled = true;
    }
}
