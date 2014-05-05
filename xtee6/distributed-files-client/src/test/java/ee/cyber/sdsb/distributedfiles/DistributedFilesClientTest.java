package ee.cyber.sdsb.distributedfiles;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.util.Arrays;
import org.joda.time.DateTime;
import org.junit.Test;

import ee.cyber.sdsb.common.util.CryptoUtils;

import static ee.cyber.sdsb.common.util.CryptoUtils.decodeBase64;
import static org.junit.Assert.*;

public class DistributedFilesClientTest {

    private static final String GLOBAL_CONF = "globalconf";
    private static final String IDENTIFIER_MAPPING = "identifiermapping";

    @Test
    public void readDistributedFiles() throws Exception {
        final Map<String, Boolean> expectedParts = new HashMap<>();

        InputStream content = new FileInputStream(
                "src/test/resources/sdsb_central_distributed_files_1");

        DistributedFilesClient client = new DistributedFilesClient() {
            @Override
            boolean verifySignatureFreshness(DateTime date, DateTime atDate) {
                return true;

            }

            @Override
            void verifySignature(SignedMultipart response,
                    PublicKey verificationKey) throws Exception {
                assertEquals(response.getSignatureAlgoId(), "SHA-256");

                if (!Arrays.areEqual(response.getSignatureValue(),
                        decodeBase64("foobar"))) {
                    fail("Invalid signature value");
                }
            }

            @Override
            DistributedFileHandler getHandler(final String identifier)
                    throws Exception {
                return new DistributedFileHandler() {
                    @Override
                    public void handle(DistributedFile file)
                            throws Exception {
                        assertEquals(identifier, file.getFileName());
                        expectedParts.put(identifier, true);
                    }
                };
            }
        };

        SignedMultipart signedContent = client.fetch(content);
        client.verifySignature(signedContent, (PublicKey) null);
        client.parseContent(signedContent);

        assertNotNull("Did not receive global conf",
                expectedParts.get(GLOBAL_CONF));
        assertNotNull("Did not receive identifier mapping",
                expectedParts.get(IDENTIFIER_MAPPING));
    }

    @Test
    public void readAndVerify() throws Exception {
        final Map<String, Boolean> expectedParts = new HashMap<>();

        String verificationCertBase64 =
         "MIICnzCCAYegAwIBAgIBATANBgkqhkiG9w0BAQUFADATMREwDwYDVQQDDAhjb25zd" +
         "W1lcjAeFw0xMjEwMTUwNzAwNDdaFw0xNDEwMTUwNzAwNDdaMBMxETAPBgNVBAMMCG" +
         "NvbnN1bWVyMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmdzeQzHiFsf" +
         "os+al5eS3cvCPoR1GeY8ZzRiNHSVkwLiolU+hxe1+Y37UzJRuFA40/ZSBZazCoNlM" +
         "rCvJx/H0M6UFo59tSS3gYbz8PUdHet2YgQ0LtcjmZgaWJzsn9vWpBIuwYDNM+ZExF" +
         "niMow9vWVys+dnFP7RVlwDLbx4xniR/hix1+yaB1lWrC0i39Nm/t3pj0q7RPLRR5J" +
         "DSOBvkXGcJdEovd52s5V+FdxhtfM8LuL3XEd+pabviG7reMa+Wk3rMzn3xOBztmEK" +
         "dzzdIXfnvVM7WrYyyrDikIrwBUjpyElr7bfuUr4RT6eaWr5z5Zkz6S94/vlauU022" +
         "zEGfLwIDAQABMA0GCSqGSIb3DQEBBQUAA4IBAQBf+xo3pWhsHQRT4hbmsD0YV7rLz" +
         "XiuMzve+grs/6eKC06mwqp8m7Em1/1YO8sptyYLQHMFpZaYSwaxbvG2msvJS9Fdkw" +
         "rHyDDXyDaAtJI8xT12efXcrBmwFMdG/znJUkKMoEgsU8GDbs7/T5YD8VkmrB9Jp7g" +
         "X1KXCCIJjLmAkSbsjB+YnvKi2xiDEQA9ZZeFLgdWDrBheXUw6vzad/sHI8vOm6hcd" +
         "4jEOFVfVh1yEVgwbRFYODeHpWIxwYVa8Z2Gr/gV4Jj0f7Toca3LEcn0k4jMb2YMJV" +
         "Td9SDCnrEzR6xRGji+gzrYzePV6sU1oA88KZhMJhAQCsJ/iwoJz2D1B";

        X509Certificate verificationCert =
                CryptoUtils.readCertificate(verificationCertBase64);

        InputStream content = new FileInputStream(
                "src/test/resources/sdsb_central_distributed_files_2");

        DistributedFilesClient client = new DistributedFilesClient() {
            @Override
            boolean verifySignatureFreshness(DateTime date, DateTime atDate) {
                return true;
            }

            @Override
            DistributedFileHandler getHandler(final String identifier)
                    throws Exception {
                return new DistributedFileHandler() {
                    @Override
                    public void handle(DistributedFile file)
                            throws Exception {
                        assertEquals(identifier, file.getFileName());
                        expectedParts.put(identifier, true);
                    }
                };
            }
        };

        SignedMultipart signedContent = client.fetch(content);
        client.verifySignature(signedContent, verificationCert);
        client.parseContent(signedContent);

        assertNotNull("Did not receive global conf",
                expectedParts.get(GLOBAL_CONF));
    }

}
