package ee.ria.xroad.common.asic;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import lombok.SneakyThrows;

import ee.ria.xroad.common.CodedException;

/**
 * Helper functions for ASIC container verification utilities.
 */
public final class AsicUtils {

    private AsicUtils() {
    }

    /**
     * Prepares the provided string for use in filenames.
     * @param str the string
     * @return resulting string with unsuitable characters escaped
     */
    @SneakyThrows
    public static String escapeString(String str) {
        String urlEncoded =
                URLEncoder.encode(str, StandardCharsets.UTF_8.name());
        return urlEncoded.replace("/", "%2F");
    }

    /**
     * Generates the output in case of failed verification.
     * @param cause throwable that caused the failure
     * @return failed verification output string
     */
    public static String buildFailureOutput(Throwable cause) {
        String message = getMessageFromCause(cause);

        return "Verification failed: " + message;
    }

    /**
     * Generates the output in case of successful verification.
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

    private static void appendCert(StringBuilder builder, X509Certificate cert) {
        builder.append("        Subject: " + cert.getSubjectDN().getName() + "\n");
        builder.append("        Issuer: " + cert.getIssuerDN().getName() + "\n");
        builder.append("        Serial number: " + cert.getSerialNumber() + "\n");
        builder.append("        Valid from: " + cert.getNotBefore() + "\n");
        builder.append("        Valid until: " + cert.getNotAfter() + "\n");
    }

    private static String getMessageFromCause(Throwable cause) {
        if (cause instanceof CodedException) {
            return ((CodedException) cause).getFaultString();
        }

        return cause.getMessage();
    }
}
