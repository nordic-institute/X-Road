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
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.Attributes;

import javax.xml.namespace.QName;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

/**
 * Parser that translates X-Road message headers between protocol versions.
 * <p>
 * This parser extends SaxSoapParserImpl which natively accepts both V4 and V5
 * input terminology (dataspaceInstance, participantClass, etc. are mapped to
 * xRoadInstance, memberClass internally).
 * </p>
 * <p>
 * The translator adds OUTPUT translation capability - it can emit V4 or V5
 * terminology in the output XML based on configuration.
 * </p>
 */
@Slf4j
public class TerminologyTranslatingParser extends SaxSoapParserImplV2 {

    /**
     * Output terminology to use when writing XML.
     */
    public enum OutputTerminology {
        /** Output Protocol 4.0 terminology (default, no translation) */
        V4_LEGACY,
        /** Output Protocol 5.0 terminology */
        V5_NEW,
        /**
         * Automatically detect input terminology and translate to V4 if configured.
         * Used for Provider SS receiving requests (where input version is unknown upfront).
         */
        AUTO_DETECT_PROVIDER_INPUT
    }

    // V4 to V5 output translation map
    private static final Map<QName, QName> V4_TO_V5_OUTPUT_MAP = Map.of(
            QNAME_ID_INSTANCE, QNAME_ID_DATASPACE_INSTANCE,
            QNAME_ID_MEMBER_CLASS, QNAME_ID_PARTICIPANT_CLASS,
            QNAME_ID_MEMBER_CODE, QNAME_ID_PARTICIPANT_CODE,
            QNAME_ID_SERVER_CODE, QNAME_ID_CONNECTOR_CODE
    );

    // V5 to V4 output translation map (for normalizing output to V4)
    private static final Map<QName, QName> V5_TO_V4_OUTPUT_MAP = Map.of(
            QNAME_ID_DATASPACE_INSTANCE, QNAME_ID_INSTANCE,
            QNAME_ID_PARTICIPANT_CLASS, QNAME_ID_MEMBER_CLASS,
            QNAME_ID_PARTICIPANT_CODE, QNAME_ID_MEMBER_CODE,
            QNAME_ID_CONNECTOR_CODE, QNAME_ID_SERVER_CODE
    );

    private final OutputTerminology outputTerminology;

    @Getter
    private int outputTranslationCount = 0;

    // Track if we are inside the protocolVersion element to translate its value
    private boolean inProtocolVersionElement = false;

    /**
     * Force XML re-encoding since we may modify element names and content in output.
     */
    @Override
    protected boolean isProcessedXmlRequired() {
        return true;
    }

    /**
     * Translate output element names based on configured output terminology.
     */
    /**
     * Translate output element names based on configured output terminology.
     */
    @Override
    protected void writeStartElementXml(String prefix, QName element,
                                        Attributes attributes, Writer writer) throws IOException {
        // Track if we're entering protocolVersion element
        if ("protocolVersion".equals(element.getLocalPart())) {
            inProtocolVersionElement = true;
        }

        QName outputElement = translateForOutput(element);
        if (!outputElement.equals(element)) {
            outputTranslationCount++;
            log.info("TerminologyTranslatingParser: Translating element {} -> {}", element, outputElement);
        } else {
             // Log interesting elements that are NOT translated to ensure we are seeing them
             if (element.getLocalPart().contains("Instance") || element.getLocalPart().contains("protocol")) {
                 log.debug("TerminologyTranslatingParser: Passthrough element {}", element);
             }
        }
        super.writeStartElementXml(prefix, outputElement, attributes, writer);
    }

    @Override
    protected void writeCharactersXml(char[] characters, int start, int length, Writer writer) throws IOException {
        if (inProtocolVersionElement) {
            String content = new String(characters, start, length).trim();
            String newVersion = translateProtocolVersion(content);
            if (!newVersion.equals(content)) {
                log.info("TerminologyTranslatingParser: Translating protocolVersion {} -> {}", content, newVersion);
                char[] newChars = newVersion.toCharArray();
                super.writeCharactersXml(newChars, 0, newChars.length, writer);
                return;
            }
        }
        super.writeCharactersXml(characters, start, length, writer);
    }

    private String translateProtocolVersion(String version) {
        if (outputTerminology == OutputTerminology.V5_NEW) {
            // Translate V4 version to V5
            if (version.startsWith("4.")) {
                return "5.0";
            }
        } else if (outputTerminology == OutputTerminology.V4_LEGACY) {
            // Translate V5 version to V4
            if (version.startsWith("5.")) {
                return "4.0";
            }
        }
        return version;
    }

    @Override
    protected void writeEndElementXml(String prefix, QName element,
                                      Attributes attributes, Writer writer) throws IOException {
        QName outputElement = translateForOutput(element);
        super.writeEndElementXml(prefix, outputElement, attributes, writer);

        // Track if we're exiting protocolVersion element
        if ("protocolVersion".equals(element.getLocalPart())) {
            inProtocolVersionElement = false;
        }
    }

    private QName translateForOutput(QName element) {
        if (outputTerminology == OutputTerminology.V5_NEW) {
            // Translate V4 -> V5 for output
            return V4_TO_V5_OUTPUT_MAP.getOrDefault(element, element);
        } else if (outputTerminology == OutputTerminology.V4_LEGACY) {
            // V4_LEGACY: translate V5 -> V4 for output (normalize to V4)
            return V5_TO_V4_OUTPUT_MAP.getOrDefault(element, element);
        } else if (outputTerminology == OutputTerminology.AUTO_DETECT_PROVIDER_INPUT) {
            // Check global config to see if we should enforce V4 output to Provider IS
            if (TerminologyTranslationConfig.getInstance().isOutputToProviderIsInV4()) {
                // If input was V5 (key in map), translate to V4. If input was V4, stay V4.
                return V5_TO_V4_OUTPUT_MAP.getOrDefault(element, element);
            }
            // Passthrough (keep input terminology)
            return element;
        }
        return element;
    }

    /**
     * Returns translation count for output.
     */
    public int getTranslationCount() {
        return outputTranslationCount;
    }

    /**
     * For backward compatibility with existing tests.
     */
    public enum TranslationDirection {
        V5_TO_V4,
        V4_TO_V5
    }

    /**
     * Creates parser with legacy TranslationDirection enum.
     */
    public TerminologyTranslatingParser(TranslationDirection direction) {
        this(direction == TranslationDirection.V4_TO_V5
                ? OutputTerminology.V5_NEW
                : OutputTerminology.V4_LEGACY);
    }

    public TerminologyTranslatingParser(OutputTerminology outputTerminology) {
        super();
        this.outputTerminology = outputTerminology;
        log.info("TerminologyTranslatingParser initialized with output terminology: {}",
                outputTerminology);
    }
    /**
     * For backward compatibility - returns 0 since input translation is now
     * handled natively by SaxSoapParserImpl.
     */
    public int getInputTranslationCount() {
        return 0; // Input is handled natively now
    }
}
