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
package ee.ria.xroad.asicverifier;

import java.io.FileOutputStream;
import java.io.IOException;
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
                    + e);
        }
    }

    /**
     * If we can read instance identifier of this security server, we can
     * consider conf path correct.
     */
    private static void verifyConfPathCorrectness() {
        GlobalConf.getInstanceIdentifier();
    }

    private static void verifyAsic(String fileName) {
        out.println("Verifying ASiC container \"" + fileName + "\" ...");

        try {
            AsicContainerVerifier verifier = new AsicContainerVerifier(fileName);
            verifier.verify();

            onVerificationSucceeded(verifier);
        } catch (Exception e) {
            onVerificationFailed(e);
        }
    }

    @SuppressWarnings("resource") //
    private static void onVerificationSucceeded(
            AsicContainerVerifier verifier) throws IOException {
        out.println(AsicUtils.buildSuccessOutput(verifier));

        out.print("\nWould you like to extract the signed files? (y/n) ");

        if ("y".equalsIgnoreCase(new Scanner(System.in).nextLine())) {
            AsicContainer asic = verifier.getAsic();
            writeToFile(AsicContainerEntries.ENTRY_MESSAGE, asic.getMessage());

            out.println("Files successfully extracted.");
        }
    }

    private static void onVerificationFailed(Throwable cause) {
        System.err.println(AsicUtils.buildFailureOutput(cause));
    }

    private static void writeToFile(String fileName, String contents) throws IOException {
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
