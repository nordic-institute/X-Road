/**
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
package org.niis.xroad.securityserver.restapi.util;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utils for working with test x509 certificates
 */
public final class CertificateTestUtils {

    // this is base64 encoded DER certificate from common-util/test/configuration-anchor.xml

    public static final String MOCK_CERTIFICATE_HASH = "A2293825AA82A5429EC32803847E2152A303969C";
    public static final String MOCK_AUTH_CERTIFICATE_HASH = "BA6CCC3B13E23BB1D40FD17631B7D93CF8334C0E";

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

    private static final byte[] MOCK_TOP_CA_CERT_BYTES =
            CryptoUtils.decodeBase64("MIIFpzCCA4+gAwIBAgIUOKHQEPC1bBLtBShmTvoJ180394kwDQYJKoZIhvcNAQELBQ"
                    + "AwWzELMAkGA1UEBhMCRkkxFDASBgNVBAoMC1gtUm9hZCBUZXN0MRowGAYDVQQLDBFYLVJvYWQgVGVzdCBDQSBPVTEaMBg"
                    + "GA1UEAwwRWC1Sb2FkIFRlc3QgQ0EgQ04wHhcNMTkwNjE0MDYxMTMxWhcNMzkwNjA5MDYxMTMxWjBbMQswCQYDVQQGEwJG"
                    + "STEUMBIGA1UECgwLWC1Sb2FkIFRlc3QxGjAYBgNVBAsMEVgtUm9hZCBUZXN0IENBIE9VMRowGAYDVQQDDBFYLVJvYWQgV"
                    + "GVzdCBDQSBDTjCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBALX71JjmKm+cms2m5nlTTBqj+81G3tsoKS57Kp"
                    + "2u3qV443fllar+9mgFTBAAz19Donz542neTAtJx63NGSbW5OWoXMZFayI66vkYT2mBi6547hA18cLGeivS9W5VOK/3skV"
                    + "AFsvMxxL2MOAQWg4nW7uj0/7KV1d254thmu6alNWKs7BTYYwhYCnFO7w66AF2c7QiLR9iuGVRED/Mjot3Fmeok27FoJHO"
                    + "7AkHD2iq22p/wUy9qXZqk2P3/DHbXVazPVu/7WyPzru0WW9XN2XHtaszqMyIj4iCn3vN9f8T9Cd2ckw/5l6QGduKDo8ut"
                    + "Q4dC7Gxoopf+OUnR4GWcnyhKYi/3ULSi4gh1NXe1URldTmahgWjQq1wY8HVKOu15O03ZJZOivE1Vv31hZs8qxvq1eR1b5"
                    + "UoC9upHA1nNerGokmuDuUP8jWkGoUDd5ZfI2adaSvWCHGRfoi3GFF/3DQ0rlODZ0wNxjmddokSNAchQp/Er/fUGBqgMXg"
                    + "pse1wgy/D1b2PoHms0DbJnYUnuPsWRqJvYOshKSpPX1i/iTW6HvJAg74u1h1ysotq5TnUZJXDlHonO8vMYTsnVpI9aGve"
                    + "4kh0OsroJ0nic7NbCe+0+1mdyxx0ZTZiYdVyPD3V7bTUyO/1saVaazYiVCtqg3G4XhJHhPhxBJG96Xo8JC0xPq2PAgMBA"
                    + "AGjYzBhMB0GA1UdDgQWBBRF7mqBg7FoNyjICO6UhSb7EVNwPTAfBgNVHSMEGDAWgBRF7mqBg7FoNyjICO6UhSb7EVNwPT"
                    + "APBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBhjANBgkqhkiG9w0BAQsFAAOCAgEAdetsMFDVZie4tZRHNAitBQu"
                    + "4cTy4yyDhYAw+DHitp6pakuYyJtZxxkzIFQwgAIChzE725w79HWr2lblauFHJsHaJEsGPeR62Jh9S3TuyrXyhSdXKWDbD"
                    + "lsapoNguTSoTEQ9+l9AF1FAPhdSzzgeq1updbmP4tY0cIQlMOazbSsM+st6IeAklbpVWxTHAu/kkkql11W0THkvI646ns"
                    + "iHNYdo7yMbmFiKYnCOQSVM7tZFYOvqJSNdlaISlux9FSzQEXtfCzb/bEInK/v2mjdlnBXCMMU460p0UDdr3fz8CZkot3M"
                    + "VFV0YoTh1GA8xZqt1NGx7A8URuSHQ+1l9migTm6mRrnAOQDkflZ+OdJAE5ybEoOJMRP6vnCub0GpO6KPXj28kycniE60g"
                    + "/SC9gL8MEqO74uDqnCEcmpY3ccV7k3IDNrNiN9Jgxulfawt+tT12ookrnBblq7sB8A4iPdsBRion5DCtQZ984ymMvnsH6"
                    + "v+WId6LOYxkC5ItMlM+F6lu2bWAOsIgBv31WCcy5nlsvzCK32m5pFLKBU+DJonwHEpemi5C00ZA+r14SOy1kluaP6xzqw"
                    + "x6puhG/nQmHv/8cgmAyejdn6t1tifToZph1KcBXtebJTPH1orYnYKtUsKxysZF6DWXw1YkiR8Cd2sRhs307CHuVizD3Wj"
                    + "GniSmwSrk=");

