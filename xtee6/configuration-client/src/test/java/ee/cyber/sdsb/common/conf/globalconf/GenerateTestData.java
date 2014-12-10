package ee.cyber.sdsb.common.conf.globalconf;

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

import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.TestCertUtil.PKCS12;

import static ee.cyber.sdsb.common.conf.globalconf.ConfigurationDirectory.PRIVATE_PARAMETERS_XML;
import static ee.cyber.sdsb.common.conf.globalconf.ConfigurationDirectory.SHARED_PARAMETERS_XML;
import static ee.cyber.sdsb.common.conf.globalconf.PrivateParameters.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.cyber.sdsb.common.conf.globalconf.SharedParameters.CONTENT_ID_SHARED_PARAMETERS;
import static ee.cyber.sdsb.common.util.CryptoUtils.createDigestCalculator;
import static ee.cyber.sdsb.common.util.CryptoUtils.encodeBase64;

/**
 * Generates test configuration directory.
 */
public class GenerateTestData {

    private static final String ROOT = "src/test/resources/";

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

        public TestConfDir(String name, boolean writeExpireDate) {
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
               + "Content-identifier: " + entry.getContentIdentifier() + "; instance=\"" + entry.getInstanceIdentifier() + "\"\n"
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
                + "Verification-certificate-hash: " + verificationCertHashBase64 + "; hash-algorithm-id=\"http://www.w3.org/2001/04/xmlenc#sha512\"\n"
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
