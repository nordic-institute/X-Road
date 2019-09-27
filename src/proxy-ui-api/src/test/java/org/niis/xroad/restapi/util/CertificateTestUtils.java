/**
 * The MIT License
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
package org.niis.xroad.restapi.util;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utils for working with test x509 certificates
 */
public final class CertificateTestUtils {

    // this is base64 encoded DER certificate from common-util/test/configuration-anchor.xml
    /**
     * Version: V3
     * Subject: CN=N/A
     * Signature Algorithm: SHA512withRSA, OID = 1.2.840.113549.1.1.13
     *
     * Key:  Sun RSA public key, 2048 bits
     * public exponent: 65537
     * Validity: [From: Thu Jan 01 02:00:00 EET 1970,
     * To: Fri Jan 01 02:00:00 EET 2038]
     * Issuer: CN=N/A
     * SerialNumber: [    01]
     */
    private static final byte[] MOCK_CERTIFICATE_BYTES =
            CryptoUtils.decodeBase64(
                    "MIICqTCCAZGgAwIBAgIBATANBgkqhkiG9w0BAQ0FADAOMQwwCgYDVQQDDANOL0EwHhcNN"
                            + "zAwMTAxMDAwMDAwWhcNMzgwMTAxMDAwMDAwWjAOMQwwCgYDVQQDDANOL0EwggEiMA0GC"
                            + "SqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCdiI++CJsyo19Y0810Q80lOJmJ264CvGGqQ"
                            + "uB9VYha4YFsHUhltAp3LIcEpxNPuh8k7Mn+pFoetIXtBh6p5cYGf3nS0i07xSLaAAkQd"
                            + "GqzI6aiSNiGDhQGL5NdyM/cdthtdheQq3WquN7kNkmXo1c5RM2ZcK4SRy6Q44d+KdzC5"
                            + "O42mUgDdxyY2+3xpSqcAJq1/2DuDPVzAIkWH/iU2+dgnaPACcNqCgnL8g0ALu2e9vHm/"
                            + "ZYhYpS3+e2xLXEOwRvxlprsGcE1aIjKeFupwoZ4nnkqmHOA2AYS4wVVpcrmF0lDmemXA"
                            + "fi0gDqWCkyjqo9aWdo952uHVQpJarMBGothAgMBAAGjEjAQMA4GA1UdDwEB/wQEAwIGQ"
                            + "DANBgkqhkiG9w0BAQ0FAAOCAQEAMUt6UKCam3QyJnGeEMDJ0m8WbjSzD5NyUVbpR2EVr"
                            + "O+Kqbu8Kd/vjF8vdQN+TCNabqTynnrrmqkc4xBBIXHMJ+xS6SijHQ5+IJ6D/VSx+C3D6"
                            + "XrJbzCby4t+ESqGsqB6ShxiiKOSQ5A6MDaE4Doi00GMB5NymknQrnwREOMPwTZy68CZE"
                            + "aEQyE4M9KezCeVJMCXmnJt1I9oudsw3xPDjq+aYzRORW74RvNFf+sztBjPGhkqFnkl+g"
                            + "lbEK6otefyJPn5vVwjz/+ywyqzx8YJM0vPkD/PghmJxunsJObbvif9FNZaxOaEzI9QDw"
                            + "0nWzbgvsCAqdcHqRjMEQwtU75fzfg==");

    public static final String WIDGITS_CERTIFICATE_HASH = "63A104B2BAC14667873C5DBD54BE25BC687B3702";