    private static final byte[] MOCK_INTERMEDIATE_CA_CERT_BYTES =
            CryptoUtils.decodeBase64("MIIFZzCCA0+gAwIBAgIBEjANBgkqhkiG9w0BAQsFADBbMQswCQYDVQQGEwJGSTEUM"
                    + "BIGA1UECgwLWC1Sb2FkIFRlc3QxGjAYBgNVBAsMEVgtUm9hZCBUZXN0IENBIE9VMRowGAYDVQQDDBFYLVJvYWQgVGVzd"
                    + "CBDQSBDTjAeFw0yMDAzMDQwNzUzNDlaFw00MDAyMjgwNzUzNDlaMCsxGDAWBgNVBAoMD1gtUm9hZCBUZXN0IGludDEPM"
                    + "A0GA1UEAwwGaW50LWNuMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAq1uxlwbJWnn/Ey0K3CIoAY+X4mDum"
                    + "kL0qkimjQZnbh9Bowq5BCF1HCZWJ7919cJAdaQaV8TvtowWcDqxJmetcA375piNT/L4AldlotC0uxbjyiScXs0cT8qF6"
                    + "AEs7CSoxemXby/uV0y48AtylD8blSwIE6d1LxwjWrKOfipfHNN6QCVpTN5JDDu+oq3Jzs6IxtQyPOZDUdNEzap466OQm"
                    + "nHtoivTZqiiplygT0FkZ0fj3SHjP3J1ngbCTi2URaOwqaekYvuE6TUPQuaVA2YMZJwoYJ1jxmrIAcoD4HhywtkjU4yhg"
                    + "v0LfZVLmXpmAhBVAr0mn/EACnz28e39i0PhnaiFn6SV1RNd3iwdylNm7Rn+ZF1a6N1HQPCD2+VbtMCE9KT4FHyXhgKuZ"
                    + "D26zb63LrkxriOoIIlHWxMLpMTcwfoExkk2UGWYbAxf7QeYMvA4+tRQUchfE/osVv+DansbxaZrq7FXunQgcRphHlf5c"
                    + "qYFyskc8DKOJsDmo+xuMSw1RM6qe0D6rr9sw4s2hFTEcKOSCmVeWcR7iIkm0hwzZAmemr8Yc2UhIyodrCVF3Wcs6nKjd"
                    + "CagF2t+qEdm3S02E5GhUgZvrketYgiYGUwizeUyZcaAPsqK7EvBvsMai4YAOe5hJYssxPMYotb2Gp9LmxxxRG+P+tQ5S"
                    + "R9OEcLl2AsCAwEAAaNmMGQwHQYDVR0OBBYEFN97AVWLyY6+6enqkr8A1x5DyBHKMB8GA1UdIwQYMBaAFEXuaoGDsWg3K"
                    + "MgI7pSFJvsRU3A9MBIGA1UdEwEB/wQIMAYBAf8CAQAwDgYDVR0PAQH/BAQDAgGGMA0GCSqGSIb3DQEBCwUAA4ICAQAGz"
                    + "7/mINANkmtGMIvmutda7qnIONzKPP7eejSXmcE28SyNxEsiUxiognUjxnVCseQ9uEmtdNHPHzbPIPRKZrxRy6nWB2IOj"
                    + "m3rsU0g0AOA2qnHho7snx29uKCFSfQv3F8yQBySKabKSIliyMaVPMnrI8dtYVTyPYGACe1ghZX9SgMwis233FFuSSQe9"
                    + "n1umnwxduhT52Fg2asCgtS1mi8l2s3Li0tST4EB/OfeVDE6EWLasbBRQI4HV/iCgfjmnIo5migTmilimHtDOH92YfScz"
                    + "QvK7d0OP8lv7wds4pEVFDhc3SLhdTagtgerwWBJPT3ZbTRlgH8ymFitlj/H7ZlhoOsqm7q/c6isynGfYN9jW8cUfhPtl"
                    + "BIS+8SpnH8aDPeOaGuNEDnpKA2QF2XVCd2nJgwHWq6uI+CMhtNAVf6Xt4KtJDalJp1LZNeqIKeRX05kNekb80mWnHk7I"
                    + "UP6OgzetUOlx1q8X5EdCwyE5JM5C352v7OR67ERoX8u3pgxeVOclJcLB0UyPBV3X849L5jWrwJoNo6kcv1jnXwK4X/fG"
                    + "QJ7JPOfD5lmD3/9MJL+gkA23dzg/oKJABLwqoe6vx9BQSKrJtwfOUSRokPcl4xYSlsnjuOQah6PHtgjuv1fPITf0FSuD"
                    + "O40kM5i6xwOPqR4+kSXwmwQ2Z4cfs1o1ryL5w==");

