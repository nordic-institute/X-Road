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
package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconfextension.GlobalConfExtensions;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.common.ocsp.OcspVerifierOptions;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.PasswordStore;
import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.dto.AuthKeyInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.message.GetDidDocument;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.tokenmanager.module.SoftwareModuleType;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareTokenType;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareTokenUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.util.CryptoUtils.loadPkcs12KeyStore;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static ee.ria.xroad.signer.util.ExceptionHelper.tokenNotActive;
import static ee.ria.xroad.signer.util.ExceptionHelper.tokenNotInitialized;

/**
 * Handles requests for DID documents.
 */
@Slf4j
public class GetDidDocumentRequestHandler extends AbstractRequestHandler<GetDidDocument> {

    private String didFileLocation = SystemProperties.getTempFilesPath() + "did-web.json";

    @Override
    protected Object handle(GetDidDocument message) throws Exception {
        log.trace("Selecting sign key for member {}",
                message.getMemberId());

        validateToken();

        for (TokenInfo tokenInfo : TokenManager.listTokens()) {
            if (!SoftwareModuleType.TYPE.equals(tokenInfo.getType())) {
                log.trace("Ignoring {} module", tokenInfo.getType());
                continue;
            }

            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                if (!keyInfo.isForSigning()) {
                    log.trace("Ignoring {} key {}", keyInfo.getUsage(),
                            keyInfo.getId());
                    continue;
                }

                if (!keyInfo.isAvailable()) {
                    log.trace("Ignoring unavailable key {}", keyInfo.getId());
                    continue;
                }

                for (CertificateInfo certInfo : keyInfo.getCerts()) {
                    if (signCertValid(certInfo, message.getMemberId())) {
                        log.trace("Found suitable sign key {}",
                                keyInfo.getId());

                        JWK jwk = createJwk(keyInfo, certInfo);

                        JsonObject did = createDidJson(jwk, message.getDidDomain());

                        writeDidDocumentToFile(did.toString().getBytes());

                        return didFileLocation;

                        //return "Member ID: " + message.getMemberId() + " | DID domain: " + message.getDidDomain();
                        //return jwk.toPublicJWK().toJSONString();
                        //return did.toString();
                    }
                }
            }
        }
        throw CodedException.tr(X_KEY_NOT_FOUND,
                "sign_key_not_found_for_member",
                "Could not find active sign key for "
                        + "member '%s'", message.getMemberId());
    }

    private JWK createJwk(KeyInfo keyInfo, CertificateInfo certInfo) throws Exception {
        AuthKeyInfo signKeyInfo = authKeyResponse(keyInfo, certInfo);
        PrivateKey privateKey = loadSignPrivateKey(signKeyInfo);

        if (privateKey == null) {
            throw new CertificateException("Failed to load sign key");
        }

        KeyFactory kf = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(
                java.util.Base64.getDecoder().decode(keyInfo.getPublicKey())
        );
        RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(keySpecX509);

        X509Certificate cert = readCertificate(certInfo.getCertificateBytes());

        CertChain chain = GlobalConf.getCertChain(GlobalConf.getInstanceIdentifier(), cert);
        List<Base64> certs = new ArrayList<Base64>();

        for (X509Certificate c: chain.getAllCerts()) {
            certs.add(Base64.encode(c.getEncoded()));
        }

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .keyIDFromThumbprint()
                .algorithm(Algorithm.parse("RS256"))
                .x509CertChain(certs)
                .build();
    }
    private JsonObject createDidJson(JWK jwk, String didDomain) {
        String didWed = "did:web:" + didDomain
                .replace(":", "%3A")
                .replaceAll("[./]", ":");
        JsonObject did = new JsonObject();
        JsonArray context = new JsonArray();
        context.add("https://www.w3.org/ns/did/v1");
        context.add("https://w3id.org/security/suites/jws-2020/v1");
        did.add("@context", context);
        did.addProperty("id", didWed);
        JsonObject publicKeyJwk = JsonParser.parseString(jwk.toPublicJWK().toJSONString())
                .getAsJsonObject();
        JsonArray verificationMethodArr = new JsonArray();
        JsonObject verificationMethod = new JsonObject();
        verificationMethod.addProperty("id", didWed + "#" + jwk.getKeyID());
        verificationMethod.addProperty("type", "JsonWebKey2020");
        verificationMethod.addProperty("controller", didWed);
        verificationMethod.add("publicKeyJwk", publicKeyJwk);
        verificationMethodArr.add(verificationMethod);
        did.add("verificationMethod", verificationMethodArr);
        JsonArray assertionMethod = new JsonArray();
        assertionMethod.add(didWed + "#" + jwk.getKeyID());
        JsonArray authentication = new JsonArray();
        authentication.add(didWed + "#" + jwk.getKeyID());
        did.add("assertionMethod", assertionMethod);
        did.add("authentication", authentication);

        return did;
    }
    private void writeDidDocumentToFile(byte[] data) throws Exception {
        Path didFilePath = Paths.get(didFileLocation);
        Files.deleteIfExists(didFilePath);
        Path newFile = Files.createFile(didFilePath);

        try (FileOutputStream fos = new FileOutputStream(
                newFile.toAbsolutePath().toFile())) {
            fos.write(data);
        }
    }

    private void validateToken() throws CodedException {
        if (!SoftwareTokenUtil.isTokenInitialized()) {
            throw tokenNotInitialized(SoftwareTokenType.ID);
        }

        if (!TokenManager.isTokenActive(SoftwareTokenType.ID)) {
            throw tokenNotActive(SoftwareTokenType.ID);
        }
    }

    private AuthKeyInfo authKeyResponse(KeyInfo keyInfo,
                                        CertificateInfo certInfo) throws Exception {
        String alias = keyInfo.getId();
        String keyStoreFileName = SoftwareTokenUtil.getKeyStoreFileName(alias);
        char[] password = PasswordStore.getPassword(SoftwareTokenType.ID);

        return new AuthKeyInfo(alias, keyStoreFileName, password, certInfo);
    }

    private boolean signCertValid(CertificateInfo certInfo,
                                  ClientId memberId) throws Exception {
        X509Certificate cert = readCertificate(certInfo.getCertificateBytes());

        if (!certInfo.isActive()) {
            log.trace("Ignoring inactive sign certificate {}",
                    CertUtils.identify(cert));

            return false;
        }

        ClientId memberIdFromCert = GlobalConf.getSubjectName(
                new SignCertificateProfileInfoParameters(
                        ClientId.create(
                                GlobalConf.getInstanceIdentifier(),
                                memberId.getMemberClass(),
                                memberId.getMemberCode()
                        ),
                        "Member name"
                ),
                cert
        );

        try {
            cert.checkValidity();

            if (memberId.equals(memberIdFromCert)) {
                verifyOcspResponse(memberId.getXRoadInstance(), cert,
                        certInfo.getOcspBytes(), new OcspVerifierOptions(
                                GlobalConfExtensions.getInstance()
                                        .shouldVerifyOcspNextUpdate()));

                return true;
            }
        } catch (Exception e) {
            log.warn("Ignoring sign certificate '{}' because: ",
                    cert.getSubjectX500Principal().getName(), e);

            return false;
        }

        log.trace("Ignoring sign certificate {} because it does "
                + "not belong to member {} "
                + "(member id from global conf: {})", new Object[] {
                    CertUtils.identify(cert),
                    memberId, memberIdFromCert
                }
        );

        return false;
    }

    private void verifyOcspResponse(String instanceIdentifier, X509Certificate subject,
                                    byte[] ocspBytes, OcspVerifierOptions verifierOptions) throws Exception {
        if (ocspBytes == null) {
            throw new CertificateException("OCSP response not found");
        }

        OCSPResp ocsp = new OCSPResp(ocspBytes);
        X509Certificate issuer =
                GlobalConf.getCaCert(instanceIdentifier, subject);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(false), verifierOptions);
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

    private PrivateKey loadSignPrivateKey(AuthKeyInfo keyInfo) throws Exception {
        File keyStoreFile = new File(keyInfo.getKeyStoreFileName());
        log.trace("Loading sign key from key store '{}'",
                keyStoreFile);

        KeyStore ks = loadPkcs12KeyStore(keyStoreFile, keyInfo.getPassword());

        PrivateKey privateKey = (PrivateKey) ks.getKey(keyInfo.getAlias(),
                keyInfo.getPassword());
        if (privateKey == null) {
            log.warn("Failed to read sign key");
        }

        return privateKey;
    }

    private X509Certificate getCaCert(String instanceIdentifier, X509Certificate subject) throws Exception {
        return GlobalConf.getCaCert(instanceIdentifier, subject);
    }
}
