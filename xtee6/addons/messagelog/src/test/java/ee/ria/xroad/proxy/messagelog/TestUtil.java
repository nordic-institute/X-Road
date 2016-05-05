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
package ee.ria.xroad.proxy.messagelog;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.hibernate.Query;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CryptoUtils;

import static ee.ria.xroad.proxy.messagelog.MessageLogDatabaseCtx.doInTransaction;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Slf4j
final class TestUtil {
    private TestUtil() {
    }

    static final String TSP_CERT =
            "MIICwjCCAaqgAwIBAgIIb+RPNmkfCdYwDQYJKoZIhvcNAQEFBQAwNzERMA8G"
            + "A1UEAwwIQWRtaW5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UE"
            + "BhMCU0UwHhcNMTIxMTI5MTE1MzA2WhcNMTQxMTI5MTE1MzA2WjAVMRMwEQYD"
            + "VQQDDAp0aW1lc3RhbXAxMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCb"
            + "55NVDtHzs91sflX3fatZWUS69rxkxDMpcGo6doJ7YaKrCMr3BZ3ZlDTfCdEo"
            + "sWocTcYXdm3CO8BXlZvhkvKyHN/hr0UzD0T8j8mBYoq3fGjTVTJOIG2yTsyT"
            + "/3z3dpcMyGMWwsiqOd9TTtI8DcR2cOvQzlLiV9hz/kB9iLJeSQIDAQABo3gw"
            + "djAdBgNVHQ4EFgQUbdmtvKHCe0+vhKP+ZcVUjmf5w/AwDAYDVR0TAQH/BAIw"
            + "ADAfBgNVHSMEGDAWgBR3LYkuA7b9+NJlOTE1ItBGGujSCTAOBgNVHQ8BAf8E"
            + "BAMCBkAwFgYDVR0lAQH/BAwwCgYIKwYBBQUHAwgwDQYJKoZIhvcNAQEFBQAD"
            + "ggEBAFJ3AJ4I4RTeMBWhN8RLPQdJzcd0VRp9FUyYhnIkR679nXU+ZbIyaQNx"
            + "3+hPIbhcOMKxlKGm0LcDnjHL4EuJ6Gb027vF7mSwFbcKPM+L23x2QLvuVcUE"
            + "jcbP3Kcm93XCSu3RI71JINM+WinjXke/COuFzhMWJcLYj7S5dGR53ya0NnSf"
            + "7dlua5FLBRiOFA5kRWTft6RcEW0jGZzscL6wZn+hH99IihjqgdxV1GydL+Bg"
            + "DMfryZzhl+h1WtTwv0Bi5Gs81v8UlNUTnCCfLu9fatHx85/ttFcXEyt9SQze"
            + "3NGcaR1i3kyZvNijzG3C+jrUnJ/lFs5AcIiPG0Emz6oZEYs=";

    static String message;
    static String signature;

    static GlobalConfProvider getGlobalConf() {
        return new EmptyGlobalConf() {
            @Override
            public List<X509Certificate> getTspCertificates()
                    throws CertificateException {
                try {
                    return Arrays.asList(CryptoUtils.readCertificate(TSP_CERT));
                } catch (IOException e) {
                    throw new CertificateException(e);
                }
            }
        };
    }

    static ServerConfProvider getServerConf() {
        return new EmptyServerConf() {
            @Override
            public List<String> getTspUrl() {
                return Arrays.asList("http://iks2-ubuntu.cyber.ee:8080/"
                        + "signserver/tsa?workerName=TimeStampSigner");
            }
        };
    }

    static void initForTest() {
        System.setProperty(
                SystemProperties.DATABASE_PROPERTIES,
                "src/test/resources/hibernate.properties");

        ServerConf.reload(getServerConf());
        GlobalConf.reload(getGlobalConf());
    }

    static void cleanUpDatabase() throws Exception {
        MessageLogDatabaseCtx.doInTransaction(session -> {
            Query q = session.createSQLQuery(
                    // Since we are using HSQLDB for tests, we can use
                    // special commands to completely wipe out the database
                    "TRUNCATE SCHEMA public AND COMMIT");
            q.executeUpdate();
            return null;
        });
    }

    static SoapMessageImpl createMessage() throws Exception {
        return createMessage("123456789");
    }

    static SoapMessageImpl createMessage(String queryId) throws Exception {
        if (message == null) {
            try (InputStream in =
                    new FileInputStream("src/test/resources/simple.query")) {
                message = IOUtils.toString(in);
            }
        }

        String soap = message.replaceAll("<xroad:id>1234567890</xroad:id>",
                "<xroad:id>" + queryId + "</xroad:id>");
        return (SoapMessageImpl) new SoapParserImpl().parse(
                new ByteArrayInputStream(
                        soap.getBytes(StandardCharsets.UTF_8)));
    }

    static SignatureData createSignature() throws Exception {
        if (signature == null) {
            try (InputStream in =
                    new FileInputStream("src/test/resources/signature.xml")) {
                signature = IOUtils.toString(in);
            }
        }

        return new SignatureData(signature, null, null);
    }

    @SuppressWarnings("unchecked")
    static List<Task> getTaskQueue() throws Exception {
        return doInTransaction(session -> session.createQuery(
                TaskQueue.getTaskQueueQuery()).list());
    }

    static void assertTaskQueueSize(int expectedSize) throws Exception {
        List<Task> taskQueue = getTaskQueue();
        assertNotNull(taskQueue);
        assertEquals(expectedSize, taskQueue.size());
    }

    static ShellCommandOutput runShellCommand(String command) {
        if (isBlank(command)) {
            return null;
        }

        log.info("Executing shell command: \t{}",
                command);

        try {
            Process process =
                    new ProcessBuilder(command.split("\\s+")).start();

            StandardErrorCollector standardErrorReader =
                    new StandardErrorCollector(process);

            StandardOutputReader standardOutputReader =
                    new StandardOutputReader(process);

            standardOutputReader.start();
            standardErrorReader.start();

            standardOutputReader.join();
            standardErrorReader.join();
            process.waitFor();

            int exitCode = process.exitValue();

            return new ShellCommandOutput(
                    exitCode,
                    standardOutputReader.getStandardOutput(),
                    standardErrorReader.getStandardError());
        } catch (Exception e) {
            log.error(
                    "Failed to execute archive transfer command '{}'",
                    command);
            throw new RuntimeException(e);
        }
    }

    @RequiredArgsConstructor
    private static class StandardOutputReader extends Thread {
        private final Process process;

        @Getter
        private String standardOutput;

        @Override
        public void run() {
            try (InputStream input = process.getInputStream()) {
                standardOutput = IOUtils.toString(input, StandardCharsets.UTF_8);
            } catch (IOException e) {
                // We can ignore it.
                log.error("Could not read standard output", e);
            }
        }
    }

    @RequiredArgsConstructor
    private static class StandardErrorCollector extends Thread {
        private final Process process;

        @Getter
        private String standardError;

        @Override
        public void run() {
            try (InputStream error = process.getErrorStream()) {
                standardError = IOUtils.toString(error, StandardCharsets.UTF_8);
            } catch (IOException e) {
                // We can ignore it.
                log.error("Could not read standard error", e);
            }
        }
    }
}
