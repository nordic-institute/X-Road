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
import ee.ria.xroad.signer.protocol.message.GetDidAndSelfDescription;
import ee.ria.xroad.signer.protocol.message.GetDidAndSelfDescriptionResponse;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.tokenmanager.module.SoftwareModuleType;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareTokenType;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareTokenUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
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
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.util.CryptoUtils.loadPkcs12KeyStore;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static ee.ria.xroad.signer.util.ExceptionHelper.tokenNotActive;
import static ee.ria.xroad.signer.util.ExceptionHelper.tokenNotInitialized;

/**
 * Handles requests for DID documents.
 */
@Slf4j
public class GetDidAndSelfDescriptionRequestHandler extends AbstractRequestHandler<GetDidAndSelfDescription> {

    private static final String ISO_8601_DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private String didFileLocation = SystemProperties.getTempFilesPath() + "did-web.json";
    private String selfDescriptionFileLocation = SystemProperties.getTempFilesPath() + "self-description.json";
    private String termsAndConditionsHash = "36ba819f30a3c4d4a7f16ee0a77259fc92f2e1ebf739713609f"
            + "1c11eb41499e7aa2cd3a5d2011e073f9ba9c107493e3e8629cc15cd4fc07f67281d7ea9023db0";
    private boolean isDetached = true;

