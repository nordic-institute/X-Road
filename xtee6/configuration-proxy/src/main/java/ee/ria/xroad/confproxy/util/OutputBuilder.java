/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.confproxy.util;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import ee.ria.xroad.common.conf.globalconf.ConfigurationDirectory;
import ee.ria.xroad.common.conf.globalconf.ConfigurationPartMetadata;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.HashCalculator;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MultipartEncoder;
import ee.ria.xroad.confproxy.ConfProxyProperties;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;

import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmId;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_IDENTIFIER;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_LOCATION;
import static ee.ria.xroad.common.util.MimeUtils
        .HEADER_CONTENT_TRANSFER_ENCODING;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_EXPIRE_DATE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGORITHM_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_SIG_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_VERIFICATION_CERT_HASH;
import static ee.ria.xroad.common.util.MimeUtils.mpMixedContentType;
import static ee.ria.xroad.common.util.MimeUtils.mpRelatedContentType;
import static ee.ria.xroad.common.util.MimeUtils.randomBoundary;

/**
 * Utility class that encapsulates the process of signing the downloaded
 * global configuration and moving it to the target location.
 */
@Slf4j
public class OutputBuilder {

    public static final String SIGNED_DIRECTORY_NAME = "conf";

    private final ConfigurationDirectory confDir;
    private final ConfProxyProperties conf;

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
     * @param confDirectory global configuration to be processed
     * @param configuration configuration proxy instance configuration
     * @throws Exception in case of errors when a temporary directory
     */
    public OutputBuilder(final ConfigurationDirectory confDirectory,
            final ConfProxyProperties configuration) throws Exception {
        this.confDir = confDirectory;
        this.conf = configuration;
        setup();
    }

    /**
     * Generates a signed directory MIME for the global configuration and
     * writes the directory contents to a temporary location.
     * @throws Exception if errors occur when reading global configuration files
     */
    public final void buildSignedDirectory() throws Exception {
        try (ByteArrayOutputStream mimeContent = new ByteArrayOutputStream()) {
            build(mimeContent);
            log.debug("Generated directory content:\n{}\n",
                    mimeContent.toString());
            byte[] contentBytes = mimeContent.toByteArray();
            mimeContent.reset();
            sign(contentBytes, mimeContent);
            Files.write(tempConfPath, mimeContent.toByteArray());
            log.debug("Written signed directory to '{}'", tempConfPath);
        }
    }

    /**
     * Moves the signed global configuration to the location where it is
     * accessible to clients and cleans up any remaining temporary files.
     * @throws Exception in case of unsuccessful file operations
     */
    public final void moveAndCleanup() throws Exception {
        String path = conf.getConfigurationTargetPath();
        Path targetPath = Paths.get(path, timestamp);
        Path targetConf = Paths.get(path, SIGNED_DIRECTORY_NAME);
        Files.createDirectories(targetPath.getParent());
        log.info("Moving '{}' to '{}'", tempDirPath, targetPath);
        Files.move(tempDirPath, targetPath);
        log.info("Moving '{}' to '{}'", tempConfPath, targetConf);
        Files.move(tempConfPath, targetConf, StandardCopyOption.ATOMIC_MOVE);
        FileUtils.deleteDirectory(tempDirPath.toFile());
    }

    /**
     * Setup reference data and temporary directory for the output builder.
     * @throws Exception if temporary directory could not be created
     */
    private void setup() throws Exception {
        String tempDir = conf.getTemporaryDirectoryPath();
        String hashAlgURI = conf.getHashAlgorithmURI();

        hashCalculator = new HashCalculator(hashAlgURI);
        timestamp = Long.toString(new Date().getTime());
        tempConfPath = Paths.get(tempDir, SIGNED_DIRECTORY_NAME);
        tempDirPath = Paths.get(tempDir, timestamp);
        Files.createDirectories(tempDirPath);
        FileUtils.cleanDirectory(tempDirPath.toFile());

        dataBoundary = randomBoundary();
        envelopeBoundary = randomBoundary();
        envelopeHeader = HEADER_CONTENT_TYPE + ": "
                + mpRelatedContentType(envelopeBoundary) + "\n\n";
    }

    /**
     * Generates global configuration directory content MIME.
     * @param mimeContent output stream to write to
     * @throws Exception if reading global configuration files fails
     */
    private void build(final ByteArrayOutputStream mimeContent)
            throws Exception {
        try (MultipartEncoder encoder =
                new MultipartEncoder(mimeContent, dataBoundary)) {
            DateTime expireDate =
                    new DateTime().plusSeconds(
                            conf.getValidityIntervalSeconds());
            encoder.startPart(null, new String[] {
                        HEADER_EXPIRE_DATE + ": "
                                + expireDate.toDateTime(DateTimeZone.UTC)
                    });

            String instance = conf.getInstance();
            confDir.eachFile((metadata, inputStream) -> {
                try (FileOutputStream fos =
                        createFileOutputStream(tempDirPath, metadata)) {
                    TeeInputStream tis = new TeeInputStream(inputStream, fos);
                    appendFileContent(encoder, instance, metadata, tis);
                }
            });
        }
    }

