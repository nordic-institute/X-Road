/*
 * The MIT License
 *
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

package org.niis.xroad.cs.admin.core.entity.mapper;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.domain.ApprovedTsa;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.KeyUsageConverter;
import org.niis.xroad.cs.admin.core.entity.ApprovedTsaEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Set;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.cs.admin.api.domain.ApprovedTsa.ApprovedTsaCost.UNDEFINED;
import static org.niis.xroad.cs.admin.api.dto.KeyUsageEnum.NON_REPUDIATION;

@SpringBootTest(classes = {ApprovedTsaMapperImpl.class, CertificateConverter.class, KeyUsageConverter.class})
class ApprovedTsaMapperTest {

    private static final int ID = 123;
    private static final String URL = "http://test.url";
    private static final String NAME = "name";
    private static final Instant VALID_FROM = TimeUtils.now().minus(1, DAYS);
    private static final Instant VALID_TO = TimeUtils.now().plus(2, DAYS);
    private static final X509Certificate CERTIFICATE = TestCertUtil.getTspCert();

    private static final Instant TEST_TSA_CERT_VALID_FROM = Instant.ofEpochMilli(1354189986000L); //2012-11-29 11:53:06Z
    private static final Instant TEST_TSA_CERT_VALID_TO = Instant.ofEpochMilli(1417261986000L); //2014-11-29 11:53:06Z

    @Autowired
    private ApprovedTsaMapper approvedTsaMapper;

    @Test
    void toTarget() throws Exception {
        final ApprovedTsa result = approvedTsaMapper.toTarget(approvedTsaEntity());

        assertThat(result.getId()).isEqualTo(ID);
        assertThat(result.getName()).isEqualTo(NAME);
        assertThat(result.getUrl()).isEqualTo(URL);
        assertThat(result.getValidFrom()).isEqualTo(VALID_FROM);
        assertThat(result.getValidTo()).isEqualTo(VALID_TO);

        assertThat(result.getCertificate().getHash()).isEqualTo("094D62D75ECC25D6BD9EA83C7B34678016BB72BB80118FF6EC7E4D383A678CD1");
        assertThat(result.getCertificate().getIssuerCommonName()).isEqualTo("AdminCA1");
        assertThat(result.getCertificate().getIssuerDistinguishedName()).isEqualTo("C=SE, O=EJBCA Sample, CN=AdminCA1");
        assertThat(result.getCertificate().getKeyUsages()).isEqualTo(Set.of(NON_REPUDIATION));
        assertThat(result.getCertificate().getNotAfter()).isEqualTo(TEST_TSA_CERT_VALID_TO);
        assertThat(result.getCertificate().getNotBefore()).isEqualTo(TEST_TSA_CERT_VALID_FROM);
        assertThat(result.getCertificate().getPublicKeyAlgorithm()).isEqualTo("RSA");
        assertThat(result.getCertificate().getRsaPublicKeyExponent()).isEqualTo(new BigInteger("65537"));
        assertThat(result.getCertificate().getRsaPublicKeyModulus()).isEqualTo("9be793550ed1f3b3dd6c7e55f77"
                + "dab595944baf6bc64c43329706a3a76827b61a2ab08caf7059dd99434df09d128b16a1c4dc617766dc23bc057959be19"
                + "2f2b21cdfe1af45330f44fc8fc981628ab77c68d355324e206db24ecc93ff7cf776970cc86316c2c8aa39df534ed23c0"
                + "dc47670ebd0ce52e257d873fe407d88b25e49");
        assertThat(result.getCertificate().getSerial()).isEqualTo("8062656328010500566");
        assertThat(result.getCertificate().getSignature()).isEqualTo("5277009e08e114de3015a137c44b3d0"
                + "749cdc774551a7d154c9886722447aefd9d753e65b232690371dfe84f21b85c38c2b194a1a6d0b7039e31cbe04"
                + "b89e866f4dbbbc5ee64b015b70a3ccf8bdb7c7640bbee55c5048dc6cfdca726f775c24aedd123bd4920d33e5a2"
                + "9e35e47bf08eb85ce131625c2d88fb4b9746479df26b436749fedd96e6b914b05188e140e644564dfb7a45c116"
                + "d23199cec70beb0667fa11fdf488a18ea81dc55d46c9d2fe0600cc7ebc99ce197e8755ad4f0bf4062e46b3cd6f"
                + "f1494d5139c209f2eef5f6ad1f1f39fedb45717132b7d490cdedcd19c691d62de4c99bcd8a3cc6dc2fa3ad49c9"
                + "fe516ce4070888f1b4126cfaa19118b");
        assertThat(result.getCertificate().getSignatureAlgorithm()).isEqualTo("SHA1withRSA");
        assertThat(result.getCertificate().getSubjectAlternativeNames()).isNull();
        assertThat(result.getCertificate().getSubjectCommonName()).isEqualTo("timestamp1");
        assertThat(result.getCertificate().getSubjectDistinguishedName()).isEqualTo("CN=timestamp1");
        assertThat(result.getCertificate().getVersion()).isEqualTo(3);

        // stub values. Will be implemented in separate story
        assertThat(result.getCost()).isEqualTo(UNDEFINED);
        assertThat(result.getTimestampingInterval()).isEqualTo(60);
    }

    @Test
    void toEntity() throws Exception {
        final ApprovedTsaEntity result = approvedTsaMapper.toEntity(URL, CERTIFICATE.getEncoded());

        assertThat(result.getName()).isEqualTo("timestamp1");
        assertThat(result.getUrl()).isEqualTo(URL);
        assertThat(result.getCert()).isEqualTo(CERTIFICATE.getEncoded());
        assertThat(result.getValidFrom()).isEqualTo(TEST_TSA_CERT_VALID_FROM);
        assertThat(result.getValidTo()).isEqualTo(TEST_TSA_CERT_VALID_TO);
    }

    private ApprovedTsaEntity approvedTsaEntity() throws Exception {
        final ApprovedTsaEntity entity = new ApprovedTsaEntity();

        ReflectionTestUtils.setField(entity, "id", ID);
        entity.setUrl(URL);
        entity.setName(NAME);
        entity.setCert(CERTIFICATE.getEncoded());
        entity.setValidFrom(VALID_FROM);
        entity.setValidTo(VALID_TO);

        return entity;
    }

}
