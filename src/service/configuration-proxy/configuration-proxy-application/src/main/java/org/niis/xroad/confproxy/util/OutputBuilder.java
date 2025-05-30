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
package org.niis.xroad.confproxy.util;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MultipartEncoder;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.eclipse.jetty.util.MultiPartWriter;
import org.niis.xroad.confproxy.ConfProxyProperties;
import org.niis.xroad.globalconf.model.ConfigurationPartMetadata;
import org.niis.xroad.globalconf.model.ParametersProviderFactory;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.globalconf.model.VersionedConfigurationDirectory;
import org.niis.xroad.globalconf.util.HashCalculator;
import org.niis.xroad.signer.client.SignerRpcClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_IDENTIFIER;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_LOCATION;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_TRANSFER_ENCODING;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_EXPIRE_DATE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGORITHM_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_SIG_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_VERIFICATION_CERT_HASH;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_VERSION;
import static ee.ria.xroad.common.util.MimeUtils.mpMixedContentType;
import static ee.ria.xroad.common.util.MimeUtils.mpRelatedContentType;
import static ee.ria.xroad.common.util.MimeUtils.randomBoundary;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS;

/**
 * Utility class that encapsulates the process of signing the downloaded
 * global configuration and moving it to the target location.
 */
@Slf4j
public class OutputBuilder implements AutoCloseable {