    /**
     * Version: V3
     * Subject: O=Internet Widgits Pty Ltd, ST=Some-State, C=AU
     * Signature Algorithm: SHA256withRSA, OID = 1.2.840.113549.1.1.11
     *
     * Key:  Sun RSA public key, 512 bits
     * public exponent: 65537
     * Validity: [From: Wed Apr 24 09:59:02 EEST 2019,
     * To: Thu Apr 23 09:59:02 EEST 2020]
     * Issuer: O=Internet Widgits Pty Ltd, ST=Some-State, C=AU
     * SerialNumber: [    cfa421d2 f88c8eb5]
     */
    private static final byte[] WIDGITS_CERTIFICATE_BYTES =
            CryptoUtils.decodeBase64(
                    "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUIwekNDQVgyZ0F3SUJBZ0lKQU0ra0lkTDRqSTYx"
                            + "TUEwR0NTcUdTSWIzRFFFQkN3VUFNRVV4Q3pBSkJnTlYKQkFZVEFrRlZNUk13RVFZRFZRUUlE"
                            + "QXBUYjIxbExWTjBZWFJsTVNFd0h3WURWUVFLREJoSmJuUmxjbTVsZENCWAphV1JuYVhSeklG"
                            + "QjBlU0JNZEdRd0hoY05NVGt3TkRJME1EWTFPVEF5V2hjTk1qQXdOREl6TURZMU9UQXlXakJG"
                            + "Ck1Rc3dDUVlEVlFRR0V3SkJWVEVUTUJFR0ExVUVDQXdLVTI5dFpTMVRkR0YwWlRFaE1COEdB"
                            + "MVVFQ2d3WVNXNTAKWlhKdVpYUWdWMmxrWjJsMGN5QlFkSGtnVEhSa01Gd3dEUVlKS29aSWh2"
                            + "Y05BUUVCQlFBRFN3QXdTQUpCQU1uRAp5bkQ1dHp5K0YyNUZKbDVOUFJaMlRrclBJV2lmdmR3"
                            + "aVJCYXFudjNYSlNsWllNeHVTbERlblBNYmIwdHhXMUM4CjBxeDVnVVlDRk5xcU5qV0hWSlVD"
                            + "QXdFQUFhTlFNRTR3SFFZRFZSME9CQllFRkxMQ3hCbExXekFIZVE5U1o3b3gKbFYvUE9JUHZN"
                            + "QjhHQTFVZEl3UVlNQmFBRkxMQ3hCbExXekFIZVE5U1o3b3hsVi9QT0lQdk1Bd0dBMVVkRXdR"
                            + "RgpNQU1CQWY4d0RRWUpLb1pJaHZjTkFRRUxCUUFEUVFBY2xuR2JkdGJhVXNOTmEvWHRHYlhD"
                            + "WFpjZERRaWo2SGx3Cmp1ZGRqKzdmR2psSnZMMWF5OUlaYjIxblRJOHpOQXhsb25Ld2YrT1g0"
                            + "ODRQM2ZBVHFCMGIKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=");

    // base64 encoded junk
    private static final byte[] INVALID_CERT_BYTES =
            CryptoUtils.decodeBase64(
                    "dG90YWwgMzYKZHJ3eHJ3eHIteCAzIGphbm5lIGphbm5lIDQwOTYgaHVodGkgMjQgMTY6MjEgLgpkcnd4cn"
                            + "d4ci14IDkgamFubmUgamFubmUgNDA5NiBodWh0aSAyNCAxMToxNSAuLgotcnctcnctci0tIDEg"
                            + "amFubmUgamFubmUgMzEwNSBodWh0aSAyNCAxNjowOSBkZWNvZGVkCi1ydy1ydy1yLS0gMSBqYW"
                            + "5uZSBqYW5uZSAyMjUyIGh1aHRpIDIzIDE0OjEyIGdvb2dsZS1jZXJ0LmRlcgotcnctcnctci0t"
                            + "IDEgamFubmUgamFubmUgMzAwNCBodWh0aSAyNCAxNjowOSBnb29nbGUtY2VydC5kZXIuYmFzZT"
                            + "Y0Ci1ydy1ydy1yLS0gMSBqYW5uZSBqYW5uZSAzMTA1IGh1aHRpIDIzIDE0OjA5IGdvb2dsZS1j"
                            + "ZXJ0LnBlbQotcnctcnctci0tIDEgamFubmUgamFubmUgNDE0MCBodWh0aSAyNCAxNjowOSBnb2"
                            + "9nbGUtY2VydC5wZW0uYmFzZTY0Ci1ydy1ydy1yLS0gMSBqYW5uZSBqYW5uZSAgICAwIGh1aHRp"
                            + "IDI0IDE2OjIxIG5vbi1jZXJ0CmRyd3hyd3hyLXggMiBqYW5uZSBqYW5uZSA0MDk2IGh1aHRpID"
                            + "I0IDE2OjIxIHRpbnkK");

