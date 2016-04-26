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
package ee.ria.xroad.common.conf.globalconf;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.operator.DigestCalculator;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestCertUtil.PKCS12;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationDirectory.PRIVATE_PARAMETERS_XML;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationDirectory.SHARED_PARAMETERS_XML;
import static ee.ria.xroad.common.conf.globalconf.PrivateParameters.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.SharedParameters.CONTENT_ID_SHARED_PARAMETERS;
import static ee.ria.xroad.common.util.CryptoUtils.createDigestCalculator;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;

/**
 * Generates test configuration directory.
 */
public final class GenerateTestData {

    private static final String ROOT = "src/test/resources/";

    private GenerateTestData() {
    }

    /**
     * Main program entry point.
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {
        // simple conf with private & shared params + additional file
        new TestConfDir("test-conf-simple")
            .addEntry(new ConfDirEntry(CONTENT_ID_PRIVATE_PARAMETERS,
                    "EE", "/" + PRIVATE_PARAMETERS_XML),
                    PRIVATE_PARAMETERS_XML)
            .addEntry(new ConfDirEntry(CONTENT_ID_SHARED_PARAMETERS,
                    "EE", "/" + SHARED_PARAMETERS_XML),
                    SHARED_PARAMETERS_XML)
            .addEntry(new ConfDirEntry("FOO",
                    "EE", "/foo.xml"),
                    "/foo.xml")
            .save();

        // detached conf scenario
        new TestConfDir("test-conf-detached")
            .addEntry(new ConfDirEntry(CONTENT_ID_PRIVATE_PARAMETERS,
                    "EE", "/" + PRIVATE_PARAMETERS_XML),
                    PRIVATE_PARAMETERS_XML)
            .save();
    }

    @RequiredArgsConstructor
    private static class TestConfDir {

        private final String name;
        private final List<ConfDirEntry> entries = new ArrayList<>();
        private boolean writeExpireDate = true;

        TestConfDir(String name, boolean writeExpireDate) {
            this(name);
            this.writeExpireDate = writeExpireDate;
        }

        TestConfDir addEntry(ConfDirEntry e, String fileName) throws Exception {
            e.setContent(getFileContent(Paths.get(ROOT, name,
                    e.getInstanceIdentifier(), fileName)));
            entries.add(e);
            return this;
        }

        void save() throws Exception {
            String parts = "";

            if (writeExpireDate) {
                parts += "--innerboundary\nExpire-date: 2016-05-20T17:42:55Z\n\n";
            }

            for (ConfDirEntry entry : entries) {
                parts += "\n" + getContentMultipart(entry);
            }

            Signature sig = Signature.getInstance("SHA512withRSA");
            sig.initSign(getSignCert().key);
            sig.update(parts.getBytes());

            String topMp = getTopMultipart(parts, encodeBase64(sig.sign()),
                    hash(getSignCert().cert.getEncoded()));
            try (FileOutputStream out =
                    new FileOutputStream(ROOT + name + ".txt")) {
                out.write(topMp.getBytes());
            }

            System.out.println(topMp);
        }
    }

    private static String getFileContent(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            return IOUtils.toString(in);
        }
    }

    @Data
    private static class ConfDirEntry {
        final String contentIdentifier;
        final String instanceIdentifier;
        final String fileName;
        String content;
    }

    private static String getContentMultipart(ConfDirEntry entry) throws Exception {
        return "--innerboundary\n"
               + "Content-type: application/octet-stream\n"
               + "Content-transfer-encoding: base64\n"
               + "Content-identifier: " + entry.getContentIdentifier()
               + "; instance=\"" + entry.getInstanceIdentifier() + "\"\n"
               + "Content-location: " + entry.getFileName() + "\n"
               + "Hash-algorithm-id: http://www.w3.org/2001/04/xmlenc#sha512\n\n"
               + hash(entry.getContent());
    }

    private static String getTopMultipart(String signedContent,
            String signatureBase64, String verificationCertHashBase64) {
        return "Content-Type: multipart/related; charset=UTF-8;boundary=envelopeboundary\n\n"
                + "--envelopeboundary\n"
                + "Content-Type: multipart/mixed; charset=UTF-8;boundary=innerboundary\n\n"
                + signedContent
                + "\n--envelopeboundary\n"
                + "Content-type: application/octet-stream\n"
                + "Content-transfer-encoding: base64\n"
                + "Signature-algorithm-id: http://www.w3.org/2001/04/xmldsig-more#rsa-sha512\n"
                + "Verification-certificate-hash: " + verificationCertHashBase64
                + "; hash-algorithm-id=\"http://www.w3.org/2001/04/xmlenc#sha512\"\n"
                + "\n" + signatureBase64 + "\n"
                + "--envelopeboundary--";

    }

    static String hash(String content) throws Exception {
        DigestCalculator dc = createDigestCalculator("SHA-512");
        IOUtils.write(content, dc.getOutputStream());
        return encodeBase64(dc.getDigest());
    }

    static String hash(byte[] content) throws Exception {
        DigestCalculator dc = createDigestCalculator("SHA-512");
        IOUtils.write(content, dc.getOutputStream());
        return encodeBase64(dc.getDigest());
    }

    static PKCS12 getSignCert() {
        return TestCertUtil.getConsumer();
    }
}
