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
package ee.ria.xroad.signer.console;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;

final class Utils {

    private static final int CLIENT_ID_PARTS = 3;

    private Utils() {
    }

    static void printTokenInfo(TokenInfo token, boolean verbose) {
        if (verbose) {
            System.out.println("Token type:    " + token.getType());
            System.out.println("Token id:      " + token.getId());
            System.out.println("Friendly name: " + token.getFriendlyName());
            System.out.println("Read-Only:     " + token.isReadOnly());
            System.out.println("Available:     " + token.isAvailable());
            System.out.println("Active:        " + token.isActive());
            System.out.println("Status:        " + token.getStatus());
            System.out.println("Serial number: " + token.getSerialNumber());
            System.out.println("Label:         " + token.getLabel());
            System.out.println("TokenInfo:");

            token.getTokenInfo().forEach((k, v) -> System.out.format("\t%-32s %s%n", k, v));
        } else {
            String format = "Token: %s (%s, %s, %s, %s)";

            String readOnly = token.isReadOnly() ? "read-only" : "writable";
            String available = token.isAvailable() ? "available" : "unavailable";
            String active = token.isActive() ? "active" : "inactive";

            System.out.println(String.format(format, token.getId(), token.getStatus(), readOnly, available, active));
        }
    }

    static void printKeyInfo(KeyInfo key, boolean verbose, String padding) {
        if (verbose) {
            System.out.println(padding + "Id:             " + key.getId());
            System.out.println(padding + "Name:           " + key.getFriendlyName());
            System.out.println(padding + "Label:          " + key.getLabel());
            System.out.println(padding + "Usage:          " + key.getUsage());
            System.out.println(padding + "Sign mechanism: " + key.getSignMechanismName());
            System.out.println(padding + "Available:      " + key.isAvailable());

            if (key.getPublicKey() != null) {
                System.out.println(padding + "Public key (Base64):\n" + key.getPublicKey());
            } else {
                System.out.println(padding + "<no public key available>");
            }
        } else {
            String format = padding + "Key: %s (%s, %s)";

            String available = key.isAvailable() ? "available" : "unavailable";

            System.out.println(String.format(format, key.getId(), key.getUsage(), available));
        }
    }

    static void printCertInfo(KeyInfo key, boolean verbose, String padding) {
        if (verbose) {
            key.getCerts().forEach(cert -> {
                System.out.println(padding + "Id:            " + cert.getId());
                System.out.println(padding + "Status:        " + cert.getStatus());
                System.out.println(padding + "Member:        " + cert.getMemberId());
                System.out.println(padding + "Hash:          " + hash(cert));
                System.out.println(padding + "OCSP:          " + getOcspStatus(cert.getOcspBytes()));
                System.out.println(padding + "Saved to conf: " + cert.isSavedToConfiguration());
            });

            key.getCertRequests().forEach(certReq -> {
                System.out.println(padding + "Id:            " + certReq.getId());
                System.out.println(padding + "Member:        " + certReq.getMemberId());
                System.out.println(padding + "Subject name:  " + certReq.getSubjectName());
            });
        } else {
            String certFormat = padding + "Cert: %s (%s, %s)";
            key.getCerts().forEach(cert -> System.out.println(String.format(certFormat, cert.getId(),
                    cert.getStatus(), cert.getMemberId())));

            String certReqFormat = padding + "CertReq: %s (%s, %s)";
            key.getCertRequests().forEach(certReq -> System.out.println(String.format(certReqFormat, certReq.getId(),
                    certReq.getMemberId(), certReq.getSubjectName())));
        }
    }

    static byte[] fileToBytes(String fileName) throws Exception {
        try (FileInputStream in = new FileInputStream(fileName)) {
            return IOUtils.toByteArray(in);
        }
    }

    static void bytesToFile(String file, byte[] bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            IOUtils.write(bytes, out);

            System.out.println(file);
        } catch (Exception e) {
            System.out.println("ERROR: Cannot save to file " + file + ":" + e);
        }
    }

    static void base64ToFile(String file, byte[] bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            IOUtils.write(encodeBase64(bytes), out);

            System.out.println("Saved to file " + file);
        } catch (Exception e) {
            System.out.println("ERROR: Cannot save to file " + file + ":" + e);
        }
    }

    static ClientId.Conf createClientId(String string) throws Exception {
        String[] parts = string.split(" ");

        if (parts.length < CLIENT_ID_PARTS) {
            throw new Exception("Must specify all parts for ClientId");
        }

        if (parts.length > CLIENT_ID_PARTS) {
            String subsystem = parts.length > CLIENT_ID_PARTS ? parts[parts.length - 1] : null;
            String code = StringUtils.join(ArrayUtils.subarray(parts, 2, parts.length - 1), " ");

            return ClientId.Conf.create(parts[0], parts[1], code, subsystem);
        } else {
            return ClientId.Conf.create(parts[0], parts[1], parts[2], null);
        }
    }

    @SneakyThrows
    static String getOcspStatus(byte[] ocspBytes) {
        if (ocspBytes == null) {
            return "<not available>";
        }

        OCSPResp response = new OCSPResp(ocspBytes);
        BasicOCSPResp basicResponse = (BasicOCSPResp) response.getResponseObject();
        SingleResp resp = basicResponse.getResponses()[0];
        CertificateStatus status = resp.getCertStatus();

        if (status == CertificateStatus.GOOD) {
            return "GOOD";
        } else if (status instanceof org.bouncycastle.cert.ocsp.RevokedStatus) {
            return "REVOKED";
        } else if (status instanceof org.bouncycastle.cert.ocsp.UnknownStatus) {
            return "UNKNOWN";
        } else {
            return "{" + status.getClass() + "}";
        }
    }

    @SneakyThrows
    static String hash(CertificateInfo cert) {
        return calculateCertHexHash(cert.getCertificateBytes());
    }
}