    // certificate which does not have X509v3 Key Usage extension (and hence is not sign or auth cert,
    // and CertUtils.isSigningCert & CertUtils.isAuthCert throw exceptions)
    private static final byte[] MOCK_CERT_WITHOUT_EXTENSIONS =
            CryptoUtils.decodeBase64("MIIC1DCCAbygAwIBAgIUYWRAIpLT0ke2PEhqKTzxDRzC2c0wDQYJKoZIhvcNAQEL"
                    + "BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTIwMTAyNjEyMzY1NloXDTMwMTAy"
                    + "NDEyMzY1NlowFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF"
                    + "AAOCAQ8AMIIBCgKCAQEA8j01lvwWQYHZM7uc2wpSPtDSgzDiEB14CtgFb9kft4u7"
                    + "jdwmF145ZFc6y5gJMayeqho/6FAAgtIrCzF8mcH3+V3T+WLWa1Z2t38jKYRKGYCb"
                    + "S4Ea5DW8fg9alZLHtxwq0w5nc6aN4zATw7C9leYrmxInazSOulqXUE2wHPadT5TZ"
                    + "UIjiWpJfCcDGf+Yh82akkEKJk9GmCJIzLhHh8SBDASFobza5urzEjpNVgJfCXsWU"
                    + "uyMm2ERcPgHvp472zM1yqs47GzX65emPzKPVmsffKHoZj8Kp9CdjLC2fwwPNoRn5"
                    + "WZVq/IS7DoG6iqsBJFqu8mnXVQHbQTHFpECqzzJ1DwIDAQABox4wHDAaBgNVHREE"
                    + "EzARhwR/AAABgglsb2NhbGhvc3QwDQYJKoZIhvcNAQELBQADggEBAKDQIlSD37MM"
                    + "m//SNjEW1BcPBnRkLaFCkYnxWl/bhx0Enow/cYtU+ymFJoEqEqr1qyxzGqJjhrGa"
                    + "SlCIKEibDANza/eIJGK4LijqZoYRRgnp8yK93auvw1clxDWix7VLdfLmH2OTK4N9"
                    + "Mo/ejRUy8t+r+PCpp282EqWZ0c34fP5D/z5FSkSxwZQMhbL/eOlfH8CjHZxaEDy5"
                    + "JTVlnbnZaLFELeBBNQ2h2B6+uF0YHnaDxYJsxaGL6zPEg/1B2qLydXEL1ByNJ9gd"
                    + "NS73vuKm1JWrk3BSLpwJjvxBikr8uMF4F2TjHHYL4MGEmJ1WwV3pq8blddistK2Z"
                    + "jnzoKNhWcmA=");