    public static final String SIGNED_DIRECTORY_NAME = "conf";
    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("UTC"));

    private final SignerRpcClient signerRpcClient;
    private final VersionedConfigurationDirectory confDir;
    private final ConfProxyProperties conf;
    private final int version;

    private Path tempConfPath;
    private HashCalculator hashCalculator;
    private String timestamp;
    private Path tempDirPath;

    private String dataBoundary;
    private String envelopeBoundary;
    private String envelopeHeader;

    /**
     * Constructs an output builder for the given global configuration directory
     * and configuration proxy instance configuration.
     *
     * @param confDirectory global configuration to be processed
     * @param configuration configuration proxy instance configuration
     * @throws IOException in case of errors when a temporary directory
     */
    public OutputBuilder(SignerRpcClient signerRpcClient, VersionedConfigurationDirectory confDirectory,
                         ConfProxyProperties configuration, int version)
            throws IOException {
        this.signerRpcClient = signerRpcClient;
        this.confDir = confDirectory;
        this.conf = configuration;
        this.version = version;

        setup();
    }

    /**
     * Generates a signed directory MIME for the global configuration and
     * writes the directory contents to a temporary location.
     *
     * @throws Exception if errors occur when reading global configuration files
     */
    public final void buildSignedDirectory() throws Exception {
        try (ByteArrayOutputStream mimeContent = new ByteArrayOutputStream()) {
            build(mimeContent);

            log.debug("Generated directory content:\n{}\n", mimeContent);

            byte[] contentBytes = mimeContent.toByteArray();
            mimeContent.reset();
            sign(contentBytes, mimeContent);
            Files.write(tempConfPath, mimeContent.toByteArray());

            log.debug("Written signed directory to '{}'", tempConfPath);
        }
    }

    /**
     * Moves the signed global configuration to the location where it is
     * accessible to clients.
     *
     * @throws IOException in case of unsuccessful file operations
     */
    public final void move() throws IOException {
        String path = conf.getConfigurationTargetPath();
        Path targetPath = Paths.get(path, timestamp);
        Path targetConf = Paths.get(path, String.format("%s-v%d", SIGNED_DIRECTORY_NAME, version));
        Files.createDirectories(targetPath.getParent());

        log.debug("Moving '{}' to '{}'", tempDirPath, targetPath);

        Files.move(tempDirPath, targetPath);

        log.debug("Moving '{}' to '{}'", tempConfPath, targetConf);

        Files.move(tempConfPath, targetConf, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Cleans up any remaining temporary files.
     *
     * @throws IOException in case of unsuccessful file operations
     */
    @Override
    public final void close() throws IOException {
        log.debug("Cleaning up '{}'", tempDirPath);
        FileUtils.deleteDirectory(tempDirPath.toFile());
    }

    /**
     * Setup reference data and temporary directory for the output builder.
     *
     * @throws IOException if temporary directory could not be created
     */
    private void setup() throws IOException {
        String tempDir = conf.getTemporaryDirectoryPath();
        DigestAlgorithm hashAlgURI = conf.getHashAlgorithmURI();

        hashCalculator = new HashCalculator(hashAlgURI);
        timestamp = Long.toString(new Date().getTime());
        tempConfPath = Paths.get(tempDir, String.format("%s-v%d", SIGNED_DIRECTORY_NAME, version));
        tempDirPath = Paths.get(tempDir, timestamp);

        log.debug("Creating directories {}", tempDirPath);

        Files.createDirectories(tempDirPath);

        log.debug("Clean directory {}", tempDirPath);

        FileUtils.cleanDirectory(tempDirPath.toFile());

        dataBoundary = randomBoundary();
        envelopeBoundary = randomBoundary();
        envelopeHeader = HEADER_CONTENT_TYPE + ": " + mpRelatedContentType(envelopeBoundary,
                MultiPartWriter.MULTIPART_MIXED) + "\n\n";
    }

    /**
     * Generates global configuration directory content MIME.
     *
     * @param mimeContent output stream to write to
     * @throws Exception if reading global configuration files fails
     */
    private void build(final ByteArrayOutputStream mimeContent) throws Exception {
        try (MultipartEncoder encoder = new MultipartEncoder(mimeContent, dataBoundary)) {
            OffsetDateTime expireDate = TimeUtils.offsetDateTimeNow().plusSeconds(conf.getValidityIntervalSeconds());
            encoder.startPart(null, new String[]{
                    HEADER_EXPIRE_DATE + ": " + DATETIME_FORMAT.format(expireDate.truncatedTo(ChronoUnit.MILLIS)),
                    HEADER_VERSION + ": " + String.format("%d", version)
            });

            String instance = conf.getInstance();

            confDir.eachFile((metadata, inputStream) -> {
                try (FileOutputStream fos = createFileOutputStream(tempDirPath, metadata)) {
                    if (shouldOverrideConfigurationSources(metadata)) {
                        inputStream = toInputStreamWithOverriddenConfigurationSources(inputStream, metadata.getConfigurationVersion());
                    }
                    TeeInputStream tis = new TeeInputStream(inputStream, fos);
                    appendFileContent(encoder, instance, metadata, tis);
                }
            });
        }
    }

    private boolean shouldOverrideConfigurationSources(ConfigurationPartMetadata metadata) {
        boolean isVersionGt2 = metadata.getConfigurationVersion() != null
                && Integer.parseInt(metadata.getConfigurationVersion()) > 2;
        boolean isSharedParams = CONTENT_ID_SHARED_PARAMETERS.equals(metadata.getContentIdentifier());
        boolean isMainInstance = confDir.getInstanceIdentifier().equals(metadata.getInstanceIdentifier());
        return isVersionGt2 && isSharedParams && isMainInstance;
    }

    private InputStream toInputStreamWithOverriddenConfigurationSources(InputStream sharedParamsInputStream, String configurationVersion)
            throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (sharedParamsInputStream) {
            var sharedParametersProvider = ParametersProviderFactory.forGlobalConfVersion(configurationVersion)
                    .sharedParametersProvider(sharedParamsInputStream.readAllBytes());
            var sp = sharedParametersProvider
                    .getSharedParameters().toBuilder()
                    .sources(List.of(buildConfProxyConfigurationSource()))
                    .build();
            sharedParametersProvider.getMarshaller().marshall(sp, os);
            return new ByteArrayInputStream(os.toByteArray());
        }
    }

    private SharedParameters.ConfigurationSource buildConfProxyConfigurationSource() {
        SharedParameters.ConfigurationSource confProxySource = new SharedParameters.ConfigurationSource();
        confProxySource.setAddress(SystemProperties.getConfigurationProxyAddress());
        // PS! Need to allocate both external & internal in order not to break 7.4.0 versioned clients of confproxy
        // as their shared-parameters.xsd requires at least 1 internal & 1 external verification cert to be present.
        // If 7.4.0 is no longer supported we can decide the configuration type from whether private-params
        // configuration part is present & then add the verification certs just to the matching type.
        confProxySource.setExternalVerificationCerts(conf.getVerificationCerts());
        confProxySource.setInternalVerificationCerts(conf.getVerificationCerts());
        return confProxySource;
    }

    /**
     * Signs the global configuration directory content.
     *
     * @param contentBytes configuration directory content bytes
     * @param mimeContent  output stream to write to
     * @throws Exception if errors are encountered while writing
     *                   the signed directory content to a temporary location
     */
    private void sign(final byte[] contentBytes, final ByteArrayOutputStream mimeContent) throws Exception {
        String keyId = conf.getActiveSigningKey();
        DigestAlgorithm digestAlgorithmId = conf.getSignatureDigestAlgorithmId();
        SignAlgorithm signAlgoId = getSignatureAlgorithmId(keyId, digestAlgorithmId);
        byte[] digest = calculateDigest(digestAlgorithmId, contentBytes);

        log.debug("Signing directory with signing key '{}' and signing algorithm '{}'", keyId, signAlgoId);

        String signature = getSignature(keyId, signAlgoId, digest);

        mimeContent.write(envelopeHeader.getBytes());

        try (MultipartEncoder encoder = new MultipartEncoder(mimeContent, envelopeBoundary)) {
            encoder.startPart(mpMixedContentType(dataBoundary));
            encoder.write(contentBytes);
            DigestAlgorithm hashURI = hashCalculator.getAlgoURI();
            Path verificationCertPath = conf.getCertPath(keyId);

            encoder.startPart(MimeTypes.BINARY, new String[]{
                    HEADER_CONTENT_TRANSFER_ENCODING + ": base64",
                    HEADER_SIG_ALGO_ID + ": " + signAlgoId.uri(),
                    HEADER_VERIFICATION_CERT_HASH + ": " + getVerificationCertHash(verificationCertPath) + "; "
                            + HEADER_HASH_ALGORITHM_ID + "=" + hashURI.uri()});
            encoder.write(signature.getBytes());
        }

        log.debug("Generated signed directory:\n{}\n", mimeContent);

        Files.write(tempConfPath, mimeContent.toByteArray());

        log.debug("Written signed directory to '{}'", tempConfPath);
    }

    /**
     * Computes the verification hash of the certificate at the given path.
     *
     * @param certPath path to the certificate file
     * @return verification hash for the certificate
     * @throws Exception if failed to open the certificate file
     */
    private String getVerificationCertHash(final Path certPath) throws Exception {
        try (InputStream is = new FileInputStream(certPath.toFile())) {
            byte[] certBytes = CryptoUtils.readCertificate(is).getEncoded();

            return hashCalculator.calculateFromBytes(certBytes);
        }
    }

    /**
     * Opens a stream for writing the configuration file describes by the metadata to the target location.
     *
     * @param targetPath location to write the file to
     * @param metadata   describes the configuration file
     * @return output stream for writing the file
     * @throws IOException if errors during file operations occur
     */
    private FileOutputStream createFileOutputStream(final Path targetPath, final ConfigurationPartMetadata metadata)
            throws IOException {
        Path filepath = targetPath.resolve(Paths.get(metadata.getInstanceIdentifier(), metadata.getContentLocation()));
        Files.createDirectories(filepath.getParent());
        Path newFile = Files.createFile(filepath);

        log.debug("Copying file '{}' to directory '{}'", newFile.toAbsolutePath(), targetPath);

        return new FileOutputStream(newFile.toAbsolutePath().toFile());
    }

    /**
     * Appends the metadata and hash of a configuration file to the content inside the encoder.
     *
     * @param encoder     generates the configuration directory mime from the given file content
     * @param instance    configuration proxy instance name
     * @param metadata    describes the configuration file
     * @param inputStream contents of the configuration file to compute the hash
     * @throws Exception if the configuration file content could not be appended
     */
    private void appendFileContent(final MultipartEncoder encoder, final String instance,
                                   final ConfigurationPartMetadata metadata, final InputStream inputStream) throws Exception {
        try {
            Path contentLocation = Paths.get(instance, timestamp, metadata.getInstanceIdentifier(),
                    metadata.getContentLocation());

            encoder.startPart(MimeTypes.BINARY,
                    new String[]{
                            HEADER_CONTENT_TRANSFER_ENCODING + ": base64",
                            HEADER_CONTENT_IDENTIFIER + ": "
                                    + metadata.getContentIdentifier()
                                    + "; instance=\""
                                    + metadata.getInstanceIdentifier() + "\"",
                            HEADER_CONTENT_LOCATION + ": /" + contentLocation,
                            HEADER_HASH_ALGORITHM_ID + ": " + hashCalculator.getAlgoURI().uri()
                    });

            encoder.write(hashCalculator.calculateFromStream(inputStream).getBytes());
        } catch (Exception e) {
            log.error("Failed to append '{}' content to directory data", metadata.getContentFileName());

            throw e;
        }
    }

    private SignAlgorithm getSignatureAlgorithmId(String keyId, DigestAlgorithm digestAlgoId) throws Exception {
        var signMechanismName = signerRpcClient.getSignMechanism(keyId);

        return SignAlgorithm.ofDigestAndMechanism(digestAlgoId, signMechanismName);
    }

    /**
     * Generates the signature of the configuration directory data.
     *
     * @param keyId                id of the key used for signing
     * @param signatureAlgorithmId if of the algorithm used for signing
     * @param digest               digest bytes of the directory content
     * @return the configuration directory signature string (base64)
     * @throws Exception if cryptographic operations fail
     */
    private String getSignature(final String keyId, final SignAlgorithm signatureAlgorithmId, final byte[] digest) {
        byte[] signature = signerRpcClient.sign(keyId, signatureAlgorithmId, digest);

        return encodeBase64(signature);
    }
}
