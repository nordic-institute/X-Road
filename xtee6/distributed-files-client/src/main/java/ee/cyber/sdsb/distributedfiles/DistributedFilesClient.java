package ee.cyber.sdsb.distributedfiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeConfig;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.serverconf.ServerConf;
import ee.cyber.sdsb.common.conf.serverconf.model.GlobalConfDistributorType;
import ee.cyber.sdsb.distributedfiles.handler.DefaultFileHandler;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.X_INVALID_SIGNATURE_VALUE;
import static ee.cyber.sdsb.common.util.CryptoUtils.getSignatureAlgorithmId;
import static ee.cyber.sdsb.common.util.CryptoUtils.readCertificate;
import static ee.cyber.sdsb.common.util.MimeUtils.HEADER_CONTENT_DATE;
import static ee.cyber.sdsb.common.util.MimeUtils.HEADER_CONTENT_TYPE;

/**
 * A client that periodically queries files from Central.
 *
 * Response from central server is expected to be a multipart response
 * containing the files and signature. Also, the header is expected
 * to contain signature algorithm identifier and ID of the signing key.
 */
@Slf4j
@DisallowConcurrentExecution
public class DistributedFilesClient implements Job {

    private static final DateTimeFormatter DATE_TIME_PARSER =
            ISODateTimeFormat.dateTimeParser();

    private static final String HEADER_CONTENT_FILE_NAME = "content-file-name";

    // Holds the last error potentially produced by any of the file distributors.
    private Exception lastAttemptError;

