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
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.common.ocsp.OcspVerifierOptions;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.PasswordStore;
import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.dto.AuthKeyInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyId;
import ee.ria.xroad.signer.protocol.message.GetDidAndSelfDescription;
import ee.ria.xroad.signer.protocol.message.GetDidAndSelfDescriptionResponse;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;
import ee.ria.xroad.signer.tokenmanager.ServiceLocator;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareTokenType;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareTokenUtil;
import ee.ria.xroad.signer.util.SignerUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.X509CertUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
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
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmId;
import static ee.ria.xroad.common.util.CryptoUtils.loadPkcs12KeyStore;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static ee.ria.xroad.signer.util.ExceptionHelper.keyNotAvailable;

/**
 * Handles requests for DID documents.
 */
@Slf4j
public class GetDidAndSelfDescriptionRequestHandler extends AbstractRequestHandler<GetDidAndSelfDescription> {
    private String gaiaXApiVersion = "v2204";
    private static final String ISO_8601_DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private String didFileLocation = SystemProperties.getTempFilesPath() + "did-web.json";
    private String selfDescriptionFileLocation = SystemProperties.getTempFilesPath() + "self-description.json";
    private String certChainFileLocation = SystemProperties.getTempFilesPath() + "certificate-chain.pem";

    @Override
    protected Object handle(GetDidAndSelfDescription message) throws Exception {
        CertificateInfo certInfo = TokenManager.findCertificateInfo(message.getSignCertId());
        String hash =  CryptoUtils.calculateCertHexHash(certInfo.getCertificateBytes());
        TokenInfoAndKeyId tokenInfoAndKeyId = TokenManager.findTokenAndKeyIdForCertHash(hash);

        TokenInfo tokenInfo = tokenInfoAndKeyId.getTokenInfo();
        KeyInfo keyInfo = tokenInfoAndKeyId.getKeyInfo();

        if (!keyInfo.isForSigning()) {
            throw new CertificateException("Authentication certificate cannot be used for signing");
        }

        if (!keyInfo.isAvailable()) {
            throw keyNotAvailable(keyInfo.getId());
        }

        if (signCertValid(certInfo)) {
            // Convert the existing sign key to a JSON Web Key (JWK)
            JWK jwk = createJwk(keyInfo, certInfo, message.getCertificateChainUrl());

            // Create a DID document for DID Web identifier
            JsonObject did = createDidJson(jwk, message.getDidDomain());

            // Write the DID document to file
            writeToFile(did.toString().getBytes(), didFileLocation);

            // Create a Gaia-X Self-Description
            JsonObject sd = createSelfDescription(message.getDidDomain(),
                    message.getCredentialId(), message.getBusinessId(),
                    message.getHeadquarterAddressCountryCode(), message.getLegalAddressCountryCode());

            // Create a JSON Web Signature (JWS) for the Self-Description
            String jws = createJws(sd, jwk, tokenInfo.getId(), keyInfo.getId());
            // Add the signature and the proof to the Self-Description
            addProofToSelfDescription(sd, did, jws);

            // Write the Self-Description to file
            writeToFile(sd.toString().getBytes(), selfDescriptionFileLocation);

            return new GetDidAndSelfDescriptionResponse(
                    didFileLocation, selfDescriptionFileLocation, certChainFileLocation);
        }

        throw CodedException.tr(X_KEY_NOT_FOUND,
                "sign_key_not_found",
                "Could not find active sign key with "
                        + "certificate ID '%s'", message.getSignCertId());
    }

    /**
     * Returns the given String with base64 Encoding with URL and Filename Safe Alphabet:
     * https://www.rfc-editor.org/rfc/rfc4648#page-7
     *
     * @param data
     * @return
     */
    private String base64UrlEncode(String data) {
        return base64UrlEncode(data.getBytes());
    }