    // an actual sign certificate with correct key usage extensions
    // Subject: C = FI, O = Member4, CN = M4, serialNumber = LXD/ss4/GOV
    private static final byte[] MOCK_SIGN_CERT =
            CryptoUtils.decodeBase64("MIIENTCCAh2gAwIBAgIBFDANBgkqhkiG9w0BAQsFADBbMQswCQYDVQQGEwJGSTEU"
                    + "MBIGA1UECgwLWC1Sb2FkIFRlc3QxGjAYBgNVBAsMEVgtUm9hZCBUZXN0IENBIE9V"
                    + "MRowGAYDVQQDDBFYLVJvYWQgVGVzdCBDQSBDTjAeFw0yMDA0MjcxMDIwMDBaFw00"
                    + "MDA0MjIxMDIwMDBaMEIxCzAJBgNVBAYTAkZJMRAwDgYDVQQKDAdNZW1iZXI0MQsw"
                    + "CQYDVQQDDAJNNDEUMBIGA1UEBRMLTFhEL3NzNC9HT1YwggEiMA0GCSqGSIb3DQEB"
                    + "AQUAA4IBDwAwggEKAoIBAQCNeIB4OU2iigsQhGnTSroXpU2qr03sIJp7ZrTEtBVx"
                    + "P7LmBWOrFxHF8NJYWeJY6scWdDCMmH1o10hJP516K9yVkRPEa/etOcGztyyuBZsH"
                    + "coY367TBQCGkeN3CvGoKifUAXo8rZNdOTlW/X7Y23WJZnnVyza7M5EPJeR9DUtkT"
                    + "1DtiPUXimczjCWCYYTGCafMtPC4l1xkeQVCoIz65QabRD+S84jQdJlVGqSrqE+Tb"
                    + "iLIdyFIpX1Pd68hH0OENoeLDPT4hkkq4Esv3F5o7wpXWFKldjnhh51upaxAbeaGf"
                    + "b53B7LzTWPzfu/VM0xEXAsE29B3b05LsErQ4Obf8bBsHAgMBAAGjHTAbMAkGA1Ud"
                    + "EwQCMAAwDgYDVR0PAQH/BAQDAgZAMA0GCSqGSIb3DQEBCwUAA4ICAQA4PH9c3ZHs"
                    + "J6TeDVn9x3lEhFMfasiJZuhyjv1jImnPsZAk9UNiIEAPSCKesb75CDV6ZfYCS2hq"
                    + "EE/o4ZOiHtrGi0J2Mr+tr/U5xkGUSzc+mTF59IyP1qCZobQkjPvHxZNtHhacw0hv"
                    + "OIFgAfON9146/pNK4SPkAdixXkM7sXcWMT0BMUDVinrkdMw+qGIIkE6Dg3njfk8o"
                    + "p/2W4sBt5r7VeirrB7IbwyWq1KBPzxFfAwz+kc61U7Dw2NnCc3OGW6mr4snyQ2m9"
                    + "stwWYrTqObVo3/0Al0ltQBdPAjwpFjbtBHnTQxkJ/Ju4nhkbWrDMcAbG+bMFcaIe"
                    + "Qhf9GdxGi5TeNT8WRG4hautK7AUCXe4O0SpzdOTHMt2M8F7c7YgJcw4JYXeZb6UL"
                    + "OTMkfAxhVgOPJpJpHH9BML3K8oKFl/aR8UvBm4zie4S5LpIcIDlCtc/WMuvs98CX"
                    + "iwc/FKhzA0z+lNCvixig6KcCCM6t7451i60itWSKI5zHyumsJ3+dRKgMFW/guKIe"
                    + "QP/a/7i63kfw8k5gy0VNJEcxMtTN7gUt35t6DDEPN4bhtU1uf/miml4BcSNEecUF"
                    + "t74ogJvSf22HLx+oew/6fHoS/EgEsv9IaX6IwUY5yN8cMdJOh8KkpFSLwY8pA2dn"
                    + "sUTKacwQnP/ti/60ufPvsR+OVxONBLGwVw==");

