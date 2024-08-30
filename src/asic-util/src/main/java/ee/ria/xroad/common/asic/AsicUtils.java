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
package ee.ria.xroad.common.asic;

import ee.ria.xroad.common.CodedException;

import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.enumerations.RevocationType;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static ee.ria.xroad.common.asic.AsicContainerVerifier.getSigner;
import static ee.ria.xroad.common.asic.AsicHelper.stripSlash;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Helper functions for ASIC container verification utilities.
 */
public final class AsicUtils {

    private AsicUtils() {
    }

    /**
     * Prepares the provided string for use in filenames.
     *
     * @param str the string
     * @return resulting string with unsuitable characters escaped
     */
    @SneakyThrows
    public static String escapeString(String str) {
        String urlEncoded =
                URLEncoder.encode(str, StandardCharsets.UTF_8);
        return urlEncoded.replace("/", "%2F");
    }

    /**
     * Generates the output in case of failed verification.
     *
     * @param cause throwable that caused the failure
     * @return failed verification output string
     */
    public static String buildFailureOutput(Throwable cause) {
        String message = getMessageFromCause(cause);

        return "Verification failed: " + message;
    }

    /**
     * Generates the output in case of successful verification.
     *
     * @param verifier container verifier that was successful
     * @return successful verification output string
     */
    public static String buildSuccessOutput(AsicContainerVerifier verifier) {
        StringBuilder builder = new StringBuilder();

        builder.append("Verification successful.\n");
        builder.append("Signer\n");
        builder.append("    Certificate:\n");
        appendCert(builder, verifier.getSignerCert());
        builder.append("    ID: " + verifier.getSignerName() + "\n");
        builder.append("OCSP response\n");
        builder.append("    Signed by:\n");
        appendCert(builder, verifier.getOcspCert());
        builder.append("    Produced at: " + verifier.getOcspDate() + "\n");
        builder.append("Timestamp\n");
        builder.append("    Signed by:\n");
        appendCert(builder, verifier.getTimestampCert());
        builder.append("    Date: " + verifier.getTimestampDate() + "\n");

        verifier.getAttachmentHashes().forEach(h -> builder.append(h + "\n"));

        return builder.toString();
    }

    public static boolean isLegacyContainer(String fileName) throws IOException {
        try (ZipFile zipFile = new ZipFile(fileName)) {
            List<String> containerEntries = Collections.list(zipFile.entries()).stream()
                    .map(e -> stripSlash(e.getName()))
                    .toList();

            return containerEntries.contains(AsicContainerEntries.ENTRY_SIG_HASH_CHAIN)
                    && containerEntries.contains(AsicContainerEntries.ENTRY_SIG_HASH_CHAIN_RESULT);
        }
    }

    public static String getMessageFromContainer(String fileName) throws IOException {
        try (ZipFile zipFile = new ZipFile(fileName)) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (AsicContainerEntries.ENTRY_MESSAGE.equalsIgnoreCase(stripSlash(entry.getName()))) {
                    try (InputStream inputStream = zipFile.getInputStream(entry)) {
                        return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    }
                }
            }
            throw new NoSuchElementException(String.format("No '%s' found in container", AsicContainerEntries.ENTRY_MESSAGE));
        }
    }

    /**
     * Generates the output in case of successful verification.
     *
     * @param diagnosticData resulting from container verification
     * @param message        contents of the message.xml file
     * @return successful verification output string
     */
    public static String buildSuccessOutput(DiagnosticData diagnosticData, String message) {
        var signature = diagnosticData.getSignatures().get(0);
        var signingCert = signature.getSigningCertificate();
        X509Certificate cert = readCertificate(signingCert.getBinaries());

        var revocation = diagnosticData.getLatestRevocationDataForCertificate(signingCert);
        var ocsp = Optional.of(revocation).filter(r -> RevocationType.OCSP == r.getRevocationType()).orElseThrow();
        var ocspCert = readCertificate(ocsp.getSigningCertificate().getBinaries());

        var timestamp = diagnosticData.getTimestampList().stream()
                .filter(t -> t.getTimestampedSignatures().contains(signature))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No timestamp found for signature"));
        var timestampCert = readCertificate(timestamp.getSigningCertificate().getBinaries());

        StringBuilder builder = new StringBuilder();
        builder.append("Verification successful.\n");
        builder.append("Signer\n");
        builder.append("    Certificate:\n");
        appendCert(builder, cert);
        builder.append("    ID: " + getSigner(message) + "\n");
        builder.append("OCSP response\n");
        builder.append("    Signed by:\n");
        appendCert(builder, ocspCert);
        builder.append("    Produced at: " + ocsp.getProductionDate() + "\n");
        builder.append("Timestamp\n");
        builder.append("    Signed by:\n");
        appendCert(builder, timestampCert);
        builder.append("    Date: " + timestamp.getProductionTime() + "\n");

        //TODO: attachmenthashes - are they even needed?

        return builder.toString();
    }

    private static void appendCert(StringBuilder builder, X509Certificate cert) {
        builder.append("        Subject: " + cert.getSubjectX500Principal().toString() + "\n");
        builder.append("        Issuer: " + cert.getIssuerX500Principal().toString() + "\n");
        builder.append("        Serial number: " + cert.getSerialNumber() + "\n");
        builder.append("        Valid from: " + cert.getNotBefore() + "\n");
        builder.append("        Valid until: " + cert.getNotAfter() + "\n");
    }

    private static String getMessageFromCause(Throwable cause) {
        if (cause instanceof CodedException ce) {
            return ce.getFaultString();
        }

        return cause.getMessage();
    }

    /**
     * Truncates string to certain length
     *
     * @return string delimited to given length
     */
    public static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() > max) {
            return s.substring(0, max);
        } else {
            return s;
        }
    }
}