    /**
     * Returns the given byte array with base64 Encoding with URL and Filename Safe Alphabet:
     * https://www.rfc-editor.org/rfc/rfc4648#page-7
     *
     * @param data
     * @return
     */
    private String base64UrlEncode(byte[] data) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * Copied from GenerateSelfSignedCertRequestHandler with some modifications.
     *
     * @param tokenId
     * @param keyId
     * @param dataToSign
     * @return
     * @throws Exception
     */
    private String sign(String tokenId, String keyId, String dataToSign) throws Exception {
        // SHA256WITHRSA_ID equals to RS256
        String signAlgoId = CryptoUtils.SHA256WITHRSA_ID;
        String digAlgoId = getDigestAlgorithmId(signAlgoId);
        byte[] digest = calculateDigest(digAlgoId, dataToSign.getBytes());

        Sign message = new Sign(keyId, signAlgoId, digest);

        Object response = SignerUtil.ask(ServiceLocator.getTokenSigner(getContext(), tokenId), message);

        if (response instanceof SignResponse) {
            byte[] data = ((SignResponse) response).getSignature();
            // Must be returned with base 64 Encoding with URL and Filename Safe Alphabet:
            // https://www.rfc-editor.org/rfc/rfc4648#page-7
            return base64UrlEncode(data);
        } else {
            throw new RuntimeException("Failed to sign with key " + keyId
                    + "; response was " + response);
        }
    }

    /**
     * Create a JWS token of the Gaia-X Self-Description using the JWK presentation
     * of an X-Road Member's sign key. The JWS signature is created using unencoded
     * payload option and a detached payload. The generated JWS token is verified and an
     * exception is thrown if the verification fails.
     *
     * @param selfDescription
     * @param jwk
     * @param tokenId
     * @param keyId
     * @return
     * @throws Exception
     */
    private String createJws(JsonObject selfDescription, JWK jwk, String tokenId, String keyId) throws Exception {
        // Canonize the credential
        String canonizedSd = canonize(selfDescription.toString());
        // Hash the canonized credential with SHA256
        String sha256hex = DigestUtils.sha256Hex(canonizedSd);

        // Create the JWS header object
        JsonObject headerJson = new JsonObject();
        // The payload must not be base64 URL encoded
        headerJson.addProperty("b64", false);
        JsonArray crit = new JsonArray();
        crit.add("b64");
        headerJson.add("crit", crit);
        headerJson.addProperty("alg", "RS256");

        // Base64 URL encode the header
        String header = base64UrlEncode(headerJson.toString());

        // Build the string to be signed using the canonized and hashed credential as payload
        String dataToSign = header + '.' + sha256hex;
        // Sign the data
        String signature = sign(tokenId, keyId, dataToSign);

        // Build the JWS token with a detached payload
        String jws = header + ".." + signature;

        // Verify the generated JWS
        JWSObject parsedJWSObject = JWSObject.parse(jws, new Payload(sha256hex));
        JWSVerifier verifier = new RSASSAVerifier(jwk.toPublicJWK().toRSAKey());
        if (!parsedJWSObject.verify(verifier)) {
            throw new Exception("Verifying the JWS failed");
        }
        return jws;
    }

