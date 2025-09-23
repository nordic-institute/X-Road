/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.confclient.core;

import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.model.ConfigurationLocation;
import org.niis.xroad.globalconf.model.ConfigurationUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static ee.ria.xroad.common.crypto.identifier.Providers.BOUNCY_CASTLE;
import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_EXPIRE_DATE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_VERSION;

/**
 * Parses and handles the downloaded configuration directory.
 */
@Slf4j
@RequiredArgsConstructor
public class ConfigurationParser {

    // We cache the certificates we have found for a given hash
    static final Map<String, X509Certificate> HASH_TO_CERT = new ConcurrentHashMap<>();

    private enum ConfigurationPart {
        SIGNED_DATA,
        SIGNATURE
    }

    private enum ContentPart {
        HEADER,
        CONTENT
    }

    private final ConfigurationDownloader downloader;

    protected Configuration configuration;

    private String[] contentIdentifiers;
    private MimeStreamParser parser;

    private byte[] signedData;

    /**
     * Parses the configuration directory from the given input stream.
     *
     * @param location                      the configuration location
     * @param contentIdentifiersToBeHandled array of content identifiers that are handled.
     *                                      If null, all content is handled.
     * @return list of downloaded files
     */
    public synchronized Configuration parse(ConfigurationLocation location, String... contentIdentifiersToBeHandled) {
        log.trace("parse");

        configuration = new Configuration(location);

        contentIdentifiers = contentIdentifiersToBeHandled;

        parser = new MimeStreamParser();
        parser.setContentHandler(new MultipartContentHandler());

        try (InputStream in = getInputStream()) {
            parser.parse(in);
        } catch (MimeException | IOException e) {
            throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_PARSING_DOWNLOADED_CONF_DIRECTORY_FAILURE)
                    .details("Failed to parse configuration from %s: %s".formatted(location.getDownloadURL(), e.getMessage()))
                    .cause(e)
                    .build();
        }

        verifyIntegrity();