    private CertificateTestUtils() {
        // noop
    }

    /**
     * Subject = CN=N/A, expires = 2038
     *
     * @return
     */
    public static byte[] getMockCertificateBytes() {
        return MOCK_CERTIFICATE_BYTES;
    }

    /**
     * return given certificate bytes as an X509Certificate
     *
     * @return
     */
    public static X509Certificate getCertificate(byte[] certificateBytes) {
        return CryptoUtils.readCertificate(certificateBytes);
    }

    /**
     * Subject = CN=N/A, expires = 2038
     *
     * @return
     */
    public static X509Certificate getMockCertificate() {
        return getCertificate(getMockCertificateBytes());
    }

    /**
     * Return a Resource for reading a byte array
     */
    public static Resource getResource(byte[] bytes) {
        return new ByteArrayResource(bytes);
    }


    /**
     * Subject = O=Internet Widgits Pty Ltd, ST=Some-State, C=AU
     * expires = Thu Apr 23 09:59:02 EEST 2020
     *
     * @return
     */
    public static byte[] getWidgitsCertificateBytes() {
        return WIDGITS_CERTIFICATE_BYTES;
    }

    /**
     * Subject = O=Internet Widgits Pty Ltd, ST=Some-State, C=AU
     * expires = Thu Apr 23 09:59:02 EEST 2020
     *
     * @return
     */
    public static X509Certificate getWidgitsCertificate() {
        return getCertificate(getWidgitsCertificateBytes());
    }

    /**
     * Return hash for getWidgitsCertificateBytes
     * @return
     */
    public static String getWidgitsCertificateHash() {
        return WIDGITS_CERTIFICATE_HASH;
    }

    /**
     * Base64 encoded junk, not a certificate
     *
     * @return
     */
    public static byte[] getInvalidCertBytes() {
        return INVALID_CERT_BYTES;
    }

    /**
     * Create a test CertificateInfo object with given ocsp status and certificate status.
     * CertificateInfo has savedToConfiguration = true
     * @param certificate
     * @param ocspStatus
     * @param certificateStatus
     * @return
     * @throws Exception
     */
    public static CertificateInfo createTestCertificateInfo(X509Certificate certificate,
            CertificateStatus ocspStatus,
            String certificateStatus) throws Exception {
        return createTestCertificateInfo(certificate,
                ocspStatus,
                certificateStatus,
                true);
    }

    /**
     * Create a test CertificateInfo object with given ocsp status and certificate status
     * @param certificate
     * @param ocspStatus
     * @param certificateStatus
     * @param isSavedToConfiguration
     * @return
     * @throws Exception
     */
    public static CertificateInfo createTestCertificateInfo(X509Certificate certificate,
            CertificateStatus ocspStatus,
            String certificateStatus,
            boolean isSavedToConfiguration) throws Exception {
        List<OCSPResp> ocsp = generateOcspResponses(
                Arrays.asList(certificate),
                ocspStatus);
        CertificateInfo certificateInfo = new CertificateInfo(
                ClientId.create("a", "b", "c"),
                true, isSavedToConfiguration,
                certificateStatus, "1",
                certificate.getEncoded(),
                ocsp.iterator().next().getEncoded());
        return certificateInfo;
    }

    private static List<OCSPResp> generateOcspResponses(List<X509Certificate> certs,
            CertificateStatus status) throws Exception {
        List<OCSPResp> responses = new ArrayList<>();
        for (X509Certificate cert : certs) {
            responses.add(OcspTestUtils.createOCSPResponse(cert,
                    getIssuerCert(cert, certs),
                    TestCertUtil.getOcspSigner().certChain[0],
                    TestCertUtil.getOcspSigner().key,
                    status));
        }
        return responses;
    }

    private static X509Certificate getIssuerCert(X509Certificate subject,
            List<X509Certificate> certs) {
        for (X509Certificate cert : certs) {
            if (cert.getSubjectX500Principal().equals(
                    subject.getIssuerX500Principal())) {
                return cert;
            }
        }

        return TestCertUtil.getCertChainCert("root_ca.p12");
    }

}
