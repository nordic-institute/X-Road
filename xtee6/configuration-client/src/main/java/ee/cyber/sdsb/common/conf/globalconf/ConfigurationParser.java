package ee.cyber.sdsb.common.conf.globalconf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.joda.time.DateTime;

import ee.cyber.sdsb.common.CodedException;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.decodeBase64;
import static ee.cyber.sdsb.common.util.CryptoUtils.getAlgorithmId;
import static ee.cyber.sdsb.common.util.MimeUtils.HEADER_CONTENT_TYPE;
import static ee.cyber.sdsb.common.util.MimeUtils.HEADER_EXPIRE_DATE;

/**
 * Parses and handles the downloaded configuration directory.
 */
@Slf4j
public class ConfigurationParser {

    // We cache the certificates we have found for a given hash
    static final Map<String, X509Certificate> HASH_TO_CERT =
            new ConcurrentHashMap<>();

    private enum ConfigurationPart {
        SIGNED_DATA,
        SIGNATURE
    }

    private enum ContentPart {
        EXPIRATION_DATE,
        CONTENT
    }

    private final String[] supportedInstanceIdentifiers;

    private ConfigurationLocation configurationLocation;

    private String[] contentIdentifiers;
    private MimeStreamParser parser;

    private DateTime signedDataExpirationDate;
    private byte[] signedData;

    @Getter
    private List<ConfigurationFile> files = new ArrayList<>();

    /**
     * @param supportedInstanceIdentifiers the list of instance identifiers
     * that are to be downloaded
     */
    public ConfigurationParser(String... supportedInstanceIdentifiers) {
        this.supportedInstanceIdentifiers = supportedInstanceIdentifiers;
    }

    /**
     * Parses the configuration directory from the given input stream.
     *
     * @param location the configuration location
     * @param contentIdentifiersToBeHandled array of content identifiers that are handled.
     * If null, all content is handled.
     * @return list of downloaded files
     * @throws Exception if an error occurs
     */
    public synchronized List<ConfigurationFile> parse(
            ConfigurationLocation location,
            String... contentIdentifiersToBeHandled) throws Exception {
        this.configurationLocation = location;
        this.contentIdentifiers = contentIdentifiersToBeHandled;

        this.parser = new MimeStreamParser();
        this.parser.setContentHandler(new MultipartContentHandler());

        try (InputStream in = getInputStream(location.getDownloadURL())) {
            this.parser.parse(in);
        }

        verifyIntegrity();

        return files;
    }

    protected InputStream getInputStream(String downloadURL) throws Exception {
        try {
            return new URL(downloadURL).openStream();
        } catch (IOException e) {
            throw new CodedException(X_HTTP_ERROR, e);
        }
    }

    private void verifyIntegrity() {
        if (signedData == null) {
            throw new CodedException(X_MALFORMED_GLOBALCONF,
                    "Configuration instance %s is missing signed data",
                    getInstanceIdentifier());
        }

        if (signedDataExpirationDate == null) {
            throw new CodedException(X_MALFORMED_GLOBALCONF,
                    "Configuration instance %s is missing signed data "
                    + "expiration date", getInstanceIdentifier());
        }
    }

    private String getInstanceIdentifier() {
        return configurationLocation.getSource().getInstanceIdentifier();
    }

    /**
     * Topmost multipart content handler. Reads and verifies the signed data.
     */
    @RequiredArgsConstructor
    private class MultipartContentHandler extends AbstractContentHandler {

        private Map<String, String> headers;

        private String signedDataContentType;

        private ConfigurationPart nextPart = ConfigurationPart.SIGNED_DATA;

        @Override
        public final void startMultipart(BodyDescriptor bd) {
            parser.setFlat();
        }

        @Override
        public void startHeader() throws MimeException {
            headers = new HashMap<>();
        }

        @Override
        public void field(Field field) throws MimeException {
            headers.put(field.getName().toLowerCase(), field.getBody());
        }

        @Override
        public void body(BodyDescriptor bd, InputStream is)
                throws MimeException, IOException {
            if (nextPart == ConfigurationPart.SIGNED_DATA) {
                readSignedData(is);
            } else {
                byte[] signature = decodeBase64(IOUtils.toString(is));
                verifySignedData(signature);
                handleSignedData();
            }
        }

        protected String getHeader(String headerName) {
            return headers.get(headerName.toLowerCase());
        }

        protected void readSignedData(InputStream is) throws IOException {
            log.trace("readSignedData()");

            signedDataContentType = getHeader(HEADER_CONTENT_TYPE);
            signedData = IOUtils.toByteArray(is);

            nextPart = ConfigurationPart.SIGNATURE;
        }

