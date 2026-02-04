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
package org.niis.xroad.ss.test;

import ee.ria.xroad.common.crypto.KeyManagers;
import ee.ria.xroad.common.crypto.SignDataPreparer;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.globalconf.generator.ConfigurationPart;
import org.niis.xroad.cs.admin.globalconf.generator.DirectoryContentBuilder;
import org.niis.xroad.cs.admin.globalconf.generator.DirectoryContentBuilder.DirectoryContentHolder;
import org.niis.xroad.cs.admin.globalconf.generator.DirectoryContentSigner;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA512;
import static ee.ria.xroad.common.crypto.identifier.Providers.BOUNCY_CASTLE;
import static ee.ria.xroad.common.crypto.identifier.SignMechanism.CKM_RSA_PKCS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS;


class GlobalConfSignTest {
    private static final String CONF_ROOT = "src/intTest/resources/nginx-container-files/var/lib/xroad/public";
    public static final String V6_CONF_PATH = "/V6/20251110170000548026000";
    public static final String INTERNAL_KEY_ID = "internal";
    public static final Instant EXPIRE_DATE = Instant.parse("2035-11-11T03:07:40Z");


    @Disabled("Run to sign system-tests' global configuration, when configuration is changed")
    @Test
    void signInternalConf() throws Exception {
        var signedDirectory = getSignedDirectory(internalDirectoryContent(), INTERNAL_KEY_ID);

        assertThat(signedDirectory).contains(
                "Verification-certificate-hash: pc2fwlS1MpbN2rxno4qAi27zvPTNIWxvC5xeHRLIQsPj5RR2J7TuY+6VAH0rmbDecjtia9AjYDzGIj3i6K9T1w==");
        Files.writeString(Path.of(CONF_ROOT, "V6/internalconf"), signedDirectory);
    }

    private String getSignedDirectory(DirectoryContentHolder directoryContent, String keyId) throws Exception {
        var privateKey = CertUtils.readKeyPairFromPemFile("src/test/resources/sign-keys/" + keyId + ".key").getPrivate();
        var certBytes = CryptoUtils.readCertificate(getClass().getResourceAsStream("/sign-keys/" + keyId + ".crt")).getEncoded();
        var signerProxyFacade = new TestConfSignSignerProxyFacade(privateKey);

        return new DirectoryContentSigner(signerProxyFacade, SHA512, SHA512)
                .createSignedDirectory(directoryContent, INTERNAL_KEY_ID, certBytes);
    }


    DirectoryContentHolder internalDirectoryContent() throws Exception {
        var builder = new DirectoryContentBuilder(SHA512, EXPIRE_DATE, V6_CONF_PATH, "DEV", 6);
        builder.contentPart(configurationPart(V6_CONF_PATH, FILE_NAME_SHARED_PARAMETERS, CONTENT_ID_SHARED_PARAMETERS));
        builder.contentPart(configurationPart(V6_CONF_PATH, FILE_NAME_PRIVATE_PARAMETERS, CONTENT_ID_PRIVATE_PARAMETERS));
        return builder.build();
    }

    private static ConfigurationPart configurationPart(String path, String filename, String contentId) throws IOException {
        return ConfigurationPart.builder()
                .filename(filename)
                .contentIdentifier(contentId)
                .data(Files.readAllBytes(Path.of(CONF_ROOT, path, filename)))
                .build();
    }

    private static class TestConfSignSignerProxyFacade implements SignerProxyFacade {
        private final PrivateKey privateKey;

        TestConfSignSignerProxyFacade(PrivateKey privateKey) {
            this.privateKey = privateKey;
        }

        @Override
        public SignMechanism getSignMechanism(String keyId) {
            return CKM_RSA_PKCS;
        }

        @Override
        public byte[] sign(String keyId, SignAlgorithm algorithm, byte[] digest) {
            try {
                SignAlgorithm signAlgorithm = KeyManagers.getFor(KeyAlgorithm.RSA).getSoftwareTokenSignAlgorithm();
                byte[] data = SignDataPreparer.of(algorithm).prepare(digest);
                Signature signature = Signature.getInstance(signAlgorithm.name(), BOUNCY_CASTLE);
                signature.initSign(privateKey);
                signature.update(data);
                return signature.sign();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void initSoftwareToken(char[] password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TokenInfo> getTokens() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TokenInfo getToken(String tokenId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void activateToken(String tokenId, char[] password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deactivateToken(String tokenId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public KeyInfo generateKey(String tokenId, String keyLabel, KeyAlgorithm algorithm) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] generateSelfSignedCert(String keyId, ClientId.Conf memberId,
                                             KeyUsageInfo keyUsage, String commonName, Date notBefore, Date notAfter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteKey(String keyId, boolean deleteFromToken) {
            throw new UnsupportedOperationException();
        }
    }
}
