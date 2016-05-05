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
package ee.ria.xroad.common.util;

import org.bouncycastle.operator.OperatorCreationException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link CertUtils}
 */
public class CertUtilsTest {

    /**
     * Setup test
     * @throws IOException when error occurs
     */
    @Before
    public void before() throws IOException {
        final String pkcsPath = "src/test/resources/internal.p12";
        Path path = Paths.get(pkcsPath);
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

    /**
     * Test generate certificate request
     * @throws NoSuchAlgorithmException when algorithm is not available
     * @throws OperatorCreationException when operator cannot be created
     * @throws IOException when I/O error occurs
     */
    @Test
    public void testGenerateCertRequest() throws NoSuchAlgorithmException, OperatorCreationException, IOException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        PrivateKey privateKey = pair.getPrivate();
        PublicKey publicKey = pair.getPublic();
        byte[] certRequest = CertUtils.generateCertRequest(privateKey, publicKey, "C=FI, CN=foo.bar.com");
        assertTrue(certRequest != null && certRequest.length > 0);
    }

    /**
     * Test reading a keypair from file
     * @throws NoSuchAlgorithmException when algorithm is not available
     * @throws IOException when I/O error occurs
     * @throws InvalidKeySpecException when keypair is invalid
     */
    @Test
    public void testReadKeyPair() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        KeyPair keyPair = CertUtils.readKeyPairFromPemFile("src/test/resources/internal.key");
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
    }

    /**
     * Test reading certificate from file
     * @throws CertificateException when certificate is invalid
     * @throws IOException when I/O error occurs
     */
    @Test
    public void testReadCertificate() throws CertificateException, IOException {
        X509Certificate certificate = CertUtils.readCertificate("src/test/resources/internal.crt");
        assertNotNull(certificate);
        assertEquals(certificate.getSubjectDN().getName(), "CN=ubuntu-xroad-securityserver-dev");
    }

    /**
     * Test creating pkcs12 keystore
     * @throws Exception when error occurs
     */
    @Test
    public void testCreatePkcs12() throws Exception {
        final String pkcsPath = "src/test/resources/internal.p12";
        CertUtils.createPkcs12("src/test/resources/internal.key", "src/test/resources/internal.crt", pkcsPath);
        Path path = Paths.get(pkcsPath);
        assertTrue(Files.exists(path));
    }
}
