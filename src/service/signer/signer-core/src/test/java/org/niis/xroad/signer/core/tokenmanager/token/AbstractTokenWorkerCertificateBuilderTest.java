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
package org.niis.xroad.signer.core.tokenmanager.token;

import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.DerSubjectCertUtil.derName;
import static ee.ria.xroad.common.DerSubjectCertUtil.selfSignedCertificate;
import static org.assertj.core.api.Assertions.assertThat;

class AbstractTokenWorkerCertificateBuilderTest {

    private static final String DER_COUNTRY_DEV_ORG_COM_CN_222 =
            "302a310c300a06035504061303444556310c300a060355040a0c03434f4d310c300a06035504030c03323232";

    @Test
    void subjectWithCountryThatIsNotTwoCharactersAndIssuerWithLongCommonNameKeepDerEncoding() throws Exception {
        String longName = "x".repeat(65);
        X509Certificate issuer = selfSignedCertificate(derName("C", "DEV", "O", "COM", "CN", longName));
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        var keyPair = generator.generateKeyPair();
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate());

        JcaX509v3CertificateBuilder builder = AbstractTokenWorker.getCertificateBuilder(
                "C=DEV, O=COM, CN=222", keyPair.getPublic(), issuer);
        var certificate = builder.build(signer);

        assertThat(certificate.getSubject().getEncoded())
                .isEqualTo(Hex.decode(DER_COUNTRY_DEV_ORG_COM_CN_222));
        assertThat(certificate.getIssuer().getEncoded())
                .isEqualTo(derName("CN", longName, "O", "COM", "C", "DEV").getEncoded());
    }
}