    private CertificateTestUtils() {
        // noop
    }

    /**
     * Certificate which does not have X509v3 Key Usage extension (and hence is not a sign or auth cert,
     *  and CertUtils.isSigningCert & CertUtils.isAuthCert throw exceptions)
     */
    public static X509Certificate getMockCertificateWithoutExtensions() {
        return getCertificate(MOCK_CERT_WITHOUT_EXTENSIONS);
    }

    /**
     * See {@link #getMockCertificateWithoutExtensions()}
     */
    public static byte[] getMockCertificateWithoutExtensionsBytes() {
        return MOCK_CERT_WITHOUT_EXTENSIONS;
    }

    /**
     * An actual sign certificate with correct key usage extensions
     * Subject: C = FI, O = Member4, CN = M4, serialNumber = LXD/ss4/GOV
     */
    public static X509Certificate getMockSignCertificate() {
        return getCertificate(MOCK_SIGN_CERT);
    }

    /**
     * subject = CN=int-cn, O=X-Road Test int
     */
    public static X509Certificate getMockIntermediateCaCertificate() {
        return getCertificate(MOCK_INTERMEDIATE_CA_CERT_BYTES);
    }

    /**
     * subject = CN=X-Road Test CA CN, OU=X-Road Test CA OU, O=X-Road Test, C=FI
     */
    public static X509Certificate getMockTopCaCertificate() {
        return getCertificate(MOCK_TOP_CA_CERT_BYTES);
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
     * Builder for CertRequestInfo objects.
     * Default values:
     * - id = 1
     * - clientId = a:b:c
     * - subjectName = "subject-name"
     */
    public static class CertRequestInfoBuilder {
        private ClientId.Conf clientId = ClientId.Conf.create("a", "b", "c");
        private String id = "id";
        public CertRequestInfoBuilder() {
        }

        public CertRequestInfoBuilder clientId(ClientId.Conf clientIdParam) {
            clientId = clientIdParam;
            return this;
        }

        public CertRequestInfoBuilder id(String idParam) {
            this.id = idParam;
            return this;
        }

        public CertRequestInfo build() {
            return new CertRequestInfo(
                    id,
                    clientId,
                    "subject-name");
        }
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
        private boolean savedToConfiguration = true;
        private boolean active = true;
        private String id = "1";
        private ClientId.Conf clientId = ClientId.Conf.create("a", "b", "c");


        public CertificateInfoBuilder() {
        }

        public CertificateInfoBuilder active(boolean activeParam) {
            this.active = activeParam;
            return this;
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
            savedToConfiguration = savedToConfigurationParam;
            return this;
        }

        public CertificateInfoBuilder clientId(ClientId.Conf clientIdParam) {
            clientId = clientIdParam;
            return this;
        }

        public CertificateInfo build() {
            try {
                List<OCSPResp> ocsp = generateOcspResponses(
                        Arrays.asList(certificate),
                        ocspStatus);
                CertificateInfo certificateInfo = new CertificateInfo(
                        clientId,
                        active,
                        savedToConfiguration,
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

    public static byte[] generateOcspBytes(X509Certificate cert, CertificateStatus status) throws Exception {
        OCSPResp response = generateOcspResponses(Collections.singletonList(cert), status).get(0);
        return response.getEncoded();
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