    /**
     * Signs the global configuration directory content.
     * @param contentBytes configuration directory content bytes
     * @param mimeContent output stream to write to
     * @throws Exception if errors are encountered while writing
     * the signed directory content to a temporary location
     */
    private void sign(final byte[] contentBytes,
            final ByteArrayOutputStream mimeContent) throws Exception {
        String algId = conf.getSignatureAlgorithmId();
        String keyId = conf.getActiveSigningKey();
        log.debug("Signing directory with signing key '{}' "
                + "and signing algorithm '{}'", keyId, algId);
        String signature = signHelper(keyId, algId, contentBytes);

        mimeContent.write(envelopeHeader.getBytes());
        try (MultipartEncoder encoder =
                new MultipartEncoder(mimeContent, envelopeBoundary)) {
            encoder.startPart(mpMixedContentType(dataBoundary));
            encoder.write(contentBytes);
            String algURI = CryptoUtils.getSignatureAlgorithmURI(algId);
            String hashURI = hashCalculator.getAlgoURI();
            Path verificatioCertPath = conf.getCertPath(keyId);
            encoder.startPart(MimeTypes.BINARY, new String[] {
                    HEADER_CONTENT_TRANSFER_ENCODING + ": base64",
                    HEADER_SIG_ALGO_ID + ": " + algURI,
                    HEADER_VERIFICATION_CERT_HASH + ": "
                            + getVerificationCertHash(verificatioCertPath)
                            + "; " + HEADER_HASH_ALGORITHM_ID + "=" + hashURI
                });
            encoder.write(signature.getBytes());
        }
        log.debug("Generated signed directory:\n{}\n", mimeContent.toString());

        Files.write(tempConfPath, mimeContent.toByteArray());
        log.debug("Written signed directory to '{}'", tempConfPath);
    }

    /**
     * Computes the verification hash of the certificate at the given path.
     * @param certPath path to the certificate file
     * @return verification hash for the certificate
     * @throws Exception if failed to open the certificate file
     */
    private String getVerificationCertHash(final Path certPath)
            throws Exception {
        try (InputStream is = new FileInputStream(certPath.toFile())) {
            byte[] certBytes = CryptoUtils.readCertificate(is).getEncoded();
            return hashCalculator.calculateFromBytes(certBytes);
        }
    }

    /**
     * Opens a stream for writing the configuration file describes by the
     * metadata to the target location.
     * @param targetPath location to write the file to
     * @param metadata describes the configuration file
     * @return output stream for writing the file
     * @throws Exception if errors during file operations occur
     */
    private FileOutputStream createFileOutputStream(final Path targetPath,
            final ConfigurationPartMetadata metadata) throws Exception {
        Path filepath =
                targetPath.resolve(Paths.get(metadata.getInstanceIdentifier(),
                        metadata.getContentLocation()));
        Files.createDirectories(filepath.getParent());
        Path newFile = Files.createFile(filepath);
        log.debug("Copying file '{}' to directory '{}'",
                newFile.toAbsolutePath(), targetPath);

        return new FileOutputStream(newFile.toAbsolutePath().toFile());
    }

    /**
     * Appends the metadata and hash of a configuration file to the content
     * inside the encoder.
     * @param encoder generates the configuration directory mime from the given
     * file content
     * @param instance configuration proxy instance name
     * @param metadata describes the configuration file
     * @param inputStream contents of the configuration file to
     * compute the hash
     * @throws Exception if the configuration file content could
     * not be appended
     */
    private void appendFileContent(final MultipartEncoder encoder,
            final String instance, final ConfigurationPartMetadata metadata,
            final InputStream inputStream) throws Exception {
        try {
            Path contentLocation =
                    Paths.get(instance, timestamp,
                            metadata.getInstanceIdentifier(),
                            metadata.getContentLocation());
            encoder.startPart(MimeTypes.BINARY,
                    new String[] {
                        HEADER_CONTENT_TRANSFER_ENCODING + ": base64",
                        HEADER_CONTENT_IDENTIFIER + ": "
                                + metadata.getContentIdentifier()
                                + "; instance=\""
                                + metadata.getInstanceIdentifier()  + "\"",
                        HEADER_CONTENT_LOCATION + ": /" + contentLocation,
                        HEADER_HASH_ALGORITHM_ID + ": "
                            + hashCalculator.getAlgoURI()
                    });

            encoder.write(
                    hashCalculator.calculateFromStream(inputStream).getBytes());
        } catch (Exception e) {
            log.error("Failed to append '{}' content to directory data",
                    metadata.getContentFileName());
            throw e;
        }
    }

    /**
     * Generates the signature of the configuration directory data.
     * @param keyId id of the key used for signing
     * @param signatureAlgorithmId if of the algorithm used for signing
     * @param data generated bytes of the directory content
     * @return the configuration directory signature string
     * @throws Exception if cryptographic operations fail
     */
    private String signHelper(final String keyId,
            final String signatureAlgorithmId, final byte[] data)
                    throws Exception {
        String digestAlgorithmId = getDigestAlgorithmId(signatureAlgorithmId);

        byte[] digest = calculateDigest(digestAlgorithmId, data);

        SignResponse response =
                SignerClient.execute(
                        new Sign(keyId, digestAlgorithmId, digest));

        return encodeBase64(response.getSignature());
    }
}
