package ee.cyber.sdsb.signer.console;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;

import static ee.cyber.sdsb.common.util.CryptoUtils.calculateCertHexHash;
import static ee.cyber.sdsb.common.util.CryptoUtils.encodeBase64;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class Utils {

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

            token.getTokenInfo().forEach(
                    (k, v) -> System.out.format("\t%-32s %s\n", k, v));
        } else {
            String format = "Token: %s (%s, %s, %s, %s)";

            String readOnly = token.isReadOnly() ? "read-only" : "writable";
            String available = token.isAvailable() ? "available" : "unavailable";
            String active = token.isActive() ? "active" : "inactive";

            System.out.println(String.format(format, token.getId(),
                    token.getStatus(), readOnly, available, active));
        }
    }

    static void printKeyInfo(KeyInfo key, boolean verbose, String padding) {
        if (verbose) {
            System.out.println(padding + "Id:        " + key.getId());
            System.out.println(padding + "Name:      " + key.getFriendlyName());
            System.out.println(padding + "Usage:     " + key.getUsage());
            System.out.println(padding + "Available: " + key.isAvailable());

            if (key.getPublicKey() != null) {
                System.out.println(padding + "Public key (Base64):\n"
                        + key.getPublicKey());
            } else {
                System.out.println(padding + "<no public key available>");
            }
        } else {
            String format = padding + "Key: %s (%s, %s)";

            String available = key.isAvailable() ? "available" : "unavailable";

            System.out.println(String.format(format, key.getId(),
                    key.getUsage(), available));
        }
    }

    static void printCertInfo(KeyInfo key, boolean verbose, String padding) {
        if (verbose) {
            key.getCerts().forEach(cert -> {
                System.out.println(padding + "Id:            "
                        + cert.getId());
                System.out.println(padding + "Status:        "
                        + cert.getStatus());
                System.out.println(padding + "Member:        "
                        + cert.getMemberId());
                System.out.println(padding + "Hash:          "
                        + hash(cert));
                System.out.println(padding + "OCSP:          "
                        + getOcspStatus(cert.getOcspBytes()));
                System.out.println(padding + "Saved to conf: "
                        + cert.isSavedToConfiguration());
            });

            key.getCertRequests().forEach(certReq -> {
                System.out.println(padding + "Id:            "
                        + certReq.getId());
                System.out.println(padding + "Member:        "
                        + certReq.getMemberId());
                System.out.println(padding + "Subject name:  "
                        + certReq.getSubjectName());
            });
        } else {
            String certFormat = padding + "Cert: %s (%s, %s)";
            key.getCerts().forEach(cert -> {
                System.out.println(String.format(certFormat, cert.getId(),
                        cert.getStatus(), cert.getMemberId()));
            });

            String certReqFormat = padding + "CertReq: %s (%s, %s)";
            key.getCertRequests().forEach(certReq -> {
                System.out.println(String.format(certReqFormat, certReq.getId(),
                        certReq.getMemberId(), certReq.getSubjectName()));
            });
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
            System.out.println("ERROR: Cannot save to file" + file + ":" + e);
        }
    }

    static void base64ToFile(String file, byte[] bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            IOUtils.write(encodeBase64(bytes), out);
            System.out.println("Saved to file " + file);
        } catch (Exception e) {
            System.out.println("ERROR: Cannot save to file" + file + ":" + e);
        }
    }

    static ClientId createClientId(String string) throws Exception {
        String[] parts = string.split(" ");
        if (parts.length < 3) {
            throw new Exception("Must specify all parts for ClientId");
        }

        if (parts.length > 3) {
            String subsystem = parts.length > 3
                    ? parts[parts.length - 1] : null;
            String code = StringUtils.join(
                    ArrayUtils.subarray(parts, 2, parts.length - 1), " ");
            return ClientId.create(parts[0], parts[1], code, subsystem);
        } else {
            return ClientId.create(parts[0], parts[1], parts[2], null);
        }
    }

    @SneakyThrows
    static String getOcspStatus(byte[] ocspBytes) {
        if (ocspBytes == null) {
            return "<not available>";
        }

        OCSPResp response = new OCSPResp(ocspBytes);
        BasicOCSPResp basicResponse =
                (BasicOCSPResp) response.getResponseObject();
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
