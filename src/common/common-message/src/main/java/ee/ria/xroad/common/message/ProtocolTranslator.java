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

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static ee.ria.xroad.common.util.MimeTypes.TEXT_XML;

/**
 * Singleton translator for X-Road SOAP message protocol translation.
 * Translates between V4 (memberClass, xRoadInstance) and V5 (participantClass, dataspaceInstance).
 */
@Slf4j
public final class ProtocolTranslator {

    private static final ProtocolTranslator INSTANCE = new ProtocolTranslator();

    private ProtocolTranslator() {
        // Singleton
    }

    /**
     * Get the singleton instance.
     */
    public static ProtocolTranslator getInstance() {
        return INSTANCE;
    }

    /**
     * Translate a V4 SOAP message to V5 terminology.
     * V4 elements (xRoadInstance, memberClass, etc.) are translated to V5 (dataspaceInstance, participantClass).
     *
     * @param input The V4 SOAP message
     * @return A new SOAP message with V5 terminology, or the original if translation fails
     */
    public SoapMessageImpl translateToV5(SoapMessageImpl input) {
        return translate(input, TerminologyTranslatingParser.OutputTerminology.V5_NEW);
    }

    /**
     * Translate a V5 SOAP message to V4 terminology.
     * V5 elements (dataspaceInstance, participantClass) are translated to V4 (xRoadInstance, memberClass).
     *
     * @param input The V5 SOAP message
     * @return A new SOAP message with V4 terminology, or the original if translation fails
     */
    public SoapMessageImpl translateToV4(SoapMessageImpl input) {
        return translate(input, TerminologyTranslatingParser.OutputTerminology.V4_LEGACY);
    }

    private SoapMessageImpl translate(SoapMessageImpl input, TerminologyTranslatingParser.OutputTerminology outputTerminology) {
        if (input == null) {
            return null;
        }

        try {
            byte[] inputBytes = input.getBytes();
            InputStream is = new ByteArrayInputStream(inputBytes);

            TerminologyTranslatingParser parser = new TerminologyTranslatingParser(outputTerminology);

            AtomicReference<SoapMessageImpl> resultRef = new AtomicReference<>();

            SoapMessageDecoder.Callback callback = new SoapMessageDecoder.Callback() {
                @Override
                public void soap(SoapMessage message, Map<String, String> headers) {
                    resultRef.set((SoapMessageImpl) message);
                }

                @Override
                public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders) {
                    // Not handling attachments in POC
                }

                @Override
                public void fault(SoapFault fault) {
                    log.warn("SOAP fault during translation: {}", fault.getCode());
                }

                @Override
                public void onCompleted() {
                }

                @Override
                public void onError(Exception e) {
                    log.error("Error during translation", e);
                }

                @Override
                public void close() {
                }
            };

            SoapMessageDecoder decoder = new SoapMessageDecoder(TEXT_XML, callback, parser);
            decoder.parse(is);

            SoapMessageImpl result = resultRef.get();
            if (result != null) {
                log.debug("Translation complete ({} -> {}). Elements translated: {}",
                        input.getProtocolVersion(), outputTerminology, parser.getOutputTranslationCount());
                return result;
            }

            log.warn("Translation returned null, using original message");
            return input;

        } catch (Exception e) {
            log.error("Translation failed, using original message", e);
            return input;
        }
    }
}
