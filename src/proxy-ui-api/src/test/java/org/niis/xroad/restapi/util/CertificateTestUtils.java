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

    public static final String MOCK_CERTIFICATE_HASH = "A2293825AA82A5429EC32803847E2152A303969C";

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

    /**
     * This is an authentication certificate created in a development setup
     *
     * Certificate Details:
     * Serial Number: 8 (0x8)
     * Validity
     * Not Before: Nov 28 09:20:27 2019 GMT
     * Not After : Nov 23 09:20:27 2039 GMT
     * Subject:
     * countryName               = FI
     * organizationName          = SS5
     * commonName                = ss1
     * serialNumber              = CS/SS1/ORG
     * X509v3 extensions:
     * X509v3 Basic Constraints:
     * CA:FALSE
     * X509v3 Key Usage: critical
     * Digital Signature, Key Encipherment, Data Encipherment, Key Agreement
     * X509v3 Extended Key Usage:
     * TLS Web Client Authentication, TLS Web Server Authentication
     * Certificate is to be certified until Nov 23 09:20:27 2039 GMT (7300 days)
     */
    private static final byte[] MOCK_AUTH_CERT_BYTES =
            CryptoUtils.decodeBase64(
                    "MIIEXDCCAkSgAwIBAgIBCDANBgkqhkiG9w0BAQsFADBnMQswCQYDVQQGEwJGSTEY"
                            + "MBYGA1UECgwPQ3VzdG9taXplZCBUZXN0MR4wHAYDVQQLDBVDdXN0b21pemVkIFRl"
                            + "c3QgQ0EgT1UxHjAcBgNVBAMMFUN1c3RvbWl6ZWQgVGVzdCBDQSBDTjAeFw0xOTEx"
                            + "MjgwOTIwMjdaFw0zOTExMjMwOTIwMjdaMD4xCzAJBgNVBAYTAkZJMQwwCgYDVQQK"
                            + "DANTUzUxDDAKBgNVBAMMA3NzMTETMBEGA1UEBRMKQ1MvU1MxL09SRzCCASIwDQYJ"
                            + "KoZIhvcNAQEBBQADggEPADCCAQoCggEBAJLpUt/B2EZIwoc7YjJfc9RO26NERzC0"
                            + "YCJtJyCcspqqcTmIl7is6m7e9Dfovsy33ALNxRPGrX1c01MnNL+WaOVv2YDlJWsE"
                            + "KPZTqry94hX/xG4Tn9Nspfd87gANozClsN+CHQbUdAxR+me8HR3DoRmeJjUM757E"
                            + "GoXJl4zrV2OMskcMspIA1zXwkZUKKjvFsBcTUo9HLKUeqh1EJLpHBMMok6Jl6PrI"
                            + "DnToGSDBQScv+K4PLFTtrZv0UiAsZlzaSyWR8JWuUUxZGYXxQZxJeEtFIVcYZN69"
                            + "Hz89vH107QmvW3hqDEj0oGkCFfEhNGGENhjWGz1qtiLBB+niopbNruECAwEAAaM8"
                            + "MDowCQYDVR0TBAIwADAOBgNVHQ8BAf8EBAMCA7gwHQYDVR0lBBYwFAYIKwYBBQUH"
                            + "AwIGCCsGAQUFBwMBMA0GCSqGSIb3DQEBCwUAA4ICAQChHEZ1z04voWZEZCQxcH83"
                            + "n8dG+89hzhmY5Sa/2wS9hhTeEavePkBEbjpQptoM6oeL1gn48/SXMGR9ZXpMOrRc"
                            + "bU0s90VDOsTnesIaXMD8kJTCkHpYCSIIqyynV+TdQQtMz694ioV9OyG7gWLYIMUf"
                            + "slU9oMWrq0qkOtuQ857MqBykXIaZwQnULRm0ATPbug+5KCFN8n5EaMD24CYug8gy"
                            + "HM7sZ2Xu2S1UElW6k7LDbI24d5+/HZHhy/tGuE5hJfq9x1+KlrmvjB37dkfeoW//"
                            + "ep8kKOaUagBVc3GaEFg7bzV4XPwvV2aHtoXwK2J146JoRlqnJMqzVJfoOMa0QAFN"
                            + "Zw1Bau9bpmBEsePhsakPG7WdH3TyQ8GLPSDd98jcwqt4hzJbUEDhC85hLEYomRCb"
                            + "2WSPbE8WqoOQLFKORYrsMw/RseAQSSyfjenxR+cBwFxPej5bzsZYFU8xKzPCO4/8"
                            + "BuDCvzi58mD3/nTca7qwIBhcFqIhoI6Xepkw3TvAKEyOvjeDl3ZteWlrDiBIZiPZ"
                            + "RO2U15y7Ym+4FkGR7Y/HqaXIFbLjXM0dsG5yJ/kT0yY2JRlNPY4QIHPE9I22MkzR"
                            + "Squ5wMaxbtMHoBTVKisuqbRa4HSjxAFGA0EfZkLxLsDIcOfQXmY6p2Q3Hxi8vrT7"
                            + "TeEqXuL/b9PoaiQWFcPZcg==");

    private CertificateTestUtils() {
        // noop
    }

    /**
     * Subject = CN=N/A, expires = 2038
     * @return
     */
    public static byte[] getMockCertificateBytes() {
        return MOCK_CERTIFICATE_BYTES;
    }

    /**
     * Subject = CN=N/A, expires = 2039
     * @return
     */
    public static byte[] getMockAuthCertificateBytes() {
        return MOCK_AUTH_CERT_BYTES;
    }

    /**
     * return given certificate bytes as an X509Certificate
     * @return
     */
    public static X509Certificate getCertificate(byte[] certificateBytes) {
        return CryptoUtils.readCertificate(certificateBytes);
    }

    /**
     * Subject = CN=N/A, expires = 2038
     * @return
     */
    public static X509Certificate getMockCertificate() {
        return getCertificate(getMockCertificateBytes());
    }

    /**
     * Subject = CN=N/A, expires = 2039
     * @return
     */
    public static X509Certificate getMockAuthCertificate() {
        return getCertificate(getMockAuthCertificateBytes());
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
     * @return
     */
    public static byte[] getWidgitsCertificateBytes() {
        return WIDGITS_CERTIFICATE_BYTES;
    }

    /**
     * Subject = O=Internet Widgits Pty Ltd, ST=Some-State, C=AU
     * expires = Thu Apr 23 09:59:02 EEST 2020
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
     * @return
     */
    public static byte[] getInvalidCertBytes() {
        return INVALID_CERT_BYTES;
    }

    /**
     * Builder for CertificateInfo objects.
     * Default values:
     * - clientId = a:b:c
     * - active = true
     * - savedToConfiguration = true
     * - status = REGISTERED
     * - id = 1
     * - certificate bytes = CertificateTestUtils.getMockCertificate
     * - ocsp response = GOOD
     */
    public static class CertificateInfoBuilder {
        private X509Certificate certificate = CertificateTestUtils.getMockCertificate();
        private CertificateStatus ocspStatus = CertificateStatus.GOOD;
        private String certificateStatus = CertificateInfo.STATUS_REGISTERED;
        private boolean isSavedToConfiguration = true;
        private String id = "1";

        public CertificateInfoBuilder() {
        }

        public CertificateInfoBuilder id(String idParam) {
            this.id = idParam;
            return this;
        }

        public CertificateInfoBuilder certificate(X509Certificate certificateParam) {
            this.certificate = certificateParam;
            return this;
        }

        public CertificateInfoBuilder ocspStatus(CertificateStatus ocspStatusParam) {
            this.ocspStatus = ocspStatusParam;
            return this;
        }

        public CertificateInfoBuilder certificateStatus(String certificateStatusParam) {
            this.certificateStatus = certificateStatusParam;
            return this;
        }

        public CertificateInfoBuilder savedToConfiguration(boolean savedToConfigurationParam) {
            isSavedToConfiguration = savedToConfigurationParam;
            return this;
        }

        public CertificateInfo build() {
            try {
                List<OCSPResp> ocsp = generateOcspResponses(
                        Arrays.asList(certificate),
                        ocspStatus);
                CertificateInfo certificateInfo = new CertificateInfo(
                        ClientId.create("a", "b", "c"),
                        true,
                        isSavedToConfiguration,
                        certificateStatus,
                        id,
                        certificate.getEncoded(),
                        ocsp.iterator().next().getEncoded());
                return certificateInfo;

            } catch (Exception e) {
                throw new RuntimeException("failed to create CertificateInfo", e);
            }
        }
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
