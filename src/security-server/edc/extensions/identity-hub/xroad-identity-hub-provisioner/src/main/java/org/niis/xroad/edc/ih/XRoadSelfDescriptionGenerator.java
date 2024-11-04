package org.niis.xroad.edc.ih;

import ee.ria.xroad.signer.SignerRpcClient;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.util.Base64;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.security.token.jwt.CryptoConverter;

import java.time.Instant;
import java.util.List;

import static java.time.temporal.ChronoUnit.DAYS;

@RequiredArgsConstructor
public class XRoadSelfDescriptionGenerator {
    private final SignerRpcClient signerRpcClient;

    public JWSObject generate(String xroadIdentifier, JWSSigner signer, String did, String keyId) throws Exception {
        var payload = getPayload(xroadIdentifier);
        var header = new JWSHeader.Builder(CryptoConverter.getRecommendedAlgorithm(signer))
                .base64URLEncodePayload(true)
                .customParam("iss", did)
                .keyID(did + "#" + keyId)
                .customParam("iat", Instant.now().toString())
                .customParam("exp", Instant.now().plus(90, DAYS).toString())
                .x509CertChain(List.of(Base64.encode(getActiveCertificate(keyId))))
                .build();
        var detachedPayload = new Payload(payload);
        var jwsObject = new JWSObject(header, detachedPayload);
        jwsObject.sign(signer);
        return jwsObject;
    }

    private byte[] getActiveCertificate(String keyId) throws Exception {
        var token = signerRpcClient.getTokenForKeyId(keyId);
        var certificates = token.getKeyInfo().stream()
                .filter(keyInfo -> keyInfo.getId().equals(keyId))
                .findFirst()
                .map(KeyInfo::getCerts)
                .orElseThrow();
        return certificates.stream()
                .filter(CertificateInfo::isActive)
                .map(CertificateInfo::getCertificateBytes)
                .findFirst()
                .orElseThrow();
    }

    private static String getPayload(String xroadIdentifier) {
        return "{\n" +
                "  \"xroadIdentifier\": \"" + xroadIdentifier + "\"\n" +
                "}";
    }

}
