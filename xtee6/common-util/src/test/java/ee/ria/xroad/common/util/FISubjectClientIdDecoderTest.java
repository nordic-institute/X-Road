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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link FISubjectClientIdDecoder}
 */
public class FISubjectClientIdDecoderTest {

    private static KeyPair keyPair;

    /**
     * Setup tests
     * @throws NoSuchAlgorithmException when algorithm is not available
     */
    @BeforeClass
    public static void init() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    /**
     * Test decoding client id
     * @throws GeneralSecurityException when security exception occurs
     * @throws IOException when I/O error occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test
    public void shouldDecodeClientId() throws GeneralSecurityException, IOException, OperatorCreationException {
        X509Certificate cert = generateSelfSignedCertificate(
                "C=FI, O=ACME, CN=1234567-8, serialNumber=FI-TEST/serverCode/PUB", keyPair);
        ClientId clientId = FISubjectClientIdDecoder.getSubjectClientId(cert);
        assertEquals(ClientId.create("FI-TEST", "PUB", "1234567-8"), clientId);

        cert = generateSelfSignedCertificate("C=FI, O=ACME, CN=1234567-8, "
                + "serialNumber=FI-TEST/serverCode/PUB", keyPair);
        clientId = FISubjectClientIdDecoder.getSubjectClientId(cert);
        assertEquals(ClientId.create("FI-TEST", "PUB", "1234567-8"), clientId);
    }

    /**
     * Test that decoder fails when empty components are found
     * @throws GeneralSecurityException when security exception occurs
     * @throws IOException when I/O error occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test(expected = CodedException.class)
    public void shouldFailIfEmptyComponents() throws GeneralSecurityException, IOException, OperatorCreationException {
        final X509Certificate cert = generateSelfSignedCertificate(
                "C=FI, O=ACME, CN=1234567-8, serialNumber=///", keyPair);
        FISubjectClientIdDecoder.getSubjectClientId(cert);
    }

    /**
     * Test that decoder fails if there are too many components
     * @throws GeneralSecurityException when security exception occurs
     * @throws IOException when I/O error occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test(expected = CodedException.class)
    public void shouldFailIfTooManyComponents() throws GeneralSecurityException, IOException,
            OperatorCreationException {
        final X509Certificate cert = generateSelfSignedCertificate(
                "C=FI, O=ACME, CN=1234567-8, serialNumber=1/2/3/4", keyPair);
        FISubjectClientIdDecoder.getSubjectClientId(cert);
    }

    /**
     * Test that decoder fails if country code is wrong
     * @throws GeneralSecurityException when security exception occurs
     * @throws IOException when I/O error occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test(expected = CodedException.class)
    public void shouldFailIfCountryDoesNotMatch() throws GeneralSecurityException, IOException,
            OperatorCreationException {
        final X509Certificate cert = generateSelfSignedCertificate(
                "C=XX, O=ACME, CN=1234567-8, serialNumber=FI-TEST/serverCode/PUB", keyPair);
        FISubjectClientIdDecoder.getSubjectClientId(cert);
    }

    /**
     * Test that decoder fails if organization is missing
     * @throws GeneralSecurityException when security exception occurs
     * @throws IOException when I/O error occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test(expected = CodedException.class)
    public void shouldFailIfOrgMissing() throws GeneralSecurityException, IOException, OperatorCreationException {
        final X509Certificate cert = generateSelfSignedCertificate(
                "C=FI, CN=1234567-8, serialNumber=FI-TEST/serverCode/PUB", keyPair);
        FISubjectClientIdDecoder.getSubjectClientId(cert);
    }

    /*
     *
     * Tests for legacy format
     *
     */

    /**
     * Test that legacy format decoding succeeds
     * @throws GeneralSecurityException when security exception occurs
     * @throws IOException when I/O error occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test
    public void shouldDecodeClientIdLegacy() throws GeneralSecurityException, IOException, OperatorCreationException {
        final X509Certificate cert = generateSelfSignedCertificate("C=FI, O=FI-TEST, OU=PUB, CN=1234567-8", keyPair);
        ClientId clientId = FISubjectClientIdDecoder.getSubjectClientId(cert);
        assertEquals(ClientId.create("FI-TEST", "PUB", "1234567-8"), clientId);
    }


    /**
     * Test that decoder fails if country code does not match
     * @throws GeneralSecurityException when security exception occurs
     * @throws IOException when I/O error occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test(expected = CodedException.class)
    public void shouldFailIfCountryDoesNotMatchLegacy() throws GeneralSecurityException, IOException,
            OperatorCreationException {
        final X509Certificate cert = generateSelfSignedCertificate("C=XX, O=FI-TEST, OU=PUB, CN=1234567-8", keyPair);
        FISubjectClientIdDecoder.getSubjectClientId(cert);
    }


    private X509Certificate generateSelfSignedCertificate(String dn, KeyPair pair) throws OperatorCreationException,
            CertificateException {
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
        X500Name name = new X500Name(dn);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name,
                BigInteger.ONE,
                new Date(),
                new Date(),
                name,
                pair.getPublic()
        );
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    /*
    *
    * Tests for stone age
    *
     */

    /**
     * Test that FI-DEV stone age decoding succeeds
     * @throws GeneralSecurityException when security exception occurs
     * @throws IOException when I/O error occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test
    public void shouldDecodeClientIdStoneAge() throws GeneralSecurityException, IOException, OperatorCreationException {
        final X509Certificate cert = generateSelfSignedCertificate("C=FI-DEV, O=GOV, CN=0245437-2", keyPair);
        ClientId clientId = FISubjectClientIdDecoder.getSubjectClientId(cert);
        assertEquals(ClientId.create("FI-DEV", "GOV", "0245437-2"), clientId);
    }
}
