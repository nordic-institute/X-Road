package ee.cyber.sdsb.confproxy.util;

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

import ee.cyber.sdsb.common.conf.globalconf.ConfigurationDirectory;
import ee.cyber.sdsb.common.conf.globalconf.ConfigurationPartMetadata;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.HashCalculator;
import ee.cyber.sdsb.common.util.MimeTypes;
import ee.cyber.sdsb.common.util.MultipartEncoder;
import ee.cyber.sdsb.confproxy.ConfProxyProperties;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.message.Sign;
import ee.cyber.sdsb.signer.protocol.message.SignResponse;

import static ee.cyber.sdsb.common.util.CryptoUtils.*;
import static ee.cyber.sdsb.common.util.MimeUtils.*;

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
     * @param confDir global configuration to be processed
     * @param conf configuration proxy instance configuration
     * @throws Exception in case of errors when a temporary directory
     */
    public OutputBuilder(ConfigurationDirectory confDir,
            ConfProxyProperties conf) throws Exception {
        this.confDir = confDir;
        this.conf = conf;
        setup();
    }

    /**
     * Generates a signed directory MIME for the global configuration and
     * writes the directory contents to a temporary location.
     * @throws Exception if errors occur when reading global configuration files
     */
    public void buildSignedDirectory() throws Exception {
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
    public void moveAndCleanup() throws Exception {
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

    private void build(ByteArrayOutputStream mimeContent) throws Exception {
        try (MultipartEncoder encoder =
                new MultipartEncoder(mimeContent, dataBoundary)) {
            DateTime expireDate =
                    new DateTime().plusSeconds(conf.getValidityIntervalSeconds());
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

    private void sign(byte[] contentBytes, ByteArrayOutputStream mimeContent)
                    throws Exception {
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

    private String getVerificationCertHash(Path certPath) throws Exception {
        try (InputStream is = new FileInputStream(certPath.toFile())) {
            byte[] certBytes = CryptoUtils.readCertificate(is).getEncoded();
            return hashCalculator.calculateFromBytes(certBytes);
        }
    }

    private FileOutputStream createFileOutputStream(Path targetPath,
            ConfigurationPartMetadata metadata) throws Exception {
        Path filepath =
                targetPath.resolve(Paths.get(metadata.getInstanceIdentifier(),
                        metadata.getContentLocation()));
        Files.createDirectories(filepath.getParent());
        Path newFile = Files.createFile(filepath);
        log.debug("Copying file '{}' to directory '{}'",
                newFile.toAbsolutePath(), targetPath);

        return new FileOutputStream(newFile.toAbsolutePath().toFile());
    }

    private void appendFileContent(MultipartEncoder encoder, String instance,
            ConfigurationPartMetadata metadata, InputStream inputStream)
                    throws Exception {
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

    private String signHelper(String keyId, String signatureAlgorithmId,
            byte[] data) throws Exception {
        String digestAlgorithmId = getDigestAlgorithmId(signatureAlgorithmId);

        byte[] digest = calculateDigest(digestAlgorithmId, data);

        SignResponse response =
                SignerClient.execute(
                        new Sign(keyId, digestAlgorithmId, digest));

        return encodeBase64(response.getSignature());
    }
}
