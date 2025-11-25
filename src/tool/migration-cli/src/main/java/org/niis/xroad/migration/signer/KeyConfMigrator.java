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

package org.niis.xroad.migration.signer;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.niis.xroad.migration.utils.DbCredentials;
import org.niis.xroad.migration.utils.DbPropertiesReader;
import org.niis.xroad.signer.keyconf.CertRequestType;
import org.niis.xroad.signer.keyconf.CertificateType;
import org.niis.xroad.signer.keyconf.DeviceType;
import org.niis.xroad.signer.keyconf.KeyConfType;
import org.niis.xroad.signer.keyconf.KeyType;
import org.niis.xroad.signer.keyconf.ObjectFactory;

import java.io.Console;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class KeyConfMigrator {

    public void migrate(String keyconfPath, String dbPropertiesPath) throws SQLException {
        KeyConfType keyConf = parseKeyConf(Path.of(keyconfPath, "keyconf.xml"));

        SignerRepository repo = getSignerRepository(dbPropertiesPath);

        for (DeviceType deviceType : keyConf.getDevice()) {
            log.info("Processing token {}", deviceType.getId());
            boolean isSoftToken = deviceType.getDeviceType().equalsIgnoreCase("softtoken");
            long tokenId = handleToken(deviceType, repo, isSoftToken, keyconfPath);

            log.info("  Processing token keys({})", deviceType.getKey().size());
            for (KeyType keyType : deviceType.getKey()) {
                log.info("    Processing key {}", keyType.getKeyId());
                long keyId = handleKey(keyType, repo, isSoftToken, tokenId, keyconfPath);

                log.info("      Processing key certificates({})", keyType.getCert().size());
                for (CertificateType cert : keyType.getCert()) {
                    handleCertificate(cert, keyId, repo);
                }

                log.info("      Processing key certificate requests({})", keyType.getCertRequest().size());
                for (CertRequestType certReq : keyType.getCertRequest()) {
                    handleCertificateRequest(certReq, keyId, repo);
                }
            }
        }
    }

    private void handleCertificateRequest(CertRequestType certReq, long keyId, SignerRepository repo) throws SQLException {
        Optional<Long> dbId = repo.getCertificateRequestId(certReq.getId());
        if (dbId.isPresent()) {
            log.warn("     Certificate request {} already exists", certReq.getId());
        }
        repo.saveCertificateRequest(certReq, keyId);
        log.info("     Certificate request {} saved", certReq.getId());
    }

    private void handleCertificate(CertificateType cert, long keyId, SignerRepository repo) throws SQLException {
        Optional<Long> dbId = repo.getCertificateId(cert.getId());
        if (dbId.isPresent()) {
            log.warn("        Certificate {} already exists", cert.getId());
            return;
        }
        repo.saveCertificate(cert, keyId);
        log.info("        Certificate {} saved", cert.getId());
    }

    private long handleToken(DeviceType deviceType, SignerRepository repo, boolean isSoftToken, String keyconfPath) throws SQLException {
        Optional<Long> dbId = repo.getTokenId(deviceType.getId());
        if (dbId.isPresent()) {
            log.warn("  Token {} already exists", deviceType.getId());
            return dbId.get();
        }
        byte[] pinHash = isSoftToken ? getPinHashFromInput(keyconfPath) : null;
        long tokenId = repo.saveToken(deviceType, pinHash);
        log.info("  Token {} saved", deviceType.getId());
        return tokenId;
    }

    private long handleKey(KeyType keyType, SignerRepository repo, boolean isSoftToken, long tokenId,
                           String keyconfPath) throws SQLException {
        Optional<Long> keyDbId = repo.getKeyId(keyType.getKeyId());
        if (keyDbId.isPresent()) {
            log.info("      Key {} already exists", keyType.getKeyId());
            return keyDbId.get();
        }
        byte[] keystore = isSoftToken ? readKey(keyType.getKeyId(), keyconfPath) : null;
        long keyId = repo.saveKey(keyType, tokenId, isSoftToken, keystore);
        log.info("      Key {} saved", keyType.getKeyId());
        return keyId;
    }

    protected SignerRepository getSignerRepository(String dbPropertiesPath) {
        DbCredentials dbCredentials = DbPropertiesReader.readDbCredentials(Paths.get(dbPropertiesPath), "serverconf");
        return new SignerRepository(dbCredentials);
    }

    private byte[] getPinHashFromInput(String keyconfPath) {
        char[] pin;
        do {
            pin = readPinFromConsole();
            try {
                KeyStore keystore = KeyStore.getInstance("pkcs12");
                try (FileInputStream fis = new FileInputStream(Paths.get(
                        keyconfPath, "softtoken", ".softtoken.p12").toFile())) {
                    keystore.load(fis, pin);
                }
                PrivateKey privateKey = (PrivateKey) keystore.getKey("pin", pin);
                if (privateKey == null) {
                    log.error("Provided pin is invalid, try again.");
                    pin = null;
                }
            } catch (Exception e) {
                log.error("Provided pin is invalid, try again.");
                pin = null;
            }

        } while (pin == null || pin.length == 0);

        log.info("  pin ok");
        return hashPin(pin);
    }

    protected char[] readPinFromConsole() {
        Console console = System.console();
        return console.readPassword("Enter softtoken pin:");
    }

    private byte[] readKey(String id, String keyconfPath) {
        try {
            Path keyFile = Paths.get(keyconfPath, "softtoken", id + ".p12");
            if (!Files.exists(keyFile)) {
                log.warn("Key file does not exist: {}", keyFile);
                return null;
            }
            return Files.readAllBytes(keyFile);
        } catch (IOException e) {
            log.error("Error reading key file: {}", id, e);
            return null;
        }
    }

    KeyConfType parseKeyConf(Path keyConfPath) {
        Objects.requireNonNull(keyConfPath, "keyConfPath");
        if (!Files.exists(keyConfPath)) {
            throw new IllegalArgumentException("Key configuration file not found: " + keyConfPath.toAbsolutePath());
        }

        try (InputStream inputStream = Files.newInputStream(keyConfPath)) {
            JAXBContext context = JAXBContext.newInstance(ObjectFactory.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            Object unmarshalled = unmarshaller.unmarshal(inputStream);

            if (unmarshalled instanceof JAXBElement<?> jaxbElement) {
                Object value = jaxbElement.getValue();
                if (value instanceof KeyConfType keyConf) {
                    return keyConf;
                }
            } else if (unmarshalled instanceof KeyConfType keyConf) {
                return keyConf;
            }

            throw new IllegalStateException("Unexpected payload when parsing keyconf.xml: "
                    + unmarshalled.getClass().getName());
        } catch (IOException | JAXBException e) {
            throw new IllegalStateException("Unable to parse keyconf file " + keyConfPath.toAbsolutePath(), e);
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private byte[] hashPin(char[] pin) {
        var params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withIterations(3)
                .withMemoryAsKB(12)
                .withParallelism(4)
                .build();
        byte[] pinBytes = new String(pin).getBytes(StandardCharsets.UTF_8);
        byte[] hash = new byte[32];

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        generator.generateBytes(pinBytes, hash);

        return hash;
    }
}
