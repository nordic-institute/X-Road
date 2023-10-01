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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestCertUtil.PKCS12;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.operator.DigestCalculator;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS;
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
        new TestConfDir("test-conf-simple").addEntry(new ConfDirEntry(CONTENT_ID_PRIVATE_PARAMETERS,
                        "EE", "/" + FILE_NAME_PRIVATE_PARAMETERS), FILE_NAME_PRIVATE_PARAMETERS)
            .addEntry(new ConfDirEntry(CONTENT_ID_SHARED_PARAMETERS, "EE", "/"
                            + FILE_NAME_SHARED_PARAMETERS), FILE_NAME_SHARED_PARAMETERS)
            .addEntry(new ConfDirEntry("FOO", "EE", "/foo.xml"), "/foo.xml")
                .save();

        // detached conf scenario
        new TestConfDir("test-conf-detached").addEntry(new ConfDirEntry(CONTENT_ID_PRIVATE_PARAMETERS,
                        "EE", "/" + FILE_NAME_PRIVATE_PARAMETERS), FILE_NAME_PRIVATE_PARAMETERS).save();
    }

    @RequiredArgsConstructor
    private static class TestConfDir {

        private final String name;
        private final List<ConfDirEntry> entries = new ArrayList<>();
        private boolean writeExpireDate = true;

        TestConfDir addEntry(ConfDirEntry e, String fileName) throws Exception {
            e.setContent(getFileContent(Paths.get(ROOT, name, e.getInstanceIdentifier(), fileName)));
            entries.add(e);

            return this;
        }

        void save() throws Exception {
            StringBuffer parts = new StringBuffer("");

            if (writeExpireDate) {
                parts.append("--innerboundary\nExpire-date: 2026-05-20T17:42:55Z\n\n");
            }

            for (ConfDirEntry entry : entries) {
                parts.append("\n" + getContentMultipart(entry));
            }

            Signature sig = Signature.getInstance(CryptoUtils.SHA512WITHRSA_ID);
            sig.initSign(getSignCert().key);
            sig.update(parts.toString().getBytes());

            String topMp = getTopMultipart(parts.toString(), encodeBase64(sig.sign()),
                    hash(getSignCert().certChain[0].getEncoded()));

            try (FileOutputStream out = new FileOutputStream(ROOT + name + ".txt")) {
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

    private static String getTopMultipart(String signedContent, String signatureBase64,
            String verificationCertHashBase64) {
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