        protected void verifySignedData(byte[] signature) {
            log.trace("verifySignedData()");
            try {
                SignatureParameters parameters =
                        SignatureParameters.of(headers);

                String algoId = parameters.getSignatureAlgorithmId();

                log.trace("Verifying signed content using signature "
                        + "algorithm id {}", algoId);

                Signature verifier =
                        Signature.getInstance(getAlgorithmId(algoId));

                X509Certificate verificationCert =
                        getVerificationCert(configurationLocation, parameters);
                if (verificationCert == null) {
                    throw new CodedException(X_CERT_NOT_FOUND,
                            "Cannot verify signature of configuration "
                            + "instance %s: could not find verification "
                            + "certificate for certificate hash %s",
                            getInstanceIdentifier(),
                            parameters.getVerificationCertHash());
                }

                if (!verifySignature(verifier, verificationCert, signature,
                        signedData)) {
                    throw new CodedException(X_INVALID_SIGNATURE_VALUE,
                            "Failed to verify signature of configuration "
                            + "instance %s", getInstanceIdentifier());
                }
            } catch (Exception e) {
                throw translateException(e);
            }
        }

        boolean verifySignature(Signature verifier,
                X509Certificate verificationCert, byte[] signature, byte[] sd) {
            String cn = verificationCert.getSubjectX500Principal().getName();
            try {
                verifier.initVerify(verificationCert.getPublicKey());
                verifier.update(sd);

                if (verifier.verify(signature)) {
                    log.trace("Verified signature using certificate {}", cn);
                    return true;
                } else {
                    log.error("Failed to verify signature using certificate {}",
                            cn);
                    return false;
                }
            } catch (Exception e) {
                log.error("Error verifying signature using certificate " + cn,
                        e);
                return false;
            }
        }

        X509Certificate getVerificationCert(ConfigurationLocation location,
                SignatureParameters parameters) throws Exception {
            if (HASH_TO_CERT.containsKey(
                    parameters.getVerificationCertHash())) {
                return HASH_TO_CERT.get(parameters.getVerificationCertHash());
            }

            X509Certificate cert = location.getVerificationCert(
                    parameters.getVerificationCertHash(),
                    parameters.getVerificationCertHashAlgoId());

            if (cert != null) {
                HASH_TO_CERT.put(parameters.getVerificationCertHash(), cert);
            }

            return cert;
        }

        protected void handleSignedData() throws MimeException, IOException {
            MimeConfig config = new MimeConfig();
            config.setHeadlessParsing(signedDataContentType);

            MimeStreamParser p = new MimeStreamParser(config);
            p.setContentHandler(new ConfigurationPartContentHandler());
            p.parse(new ByteArrayInputStream(signedData));
        }
    }

    /**
     * Configuration directory multipart content handler.
     */
    @RequiredArgsConstructor
    private class ConfigurationPartContentHandler
            extends AbstractContentHandler {

        protected Map<String, String> headers;

        private ContentPart nextPart = ContentPart.EXPIRATION_DATE;

        @Override
        public final void startMultipart(BodyDescriptor bd) {
            parser.setFlat();
        }

        @Override
        public void startHeader() throws MimeException {
            headers = new HashMap<>();
        }

        @Override
        public void field(Field field) throws MimeException {
            headers.put(field.getName().toLowerCase(), field.getBody());
        }

        @Override
        public void body(BodyDescriptor bd, InputStream is)
                throws MimeException, IOException {
            if (nextPart == ContentPart.EXPIRATION_DATE) {
                parseExpirationDate();
                verifyConfUpToDate();
            } else {
                parseContent(is);
            }
        }

        private void parseExpirationDate() {
            signedDataExpirationDate =
                    parseExpireDate(getHeader(HEADER_EXPIRE_DATE));
            nextPart = ContentPart.CONTENT;
        }

        private void verifyConfUpToDate() {
            if (signedDataExpirationDate != null
                    && new DateTime().isAfter(signedDataExpirationDate)) {
                throw new CodedException(X_OUTDATED_GLOBALCONF,
                        "Configuration instance %s expired on %s",
                        getInstanceIdentifier(), signedDataExpirationDate);
            }
        }

        private void parseContent(InputStream is) {
            log.trace("onContent({})", headers);
            try {
                ConfigurationFile file =
                        ConfigurationFile.of(headers, signedDataExpirationDate,
                                IOUtils.toString(is));
                if (shouldHandleContent(file)) {
                    files.add(file);
                } else {
                    log.trace("Ignoring content {}", headers);
                }
            } catch (Exception e) {
                throw translateException(e);
            }
        }

        private boolean shouldHandleContent(ConfigurationFile f) {
            if (!ArrayUtils.isEmpty(supportedInstanceIdentifiers)
                    && !StringUtils.isBlank(f.getInstanceIdentifier())
                    && !ArrayUtils.contains(supportedInstanceIdentifiers,
                            f.getInstanceIdentifier())) {
                return false;
            }

            return ArrayUtils.isEmpty(contentIdentifiers)
                    || ArrayUtils.contains(contentIdentifiers,
                            f.getContentIdentifier());
        }

        protected String getHeader(String headerName) {
            return headers.get(headerName.toLowerCase());
        }
    }

    private static DateTime parseExpireDate(String expireDateStr) {
        if (StringUtils.isBlank(expireDateStr)) {
            throw new CodedException(X_MALFORMED_GLOBALCONF,
                    "Missing header " + HEADER_EXPIRE_DATE);
        }

        return ConfigurationUtils.parseISODateTime(expireDateStr);
    }
}