    public static void execute() throws Exception {
        new DistributedFilesClient().execute(null);
    }

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        lastAttemptError = null;
        try {
            doExecute();
        } catch (Exception e) {
            log.error("Could not fetch files", e);
            throw new JobExecutionException(e);
        }
    }

    private void doExecute() throws Exception {
        log.trace("DistributedFilesClient executing...");

        List<GlobalConfDistributorType> fileDistributors =
                ServerConf.getFileDistributors();

        if (!shouldFetch(fileDistributors)) {
            return;
        }

        for (int idx = 0; idx < fileDistributors.size(); ++idx) {
            if (fetchConfiguration(fileDistributors.get(idx), idx)) {
                // Got the conf.
                return;
            }
        }

        if (lastAttemptError != null) {
            throw lastAttemptError;
        }

        // All the distributors failed.
        throw new Exception("All "
                + (fileDistributors.size() > 1
                    ? fileDistributors.size() + " " : "")
                + "attempts failed");
    }

    private boolean fetchConfiguration(
            GlobalConfDistributorType fileDistributor, int idx) {
        try {
            URL url = new URL(fileDistributor.getUrl());
            X509Certificate cert = readCertificate(
                    fileDistributor.getVerificationCert().getData());

            SignedMultipart signedContent = fetch(url);
            verifySignature(signedContent, cert);
            parseContent(signedContent);

            return true;
        } catch (Exception e) {
            String url = fileDistributor.getUrl() == null
                    ? "N/A" : fileDistributor.getUrl();
            String message = String.format(
                    "Could not fetch files from %s. distributor (URL: %s): %s",
                    idx, url, e);
            log.warn(message);
            lastAttemptError = new Exception(message);
            return false;
        }
    }

    private boolean shouldFetch(
            List<GlobalConfDistributorType> fileDistributors) throws Exception {
        if (SystemProperties.isDistributorEnabled()) {
            if (fileDistributors.isEmpty()) {
                throw new Exception("Should fetch new GlobalConf, but no "
                        + "file distributors provided");
            }

            return true;
        } else {
            log.trace("DistributedFilesClient is not enabled");
            return false;
        }
    }

    SignedMultipart fetch(URL url) throws Exception {
        log.trace("Fetching signed multipart from {}", url);

        try (InputStream is = url.openStream()) {
            return fetch(is);
        }
    }

    SignedMultipart fetch(InputStream stream) throws Exception {
        MimeStreamParser parser = new MimeStreamParser();

        SignedMultipart signedMultipart = new SignedMultipart(parser);

        log.trace("Parsing distributed files multipart");

        parser.setContentHandler(signedMultipart);
        parser.parse(stream);

        signedMultipart.verifyParts();
        return signedMultipart;
    }

    void verifySignature(SignedMultipart response,
            X509Certificate verificationCert) throws Exception {
        verifySignature(response, verificationCert.getPublicKey());
    }

    void verifySignature(SignedMultipart response, PublicKey verificationKey)
            throws Exception {
        log.trace("Verifying signed data...");

        Signature verifier = Signature.getInstance(
            getSignatureAlgorithmId(response.getSignatureAlgoId()));

        verifier.initVerify(verificationKey);
        verifier.update(response.getSignedData());

        if (!verifier.verify(response.getSignatureValue())) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE,
                    "Failed to verify signature value");
        }
    }

    void parseContent(SignedMultipart signedMultipart) throws Exception {
        MimeConfig config = new MimeConfig();
        config.setHeadlessParsing(signedMultipart.getSignedDataContentType());

        MimeStreamParser parser = new MimeStreamParser(config);
        parser.setContentHandler(new SignedPartHandler(parser));

        log.trace("Parsing signed content wrapper");
        parser.parse(new ByteArrayInputStream(signedMultipart.getSignedData()));
    }

    void handleFileReceived(DistributedFile file) throws Exception {
        String identifier = file.getFileName();

        log.trace("handleFileReceived({})", identifier);

        DistributedFileHandler handler = getHandler(identifier);
        if (handler != null) {
            handler.handle(file);
        } else {
            log.warn("Received unknown file '{}'", identifier);
        }
    }

    DistributedFileHandler getHandler(String identifier) throws Exception {
        log.trace("getHandler({})", identifier);
        // Here we can return more specialized handlers (i.e. actor based that
        // will notify other parties) in the future.
        return new DefaultFileHandler();
    }

    boolean verifySignatureFreshness(DateTime signDate, DateTime atDate) {
        int allowedFreshness =
                SystemProperties.getDistributedFilesSignatureFreshness();
        if (allowedFreshness == -1) { // freshness check is disabled
            return true;
        }

        return !signDate.plusMinutes(allowedFreshness).isBefore(atDate);
    }

    // ------------------------------------------------------------------------

    class SignedPartHandler extends AbstractMultipartContentHandler {

        SignedPartHandler(MimeStreamParser parser) {
            super(parser);
        }

        @Override
        public void body(BodyDescriptor bd, InputStream is)
                throws MimeException, IOException {
            String contentType = getHeader(HEADER_CONTENT_TYPE);
            if (contentType == null) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "%s not specified for signed data part",
                        HEADER_CONTENT_TYPE);
            }

            String dateString = getHeader(HEADER_CONTENT_DATE);
            if (dateString == null) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "%s not specified for signed data part",
                        HEADER_CONTENT_DATE);
            }

            DateTime currentDate = new DateTime();
            DateTime signDate = DATE_TIME_PARSER.parseDateTime(dateString);
            if (!verifySignatureFreshness(signDate, currentDate)) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Signature (%s) is too old (verified at %s)",
                        signDate, currentDate);
            }

            MimeConfig config = new MimeConfig();
            config.setHeadlessParsing(contentType);

            MimeStreamParser parser = new MimeStreamParser(config);
            parser.setContentHandler(
                    new DistributedFilePartHandler(parser, signDate));

            log.trace("Parsing distributed files");
            parser.parse(is);
        }
    }

    class DistributedFilePartHandler extends AbstractMultipartContentHandler {

        @Getter private final DateTime signDate;

        DistributedFilePartHandler(MimeStreamParser parser, DateTime signDate) {
            super(parser);

            this.signDate = signDate;
        }

        @Override
        public void body(BodyDescriptor bd, InputStream is)
                throws MimeException, IOException {
            String file = getHeader(HEADER_CONTENT_FILE_NAME);
            if (file == null) {
                log.error("{} not specified for part",
                        HEADER_CONTENT_FILE_NAME);
                return;
            }

            try {
                handleFileReceived(new DistributedFile(file, signDate, is));
            } catch (Exception e) {
                log.error("Error handling content '{}': {}", file, e);
            }
        }
    }
}
