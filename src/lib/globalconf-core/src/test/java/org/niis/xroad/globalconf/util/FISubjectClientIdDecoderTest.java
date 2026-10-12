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
package org.niis.xroad.globalconf.util;

import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;

import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import static ee.ria.xroad.common.DerSubjectCertUtil.derName;
import static ee.ria.xroad.common.DerSubjectCertUtil.selfSignedCertificate;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link FISubjectClientIdDecoder}
 */
public class FISubjectClientIdDecoderTest {

    private static KeyPair keyPair;

    /**
     * Setup tests
     *
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
     *
     * @throws GeneralSecurityException  when security exception occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test
    public void shouldDecodeClientId() throws GeneralSecurityException, OperatorCreationException {
        X509Certificate cert = generateSelfSignedCertificate(
                "C=FI, O=ACME, CN=1234567-8, serialNumber=FI-TEST/serverCode/PUB", keyPair);
        ClientId clientId = FISubjectClientIdDecoder.getSubjectClientId(cert);

        assertEquals(ClientId.Conf.create("FI-TEST", "PUB", "1234567-8"), clientId);

        cert = generateSelfSignedCertificate("C=FI, O=ACME, CN=1234567-8, serialNumber=FI-TEST/serverCode/PUB",
                keyPair);
        clientId = FISubjectClientIdDecoder.getSubjectClientId(cert);

        assertEquals(ClientId.Conf.create("FI-TEST", "PUB", "1234567-8"), clientId);
    }

    /**
     * Test that decoder fails when empty components are found
     *
     * @throws GeneralSecurityException  when security exception occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test(expected = XrdRuntimeException.class)
    public void shouldFailIfEmptyComponents() throws GeneralSecurityException, OperatorCreationException {
        final X509Certificate cert = generateSelfSignedCertificate("C=FI, O=ACME, CN=1234567-8, serialNumber=///",
                keyPair);
        FISubjectClientIdDecoder.getSubjectClientId(cert);
    }

    /**
     * Test that decoder fails if there are too many components
     *
     * @throws GeneralSecurityException  when security exception occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test(expected = XrdRuntimeException.class)
    public void shouldFailIfTooManyComponents() throws GeneralSecurityException,
                                                       OperatorCreationException {
        final X509Certificate cert = generateSelfSignedCertificate("C=FI, O=ACME, CN=1234567-8, serialNumber=1/2/3/4",
                keyPair);
        FISubjectClientIdDecoder.getSubjectClientId(cert);
    }

    /**
     * Test that decoder fails if country code is wrong
     *
     * @throws GeneralSecurityException  when security exception occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test(expected = XrdRuntimeException.class)
    public void shouldFailIfCountryDoesNotMatch() throws GeneralSecurityException,
                                                         OperatorCreationException {
        final X509Certificate cert = generateSelfSignedCertificate(
                "C=XX, O=ACME, CN=1234567-8, serialNumber=FI-TEST/serverCode/PUB", keyPair);
        FISubjectClientIdDecoder.getSubjectClientId(cert);
    }

    /**
     * Test that decoder fails if organization is missing
     *
     * @throws GeneralSecurityException  when security exception occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test(expected = XrdRuntimeException.class)
    public void shouldFailIfOrgMissing() throws GeneralSecurityException, OperatorCreationException {
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
     *
     * @throws GeneralSecurityException  when security exception occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test
    public void shouldDecodeClientIdLegacy() throws GeneralSecurityException, OperatorCreationException {
        final X509Certificate cert = generateSelfSignedCertificate("C=FI, O=FI-TEST, OU=PUB, CN=1234567-8", keyPair);
        ClientId clientId = FISubjectClientIdDecoder.getSubjectClientId(cert);

        assertEquals(ClientId.Conf.create("FI-TEST", "PUB", "1234567-8"), clientId);
    }

    /**
     * Test that decoder fails if country code does not match
     *
     * @throws GeneralSecurityException  when security exception occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test(expected = XrdRuntimeException.class)
    public void shouldFailIfCountryDoesNotMatchLegacy() throws GeneralSecurityException,
                                                               OperatorCreationException {
        final X509Certificate cert = generateSelfSignedCertificate("C=XX, O=FI-TEST, OU=PUB, CN=1234567-8", keyPair);
        FISubjectClientIdDecoder.getSubjectClientId(cert);
    }

    private X509Certificate generateSelfSignedCertificate(String dn, KeyPair pair) throws OperatorCreationException,
                                                                                          CertificateException {
        return generateSelfSignedCertificate(new X500Name(dn), pair);
    }

    private X509Certificate generateSelfSignedCertificate(X500Name name, KeyPair pair) throws OperatorCreationException,
                                                                                              CertificateException {
        ContentSigner signer = new JcaContentSignerBuilder(SignAlgorithm.SHA256_WITH_RSA.name()).build(pair.getPrivate());
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, BigInteger.ONE, new Date(),
                new Date(), name, pair.getPublic()
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
     *
     * @throws GeneralSecurityException  when security exception occurs
     * @throws OperatorCreationException when operator creation fails
     */
    @Test
    public void shouldDecodeClientIdStoneAge() throws GeneralSecurityException, OperatorCreationException {
        final X500Name stoneAgeSubject = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, new DERPrintableString("FI-DEV"))
                .addRDN(BCStyle.O, "GOV")
                .addRDN(BCStyle.CN, "0245437-2")
                .build();
        final X509Certificate cert = generateSelfSignedCertificate(stoneAgeSubject, keyPair);
        ClientId clientId = FISubjectClientIdDecoder.getSubjectClientId(cert);

        assertEquals(ClientId.Conf.create("FI-DEV", "GOV", "0245437-2"), clientId);
    }

    @Test
    public void shouldUseMostSpecificValuesOfRepeatedAttributesInLegacyName() {
        var cert = selfSignedCertificate(derName(
                "C", "FI", "O", "DEV", "OU", "OUTER", "OU", "COM", "CN", "outer", "CN", "222"));

        assertEquals(ClientId.Conf.create("DEV", "COM", "222"), FISubjectClientIdDecoder.getSubjectClientId(cert));
    }

    @Test
    public void shouldUseMostSpecificCommonNameOfRepeatedAttributeWithSerialNumber() {
        var cert = selfSignedCertificate(derName(
                "C", "FI", "O", "Org", "CN", "outer", "CN", "222", "serialNumber", "DEV/server/COM"));

        assertEquals(ClientId.Conf.create("DEV", "COM", "222"), FISubjectClientIdDecoder.getSubjectClientId(cert));
    }

    @Test
    public void shouldUseMostSpecificValuesOfRepeatedAttributesWithoutOrganizationUnit() {
        var cert = selfSignedCertificate(derName("C", "FI", "O", "outer", "O", "DEV", "CN", "outer", "CN", "222"));

        assertEquals(ClientId.Conf.create("FI", "DEV", "222"), FISubjectClientIdDecoder.getSubjectClientId(cert));
    }
}