    @Override
    protected Object handle(GetDidAndSelfDescription message) throws Exception {
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

                        // Convert the existing sign key to a JSON Web Key (JWK)
                        JWK jwk = createJwk(keyInfo, certInfo);

                        // Create a DID document for DID Web identifier
                        JsonObject did = createDidJson(jwk, message.getDidDomain());

                        // Write the DID document to file
                        writeToFile(did.toString().getBytes(), didFileLocation);

                        // Create a Gaia-X Self-Description
                        JsonObject sd = createSelfDescription(message.getDidDomain(),
                                message.getCredentialId(), message.getBusinessId(),
                                message.getHeadquarterAddressCountryCode(), message.getLegalAddressCountryCode());

                        // Create a JSON Web Signature (JWS) for the Self-Description
                        JWSObject jws = createJws(sd, jwk);
                        // Add the signature and the proof to the Self-Description
                        addProofToSelfDescription(sd, did, jws);

                        // Write the Self-Description to file
                        writeToFile(sd.toString().getBytes(), selfDescriptionFileLocation);

                        return new GetDidAndSelfDescriptionResponse(didFileLocation, selfDescriptionFileLocation);
                    }
                }
            }
        }
        throw CodedException.tr(X_KEY_NOT_FOUND,
                "sign_key_not_found_for_member",
                "Could not find active sign key for "
                        + "member '%s'", message.getMemberId());
    }

    /**
     * Create a JWS signature of the Gaia-X Self-Description using the JWK presentation
     * of an X-Road Member's sign key. The JWS signature is created using unencoded
     * payload option and a detached payload. The generated JWS is verified and an
     * exception is thrown if the verification fails.
     *
     * @param selfDescription
     * @param jwk
     * @return
     * @throws Exception
     */
    private JWSObject createJws(JsonObject selfDescription, JWK jwk) throws Exception {
        // The payload is not be encoded and must be passed to
        // the JWS consumer in a detached manner
        Payload payload = new Payload(selfDescription.toString());

        // Create and sign JWS
        // The JWS signature is created using unencoded payload option and a detached payload
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .base64URLEncodePayload(false)
                .criticalParams(Collections.singleton("b64"))
                .build();

        JWSObject jwsObject = new JWSObject(header, payload);
        jwsObject.sign(new RSASSASigner(jwk.toRSAKey()));

        // Serialize JWS to String
        String jws = jwsObject.serialize(isDetached);

        // Verify the generated JWS
        JWSObject parsedJWSObject = JWSObject.parse(jws, payload);
        JWSVerifier verifier = new RSASSAVerifier(jwk.toPublicJWK().toRSAKey());
        if (!parsedJWSObject.verify(verifier)) {
            throw new Exception("Verifying the JWS failed");
        }

        return jwsObject;
    }

    /**
     * Convert an X-Road Member's sign key to a JSON Web Key (JWK).
     *
     * @param keyInfo
     * @param certInfo
     * @return
     * @throws Exception
     */
    private JWK createJwk(KeyInfo keyInfo, CertificateInfo certInfo) throws Exception {
        // Use AuthKeyInfo class since it contains all required properties.
        // Consider creating SignKeyInfo class for production level implementation.
        AuthKeyInfo signKeyInfo = signKeyResponse(keyInfo, certInfo);
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

        List<Base64> certs = chain.getAllCerts()
                .stream()
                .map(this::base64EncodeCertificate)
                .collect(Collectors.toList());

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .keyIDFromThumbprint()
                .algorithm(Algorithm.parse("RS256"))
                .x509CertChain(certs)
                .build();
    }

    private Base64 base64EncodeCertificate(X509Certificate cert) {
        try {
            return Base64.encode(cert.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a DID using the given web domain according to the did:web method spec:
     * https://w3c-ccg.github.io/did-method-web/
     *
     * @param didDomain target domain
     * @return web method DID
     */
    private String createDidWed(String didDomain) {
        return "did:web:" + didDomain
                .replace(":", "%3A")
                .replaceAll("[./]", ":");
    }

    /**
     * Create a did:web DID document using the given JWK and domain. The
     * resulting file should be published in the given domain following
     * the did:web method specifications.
     *
     * @param jwk
     * @param didDomain
     * @return
     */
    private JsonObject createDidJson(JWK jwk, String didDomain) {
        String didWed = createDidWed(didDomain);
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

    /**
     * Create an unsigned Gaia-X Self-Description using the given parameters.
     *
     * @param didDomain
     * @param credentialId
     * @param businessId
     * @param headquarterAddressCountryCode
     * @param legalAddressCountryCode
     * @return
     */
    private JsonObject createSelfDescription(String didDomain, String credentialId,
                                             String businessId, String headquarterAddressCountryCode,
                                             String legalAddressCountryCode) {
        String didWed = createDidWed(didDomain);
        String date = getDateISOString();

        JsonObject sd = new JsonObject();
        JsonArray context = new JsonArray();
        context.add("https://www.w3.org/2018/credentials/v1");
        context.add("https://www.w3.org/2018/credentials/examples/v1");
        sd.add("@context", context);
        sd.addProperty("id", credentialId);
        sd.addProperty("type", "VerifiableCredential");
        sd.addProperty("issuer", didWed);
        sd.addProperty("issuanceDate", date);

        JsonObject credentialSubject = new JsonObject();
        JsonObject credentialSubjectContext = new JsonObject();
        credentialSubjectContext.addProperty("gx", "https://registry.gaia-x.eu/22.04/schema/gaia-x");
        credentialSubject.add("@context", credentialSubjectContext);
        credentialSubject.addProperty("id", didWed);

        JsonObject registrationNumber = new JsonObject();
        registrationNumber.addProperty("gx:local", businessId);
        credentialSubject.add("gx:registrationNumber", registrationNumber);

        JsonObject headquarterAddress = new JsonObject();
        headquarterAddress.addProperty("gx:countryCode", headquarterAddressCountryCode);
        credentialSubject.add("gx:headquarterAddress", headquarterAddress);

        JsonObject legalAddress = new JsonObject();
        legalAddress.addProperty("gx:countryCode", legalAddressCountryCode);
        credentialSubject.add("gx:legalAddress", legalAddress);

        credentialSubject.addProperty("gx:termsAndConditions", termsAndConditionsHash);

        sd.add("credentialSubject", credentialSubject);

        return sd;
    }

    /**
     * Add proof section including the JWS signature to the Gaia-X Self-Description.
     *
     * @param sd
     * @param did
     * @param jws
     */
    private void addProofToSelfDescription(JsonObject sd, JsonObject did, JWSObject jws) {
        String date = getDateISOString();
        String verificationMethod = did.getAsJsonArray("verificationMethod")
                .get(0).getAsJsonObject().get("id").getAsString();

        sd.getAsJsonArray("@context").add("https://w3id.org/security/suites/jws-2020/v1");
        JsonObject proof = new JsonObject();
        proof.addProperty("type", "JsonWebSignature2020");
        proof.addProperty("created", date);
        proof.addProperty("jws", jws.serialize(isDetached));
        proof.addProperty("proofPurpose", "assertionMethod");
        proof.addProperty("verificationMethod", verificationMethod);

        sd.add("proof", proof);
    }

    /**
     * Get current date/time in UTC as ISO 8601 formatted string.
     *
     * @return
     */
    private String getDateISOString() {
        Date date = new Date(System.currentTimeMillis());
        return new SimpleDateFormat(ISO_8601_DATE_PATTERN).format(date);
    }

    private void writeToFile(byte[] data, String filePath) throws Exception {
        Path didFilePath = Paths.get(filePath);
        Files.deleteIfExists(didFilePath);
        Path newFile = Files.createFile(didFilePath);

        try (FileOutputStream fos = new FileOutputStream(
                newFile.toAbsolutePath().toFile())) {
            fos.write(data);
        }
    }

    /**
     * Copied from GetAuthKeyRequestHandler.
     *
     * @throws CodedException
     */
    private void validateToken() throws CodedException {
        if (!SoftwareTokenUtil.isTokenInitialized()) {
            throw tokenNotInitialized(SoftwareTokenType.ID);
        }

        if (!TokenManager.isTokenActive(SoftwareTokenType.ID)) {
            throw tokenNotActive(SoftwareTokenType.ID);
        }
    }

    /**
     * Copied from GetAuthKeyRequestHandler.
     *
     * @param keyInfo
     * @param certInfo
     * @return
     * @throws Exception
     */
    private AuthKeyInfo signKeyResponse(KeyInfo keyInfo,
                                        CertificateInfo certInfo) throws Exception {
        String alias = keyInfo.getId();
        String keyStoreFileName = SoftwareTokenUtil.getKeyStoreFileName(alias);
        char[] password = PasswordStore.getPassword(SoftwareTokenType.ID);

        return new AuthKeyInfo(alias, keyStoreFileName, password, certInfo);
    }

    /**
     * Copied from GetAuthKeyRequestHandler with some modifications.
     *
     * @param certInfo
     * @param memberId
     * @return
     * @throws Exception
     */
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

    /**
     * Copied from GetAuthKeyRequestHandler.
     *
     * @param instanceIdentifier
     * @param subject
     * @param ocspBytes
     * @param verifierOptions
     * @throws Exception
     */
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

    /**
     * Copied from GetAuthKeyRequestHandler with minor modifications.
     *
     * @param keyInfo
     * @return
     * @throws Exception
     */
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