        return configuration;
    }

    protected InputStream getInputStream() {
        return getConfigurationInputStream(configuration.getLocation());
    }

    private void verifyIntegrity() {
        if (signedData == null) {
            throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_MISSING_SIGNED_DATA)
                    .details("Configuration instance %s is missing signed data".formatted(getInstanceIdentifier()))
                    .build();
        }

        if (configuration.getExpirationDate() == null) {
            throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_MISSING_SIGNED_DATA_EXPIRATION_DATE)
                    .details("Configuration instance %s is missing signed data expiration date".formatted(getInstanceIdentifier()))
                    .build();
        }
    }

    private String getInstanceIdentifier() {
        return configuration.getLocation().getInstanceIdentifier();
    }

    /**
     * Topmost multipart content handler. Reads and verifies the signed data.
     */
    @RequiredArgsConstructor
    private final class MultipartContentHandler extends AbstractContentHandler {

        private Map<String, String> headers;

        private String signedDataContentType;

        private ConfigurationPart nextPart = ConfigurationPart.SIGNED_DATA;

        @Override
        public void startMultipart(BodyDescriptor bd) {
            parser.setFlat();
        }

        @Override
        public void startHeader() {
            headers = new HashMap<>();
        }

        @Override
        public void field(Field field) {
            headers.put(field.getName().toLowerCase(), field.getBody());
        }

        @Override
        public void body(BodyDescriptor bd, InputStream is) throws MimeException, IOException {
            if (nextPart == ConfigurationPart.SIGNED_DATA) {
                readSignedData(is);
                nextPart = ConfigurationPart.SIGNATURE;
            } else {
                byte[] signature = decodeBase64Signature(is);
                verifySignedData(signature);
                handleSignedData();
            }
        }

        private String getHeader(String headerName) {
            return headers.get(headerName.toLowerCase());
        }

        private void readSignedData(InputStream is) throws IOException {
            log.trace("readSignedData()");

            signedDataContentType = getHeader(HEADER_CONTENT_TYPE);
            signedData = IOUtils.toByteArray(is);
        }

        private void verifySignedData(byte[] signature) {
            log.trace("verifySignedData()");

            try {
                ConfigurationSignature parameters = ConfigurationSignature.of(headers);

                SignAlgorithm algoUri = parameters.getSignatureAlgorithmId();

                log.trace("Verifying signed content using signature algorithm id {}", algoUri);

                Signature verifier = Signature.getInstance(algoUri.name(), BOUNCY_CASTLE);

                X509Certificate verificationCert = getVerificationCert(configuration.getLocation(), parameters);

                if (verificationCert == null) {
                    throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_MISSING_VERIFICATION_CERT)
                            .details(
                                    ("Cannot verify signature of configuration instance %s: could not find verification certificate for "
                                          + "certificate hash %s").formatted(
                                            getInstanceIdentifier(),
                                            parameters.getVerificationCertHash()))
                            .build();
                }

                if (!verifySignature(verifier, verificationCert, signature, signedData)) {
                    throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_SIGNATURE_VERIFICATION_FAILURE)
                            .details("Failed to verify signature of configuration instance %s".formatted(getInstanceIdentifier()))
                            .build();
                }
            } catch (Exception e) {
                throw XrdRuntimeException.systemException(e);
            }
        }

        private byte[] decodeBase64Signature(InputStream signatureIs) {
            try {
                return decodeBase64(IOUtils.toString(signatureIs, Charset.defaultCharset()));
            } catch (Exception e) {
                throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_SIGNATURE_DECODE_FAILURE)
                        .details("Failed to decode signature of configuration instance %s".formatted(getInstanceIdentifier()))
                        .cause(e)
                        .build();
            }
        }

        boolean verifySignature(Signature verifier, X509Certificate verificationCert, byte[] signature, byte[] sd) {
            String cn = verificationCert.getSubjectX500Principal().getName();

            try {
                verifier.initVerify(verificationCert.getPublicKey());
                verifier.update(sd);

                if (verifier.verify(signature)) {
                    log.trace("Verified signature using certificate {}", cn);

                    return true;
                } else {
                    log.error("Failed to verify signature using certificate {}", cn);

                    return false;
                }
            } catch (Exception e) {
                log.error("Error verifying signature using certificate {}", cn, e);

                return false;
            }
        }

        X509Certificate getVerificationCert(ConfigurationLocation location, ConfigurationSignature parameters) {
            if (HASH_TO_CERT.containsKey(parameters.getVerificationCertHash())) {
                log.trace("Return certificate from HASH_TO_CERT map");

                return HASH_TO_CERT.get(parameters.getVerificationCertHash());
            }

            X509Certificate cert = location.getVerificationCert(parameters.getVerificationCertHash(),
                    parameters.getVerificationCertHashAlgoUri());

            log.trace("cert={}", cert);

            if (cert != null) {
                log.trace("Put cert to HASH_TO_CERT map");

                HASH_TO_CERT.put(parameters.getVerificationCertHash(), cert);
            }

            return cert;
        }

        private void handleSignedData() throws MimeException, IOException {
            MimeConfig config = new MimeConfig.Builder().setHeadlessParsing(signedDataContentType).build();
            MimeStreamParser p = new MimeStreamParser(config);
            p.setContentHandler(new ConfigurationPartContentHandler());
            p.parse(new ByteArrayInputStream(signedData));
        }
    }

    /**
     * Configuration directory multipart content handler.
     */
    @RequiredArgsConstructor
    private final class ConfigurationPartContentHandler
            extends AbstractContentHandler {

        private Map<String, String> headers;

        private ContentPart nextPart = ContentPart.HEADER;

        @Override
        public void startMultipart(BodyDescriptor bd) {
            parser.setFlat();
        }

        @Override
        public void startHeader() {
            headers = new HashMap<>();
        }

        @Override
        public void field(Field field) {
            headers.put(field.getName().toLowerCase(), field.getBody());
        }

        @Override
        public void body(BodyDescriptor bd, InputStream is) {
            if (nextPart == ContentPart.HEADER) {
                parseExpirationDate();
                verifyConfUpToDate();
                parseVersion();
                nextPart = ContentPart.CONTENT;
            } else {
                parseContent(is);
            }
        }

        private void parseExpirationDate() {
            configuration.setExpirationDate(parseExpireDate(getHeader(HEADER_EXPIRE_DATE)));
        }

        private void parseVersion() {
            configuration.setVersion(getHeader(HEADER_VERSION));
        }

        private void verifyConfUpToDate() {
            if (configuration.isExpired()) {
                throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_OUTDATED)
                        .details("Configuration instance %s expired on %s"
                                .formatted(getInstanceIdentifier(), configuration.getExpirationDate()))
                        .build();
            }
        }

        private void parseContent(InputStream is) {
            log.trace("onContent({})", headers);

            try {
                ConfigurationFile file = ConfigurationFile.of(headers,
                        configuration.getExpirationDate(), configuration.getVersion(), IOUtils.toString(is, Charset.defaultCharset()));

                if (shouldHandleContent(file)) {
                    configuration.getFiles().add(file);
                } else {
                    log.trace("Ignoring content {}", headers);
                }
            } catch (Exception e) {
                throw XrdRuntimeException.systemException(e);
            }
        }

        private boolean shouldHandleContent(ConfigurationFile f) {
            return ArrayUtils.isEmpty(contentIdentifiers)
                    || ArrayUtils.contains(contentIdentifiers, f.getContentIdentifier());
        }

        private String getHeader(String headerName) {
            return headers.get(headerName.toLowerCase());
        }
    }

    private static OffsetDateTime parseExpireDate(String expireDateStr) {
        if (StringUtils.isBlank(expireDateStr)) {
            throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_HEADER_FIELD_MISSING)
                    .details("Missing header %s".formatted(HEADER_EXPIRE_DATE))
                    .metadataItems(HEADER_EXPIRE_DATE)
                    .build();
        }

        return ConfigurationUtils.parseISODateTime(expireDateStr);
    }

    /**
     * @return the input stream acquired by connecting to the download url.
     */
    private InputStream getConfigurationInputStream(ConfigurationLocation configurationLocation) {
        try {
            var connection = downloader.getDownloadURLConnection(URI.create(configurationLocation.getDownloadURL()).toURL());
            return connection.getInputStream();
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_DOWNLOAD_URL_CONNECTION_FAILURE)
                    .details("Failed to connect to the download url for configuration instance %s".formatted(getInstanceIdentifier()))
                    .build();
        }
    }


}
