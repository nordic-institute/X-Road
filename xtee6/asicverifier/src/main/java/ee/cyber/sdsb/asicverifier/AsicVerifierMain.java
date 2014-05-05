package ee.cyber.sdsb.asicverifier;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Scanner;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.asic.AsicContainer;
import ee.cyber.sdsb.common.conf.GlobalConf;

import static java.lang.System.out;

public final class AsicVerifierMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            showUsage();
        } else {
            loadConf(args[0]);
            verifyAsic(args[1]);
        }
    }

    private static void loadConf(String fileName) {
        System.setProperty(SystemProperties.GLOBAL_CONFIGURATION_FILE, fileName);
        try {
            GlobalConf.reload();
        } catch (CodedException e) {
            System.err.println("Unable to load configuration: "
                    + e.getFaultString());
            System.exit(1);
        }
    }

    private static void verifyAsic(String fileName) throws Exception {
        out.println("Verifying ASiC container \"" + fileName + "\" ...");

        try {
            FileInputStream file = new FileInputStream(fileName);
            AsicContainerVerifier verifier = AsicContainerVerifier.create(file);
            verifier.verify();

            onVerificationSucceeded(verifier);
        } catch (Exception e) {
            onVerificationFailed(e);
        }
    }

    private static void onVerificationSucceeded(
            AsicContainerVerifier verifier) throws Exception {
        out.println("Verification successful.");
        out.println("Signer");
        out.println("    Certificate:");
        printCert(verifier.signerCert);
        out.println("    ID: " + verifier.getSignerName());
        out.println("OCSP response");
        out.println("    Signed by:");
        printCert(verifier.ocspCert);
        out.println("    Produced at: " + verifier.ocspDate);
        out.println("Timestamp");
        out.println("    Signed by:");
        printCert(verifier.timestampCert);
        out.println("    Date: " + verifier.timestampDate);

        for (String log : verifier.getAttachmentHashes()) {
            out.println(log);
        }

        out.print("\nWould you like to extract the signed files? (y/n) ");

        if (new Scanner(System.in).nextLine().equalsIgnoreCase("y")) {
            AsicContainer asic = verifier.getContainer();
            writeToFile(AsicContainer.ENTRY_MESSAGE, asic.getMessage());

            out.println("Files successfully extracted.");
        }
    }

    private static void printCert(X509Certificate cert) {
        out.println("        Subject: " + cert.getSubjectDN().getName());
        out.println("        Issuer: " + cert.getIssuerDN().getName());
        out.println("        Serial number: " + cert.getSerialNumber());
        out.println("        Valid from: " + cert.getNotBefore());
        out.println("        Valid until: " + cert.getNotAfter());
    }

    private static void onVerificationFailed(Throwable cause) {
        String message = getMessageFromCause(cause);

        System.err.println("Verification failed: " + message);
    }

    private static void writeToFile(String fileName, String contents)
            throws Exception {
        FileOutputStream file = new FileOutputStream(fileName);
        file.write(contents.getBytes(StandardCharsets.UTF_8));
        file.close();

        out.println("Created file " + fileName);
    }

    private static void showUsage() {
        out.println("Usage: AsicVerifier " +
                "<configuration file> <asic container>");
    }

    private static String getMessageFromCause(Throwable cause) {
        if (cause instanceof CodedException) {
            return ((CodedException) cause).getFaultString();
        }

        return cause.getMessage();
    }
}
