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
package ee.ria.xroad.asicverifier;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.asic.AsicContainerEntries;
import ee.ria.xroad.common.asic.AsicContainerVerifier;
import ee.ria.xroad.common.asic.AsicUtils;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;

import org.apache.commons.io.IOUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ASiC container verifier utility program.
 */
public final class AsicVerifierMain {

    private AsicVerifierMain() {
    }

    /**
     * Main program entry point.
     *
     * @param args program arguments
     */
    public static void main(String[] args) {
        if (args.length == 1 && "--version".equals(args[0])) {
            showVersion();
        } else if (args.length != 2) {
            showUsage();
        } else {
            loadConf(args[0]);
            verifyAsic(args[1]);
        }
    }

    private static void loadConf(String confPath) {
        System.setProperty(SystemProperties.CONFIGURATION_PATH, confPath);

        System.out.println("Loading configuration from " + confPath + "...");
        try {
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
        System.out.println("Verifying ASiC container \"" + fileName + "\" ...");

        AsicContainerVerifier verifier = null;
        try {
            verifier = new AsicContainerVerifier(fileName);
            verifier.verify();

            onVerificationSucceeded(verifier);
        } catch (Exception e) {
            onVerificationFailed(e);
        }
        extractMessage(fileName);
    }

    @SuppressWarnings("resource")
    private static void onVerificationSucceeded(AsicContainerVerifier verifier) {
        System.out.println(AsicUtils.buildSuccessOutput(verifier));
    }

    private static void onVerificationFailed(Throwable cause) {
        cause.printStackTrace();
        System.err.println(AsicUtils.buildFailureOutput(cause));
    }

    private static void extractMessage(String fileName) {
        System.out.print("\nWould you like to extract the signed files? (y/n) ");

        if ("y".equalsIgnoreCase(new Scanner(System.in).nextLine())) {

            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(fileName)))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (AsicContainerEntries.ENTRY_MESSAGE.equalsIgnoreCase(entry.getName())) {
                        writeToFile(AsicContainerEntries.ENTRY_MESSAGE, zis);
                    }
                    if (entry.getName().startsWith(AsicContainerEntries.ENTRY_ATTACHMENT)) {
                        writeToFile(entry.getName(), zis);
                    }
                }
                System.out.println("Files successfully extracted.");
            } catch (IOException e) {
                System.out.println("Unable to extract files");
            }
        }
    }

    @SuppressWarnings("javasecurity:S2083")
    private static void writeToFile(String fileName, InputStream contents) throws IOException {
        try (FileOutputStream file = new FileOutputStream(fileName)) {
            IOUtils.copy(contents, file);
        }
        System.out.println("Created file " + fileName);
    }

    private static void showUsage() {
        System.out.println("Usage: java -jar asicverifier.jar ( --version | <configuration path> <asic container> )");
    }

    private static void showVersion() {
        System.out.println("AsicVerifier (X-Road) " + Version.XROAD_VERSION);
    }
}