    /**
     * Convert an X-Road Member's sign key to a JSON Web Key (JWK).
     *
     * @param keyInfo
     * @param certInfo
     * @param certChainUrl
     * @return
     * @throws Exception
     */
    private JWK createJwk(KeyInfo keyInfo, CertificateInfo certInfo, String certChainUrl) throws Exception {
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

        writeCertChainToFile(chain.getAllCerts());

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .keyIDFromThumbprint()
                .algorithm(Algorithm.parse("RS256"))
                .x509CertChain(certs)
                .x509CertURL(new URI(certChainUrl))
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
     * Writes the given certificate chain to a file
     * @param certChain
     * @throws Exception
     */
    private void writeCertChainToFile(List<X509Certificate> certChain) throws Exception {
        StringBuilder sb = new StringBuilder();
        certChain.stream().forEach(c -> sb.append(X509CertUtils.toPEMString(c)).append("\n"));
        writeToFile(sb.toString().getBytes(), this.certChainFileLocation);
    }

    /**
     * Normalizes the JSON string using the Universal RDF Dataset Canonicalization Algorithm 2015 (URDNA2015).
     * Currently, the implementation uses the external Gaia-X Compliance Service API.
     *
     * <b>NOTE:</b> A production level implementation shouldn't depend on an external API, but rather implement
     * the normalization on code level.
     *
     * @param json
     * @return
     * @throws Exception
     */
    private String canonize(String json) throws Exception {
        HttpPost post = new HttpPost("https://compliance.gaia-x.eu/api/" + gaiaXApiVersion + "/normalize");
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(json));

        try (
                CloseableHttpClient httpClient = HttpClients.createDefault();
                CloseableHttpResponse response = httpClient.execute(post)
        ) {
            String data = EntityUtils.toString(response.getEntity());
            log.trace(data);
            return data;
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
        // Gaia-X Compliance Service API v2204 doesn't encode the identifier
        // correctly and therefore, encoding the identifier must be skipped.
        // return "did:web:" + didDomain
        //        .replace(":", "%3A")
        //        .replaceAll("[./]", ":");
        return "did:web:" + didDomain;

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

        JsonObject sd = new JsonObject();
        JsonArray context = new JsonArray();
        context.add("https://www.w3.org/2018/credentials/v1");
        sd.add("@context", context);
        sd.addProperty("@id", credentialId);
        JsonArray type = new JsonArray();
        type.add("VerifiableCredential");
        sd.add("@type", type);

        JsonObject credentialSubject = new JsonObject();
        JsonObject credentialSubjectContext = new JsonObject();
        credentialSubjectContext.addProperty("gx", "https://registry.gaia-x.eu/22.04/schema/gaia-x");
        credentialSubject.add("@context", credentialSubjectContext);
        credentialSubject.addProperty("id", didWed);

        JsonObject registrationNumber = new JsonObject();
        registrationNumber.addProperty("@type", "xsd:string");
        registrationNumber.addProperty("@value", businessId);
        credentialSubject.add("gx:registrationNumber", registrationNumber);

        JsonObject headquarterAddress = new JsonObject();
        headquarterAddress.addProperty("@type", "gx:Address");
        JsonObject headquarterAddressCountry = new JsonObject();
        headquarterAddressCountry.addProperty("@type", "xsd:string");
        headquarterAddressCountry.addProperty("@value", headquarterAddressCountryCode);
        headquarterAddress.add("gx:country", headquarterAddressCountry);
        credentialSubject.add("gx:headquarterAddress", headquarterAddress);

        JsonObject legalAddress = new JsonObject();
        legalAddress.addProperty("@type", "gx:Address");
        JsonObject legalAddressCountry = new JsonObject();
        legalAddressCountry.addProperty("@type", "xsd:string");
        legalAddressCountry.addProperty("@value", legalAddressCountryCode);
        legalAddress.add("gx:country", legalAddressCountry);
        credentialSubject.add("gx:legalAddress", legalAddress);

        //credentialSubject.addProperty("gx:termsAndConditions", termsAndConditionsHash);

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
    private void addProofToSelfDescription(JsonObject sd, JsonObject did, String jws) {
        String date = getDateISOString();
        String verificationMethod = did.get("id").getAsString();

        //sd.getAsJsonArray("@context").add("https://w3id.org/security/suites/jws-2020/v1");
        JsonObject proof = new JsonObject();
        proof.addProperty("type", "JsonWebSignature2020");
        proof.addProperty("created", date);
        proof.addProperty("jws", jws);
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
     * Copied from GetAuthKeyRequestHandler with modifications.
     *
     * @param certInfo
     * @return
     * @throws Exception
     */
    private boolean signCertValid(CertificateInfo certInfo) throws Exception {
        X509Certificate cert = readCertificate(certInfo.getCertificateBytes());

        if (!certInfo.isActive()) {
            log.trace("Ignoring inactive sign certificate {}",
                    CertUtils.identify(cert));

            return false;
        }

        try {
            cert.checkValidity();
        } catch (Exception e) {
            log.warn("Ignoring sign certificate '{}' because: ",
                    cert.getSubjectX500Principal().getName(), e);

            return false;
        }

        return true;
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
