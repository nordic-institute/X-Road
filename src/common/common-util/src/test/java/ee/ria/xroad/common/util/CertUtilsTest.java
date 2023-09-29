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
package ee.ria.xroad.common.util;

import org.bouncycastle.operator.OperatorCreationException;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
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
     * Test reading certificate from byte array
     * @throws CertificateException when certificate is invalid
     * @throws IOException when I/O error occurs
     */
    @Test
    public void testReadCertificate() throws CertificateException, IOException {
        File file = new File("src/test/resources/internal.crt");
        byte[] bytes = Files.readAllBytes(file.toPath());
        X509Certificate[] certificate = CertUtils.readCertificateChain(bytes);
        assertNotNull(certificate);
        assertEquals(certificate[0].getSubjectDN().getName(), "CN=ubuntu-xroad-securityserver-dev");
    }

    /**
     * Test creating pkcs12 keystore
     * @throws Exception when error occurs
     */
    @Test
    public void testCreatePkcs12() throws Exception {
        final String pkcsPath = "src/test/resources/internal.p12";
        File file = new File("src/test/resources/internal.crt");
        byte[] bytes = Files.readAllBytes(file.toPath());
        CertUtils.createPkcs12("src/test/resources/internal.key", bytes, pkcsPath);
        Path path = Paths.get(pkcsPath);
        assertTrue(Files.exists(path));
    }

    @Test
    public void testGetSubjectAlternativeNamesEmpty() throws CertificateException, IOException {
        final String certPath = "src/test/resources/cert_empty.pem";
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (FileInputStream in = new FileInputStream(certPath)) {
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
            assertEquals(null, CertUtils.getSubjectAlternativeNames(cert));
        }
    }

    @Test
    public void testGetSubjectAlternativeNamesSimple() throws CertificateException, IOException {
        final String certPath = "src/test/resources/cert_simple.pem";
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (FileInputStream in = new FileInputStream(certPath)) {
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
            assertEquals("DNS:*.example.org", CertUtils.getSubjectAlternativeNames(cert));
        }
    }

    @Test
    public void testGetSubjectAlternativeNamesMulti() throws CertificateException, IOException {
        final String certPath = "src/test/resources/cert_multi.pem";
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (FileInputStream in = new FileInputStream(certPath)) {
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
            assertEquals("email:my@example.org, URI:http://example.org/, "
                    + "DirName:CN=My Name,OU=My Unit,O=My Organization,C=UK, DNS:*.example.org, "
                    + "othername:<unsupported>, Registered ID:1.2.3.4, IP Address:192.168.7.1",
                    CertUtils.getSubjectAlternativeNames(cert));
        }
    }

    @Test
    public void testGetSubjectAlternativeNamesMultiAlternativeOrder() throws CertificateException, IOException {
        final String certPath = "src/test/resources/cert_multi2.pem";
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (FileInputStream in = new FileInputStream(certPath)) {
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
            assertEquals("DirName:CN=My Name,OU=My Unit,O=My Organization,C=UK, "
                            + "URI:http://example.org/, othername:<unsupported>, Registered ID:1.2.3.4,"
                            + " IP Address:192.168.7.1, email:my@example.org, DNS:*.example.org",
                    CertUtils.getSubjectAlternativeNames(cert));
        }
    }
}
