package ee.ria.xroad.asicverifier;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.asic.AsicContainer;
import ee.ria.xroad.common.asic.AsicContainerEntries;
import ee.ria.xroad.common.asic.AsicContainerVerifier;
import ee.ria.xroad.common.asic.AsicUtils;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;

import static java.lang.System.out;

/**
 * ASiC container verifier utility program.
 */
public final class AsicVerifierMain {

    private AsicVerifierMain() {
    }

    /**
     * Main program entry point.
     * @param args program arguments
     * @throws Exception in case of errors
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            showUsage();
        } else {
            loadConf(args[0]);
            verifyAsic(args[1]);
        }
    }

    private static void loadConf(String confPath) {
        System.setProperty(SystemProperties.CONFIGURATION_PATH, confPath);

        out.println("Loading configuration from " + confPath + "...");
        try {
            GlobalConf.reload();
            verifyConfPathCorrectness();
        } catch (CodedException e) {
            System.err.println("Unable to load configuration: "
                    + e.getFaultString());
            System.exit(1);
        }
    }

    /**
     * If we can read instance identifier of this security server, we can
     * consider conf path correct.
     */
    private static void verifyConfPathCorrectness() {
        GlobalConf.getInstanceIdentifier();
    }

    private static void verifyAsic(String fileName) throws Exception {
        out.println("Verifying ASiC container \"" + fileName + "\" ...");

        try {
            AsicContainerVerifier verifier = new AsicContainerVerifier(fileName);
            verifier.verify();

            onVerificationSucceeded(verifier);
        } catch (Exception e) {
            onVerificationFailed(e);
            System.exit(1);
        }
    }

    @SuppressWarnings("resource") //
    private static void onVerificationSucceeded(
            AsicContainerVerifier verifier) throws Exception {
        out.println(AsicUtils.buildSuccessOutput(verifier));

        out.print("\nWould you like to extract the signed files? (y/n) ");

        if (new Scanner(System.in).nextLine().equalsIgnoreCase("y")) {
            AsicContainer asic = verifier.getAsic();
            writeToFile(AsicContainerEntries.ENTRY_MESSAGE, asic.getMessage());

            out.println("Files successfully extracted.");
        }
    }

    private static void onVerificationFailed(Throwable cause) {
        System.err.println(AsicUtils.buildFailureOutput(cause));
    }

    private static void writeToFile(String fileName, String contents)
            throws Exception {
        try (FileOutputStream file = new FileOutputStream(fileName)) {
            file.write(contents.getBytes(StandardCharsets.UTF_8));
        }

        out.println("Created file " + fileName);
    }

    private static void showUsage() {
        out.println("Usage: AsicVerifier "
                + "<configuration path> <asic container>");
    }
}
